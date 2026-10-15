package cn.yuandianai.juziscan.history

import android.content.Context
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import cn.yuandianai.juziscan.scan.ExportFileSize
import cn.yuandianai.juziscan.scan.ExportFileType
import cn.yuandianai.juziscan.scan.IdCardDocumentType
import cn.yuandianai.juziscan.scan.ScannedPage
import cn.yuandianai.juziscan.scan.writePdfToStream
import cn.yuandianai.juziscan.scan.writeSingleImageToFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HistoryRepository(private val context: Context) {
    private val rootDir = File(context.filesDir, "history").apply { mkdirs() }
    private val exportDir = File(rootDir, "exports").apply { mkdirs() }
    private val thumbDir = File(rootDir, "thumbs").apply { mkdirs() }
    private val recordsFile = File(rootDir, "records.json")
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    @Synchronized
    fun loadRecords(): List<HistoryRecord> {
        if (!recordsFile.exists()) return emptyList()
        val raw = recordsFile.readText(Charsets.UTF_8)
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(obj.toHistoryRecord())
            }
        }.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun saveDocumentRecord(pages: List<ScannedPage>): HistoryRecord {
        require(pages.isNotEmpty()) { "No pages to save." }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val file = File(exportDir, "$id.pdf")
        file.outputStream().use { out ->
            writePdfToStream(
                output = out,
                pages = pages,
                fileSize = ExportFileSize.Actual
            )
        }
        val thumb = saveThumbnail(
            recordId = id,
            bitmap = pages.first().previewBitmap ?: pages.first().processedBitmap ?: pages.first().sourceBitmap
        )
        val record = HistoryRecord(
            id = id,
            kind = HistoryRecordKind.Document,
            title = "文件扫描 ${timeFormat.format(Date(now))}",
            subtitle = "${pages.size} 页 PDF 文档",
            createdAtMillis = now,
            pageCount = pages.size,
            fileName = "桔子扫描_文件扫描_${timestampToken(now)}.pdf",
            filePath = file.absolutePath,
            mimeType = "application/pdf",
            thumbnailPath = thumb.absolutePath
        )
        upsert(record)
        return record
    }

    @Synchronized
    fun saveIdCardRecord(
        documentType: IdCardDocumentType,
        compositePage: ScannedPage,
        sourcePageCount: Int
    ): HistoryRecord {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val file = File(exportDir, "$id.png")
        writeSingleImageToFile(
            file = file,
            page = compositePage,
            fileType = ExportFileType.PNG,
            fileSize = ExportFileSize.Actual
        )
        val thumb = saveThumbnail(
            recordId = id,
            bitmap = compositePage.previewBitmap ?: compositePage.processedBitmap ?: compositePage.sourceBitmap
        )
        val record = HistoryRecord(
            id = id,
            kind = HistoryRecordKind.IdCard,
            title = "${documentType.label}扫描",
            subtitle = "$sourcePageCount 张证件图 · PNG 图片",
            createdAtMillis = now,
            pageCount = sourcePageCount,
            fileName = "桔子扫描_${documentType.label}_${timestampToken(now)}.png",
            filePath = file.absolutePath,
            mimeType = "image/png",
            thumbnailPath = thumb.absolutePath
        )
        upsert(record)
        return record
    }

    @Synchronized
    fun saveOrUpdateOcrRecord(
        recordId: String?,
        page: ScannedPage,
        text: String
    ): HistoryRecord {
        val normalizedText = text.trim()
        require(normalizedText.isNotBlank()) { "No OCR text to save." }
        val now = System.currentTimeMillis()
        val id = recordId ?: UUID.randomUUID().toString()
        val file = File(exportDir, "$id.txt")
        file.writeText(normalizedText, Charsets.UTF_8)
        val thumb = saveThumbnail(
            recordId = id,
            bitmap = page.previewBitmap ?: page.processedBitmap ?: page.sourceBitmap
        )
        val record = HistoryRecord(
            id = id,
            kind = HistoryRecordKind.Ocr,
            title = "文字识别 ${timeFormat.format(Date(now))}",
            subtitle = buildOcrSubtitle(normalizedText),
            createdAtMillis = now,
            pageCount = 1,
            fileName = "桔子扫描_文字识别_${timestampToken(now)}.txt",
            filePath = file.absolutePath,
            mimeType = "text/plain",
            thumbnailPath = thumb.absolutePath,
            textPreview = normalizedText.take(120)
        )
        upsert(record)
        return record
    }

    @Synchronized
    fun deleteRecord(recordId: String) {
        val records = loadRecords().toMutableList()
        val target = records.firstOrNull { it.id == recordId } ?: return
        File(target.filePath).delete()
        target.thumbnailPath?.let { File(it).delete() }
        records.removeAll { it.id == recordId }
        persist(records)
    }

    fun shareRecord(record: HistoryRecord) {
        val (sharedName, uri) = prepareExternalFile(record)
        val chooserTitle = when (record.kind) {
            HistoryRecordKind.Document -> "分享扫描文档"
            HistoryRecordKind.IdCard -> "分享证件扫描"
            HistoryRecordKind.Ocr -> "分享识别文本"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, sharedName)
            clipData = ClipData.newUri(context.contentResolver, sharedName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openRecord(record: HistoryRecord) {
        val (displayName, uri) = prepareExternalFile(record)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, record.mimeType)
            clipData = ClipData.newUri(context.contentResolver, displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Starting an activity does not require package visibility. A preflight
        // resolveActivity() can hide installed PDF viewers on Android 11+.
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw IllegalStateException("未找到可预览此类文件的应用", error)
        }
    }

    private fun prepareExternalFile(record: HistoryRecord): Pair<String, android.net.Uri> {
        val sourceFile = File(record.filePath)
        require(sourceFile.isFile) { "文档文件不存在，可能已被清理" }
        val displayName = record.fileName
            .let { if (it.startsWith("桔子扫描_")) it else "桔子扫描_$it" }
        val externalFile = File(context.cacheDir, displayName)
        sourceFile.copyTo(externalFile, overwrite = true)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            externalFile
        )
        return displayName to uri
    }

    fun decodeThumbnail(record: HistoryRecord): Bitmap? {
        val path = record.thumbnailPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    private fun saveThumbnail(recordId: String, bitmap: Bitmap): File {
        val output = File(thumbDir, "$recordId.jpg")
        val scaled = scaleThumbnail(bitmap, 360)
        output.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return output
    }

    private fun scaleThumbnail(bitmap: Bitmap, maxSize: Int): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxEdge.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun buildOcrSubtitle(text: String): String {
        val lineCount = text.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        return "$lineCount 行识别文字 · ${text.length} 字"
    }

    private fun timestampToken(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
    }

    private fun upsert(record: HistoryRecord) {
        val records = loadRecords().toMutableList()
        records.removeAll { it.id == record.id }
        records.add(0, record)
        persist(records)
    }

    private fun persist(records: List<HistoryRecord>) {
        val array = JSONArray()
        records.sortedByDescending { it.createdAtMillis }.forEach { record ->
            array.put(record.toJson())
        }
        recordsFile.writeText(array.toString(), Charsets.UTF_8)
    }

    private fun HistoryRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("title", title)
            .put("subtitle", subtitle)
            .put("createdAtMillis", createdAtMillis)
            .put("pageCount", pageCount)
            .put("fileName", fileName)
            .put("filePath", filePath)
            .put("mimeType", mimeType)
            .put("thumbnailPath", thumbnailPath)
            .put("textPreview", textPreview)
    }

    private fun JSONObject.toHistoryRecord(): HistoryRecord {
        return HistoryRecord(
            id = getString("id"),
            kind = HistoryRecordKind.valueOf(getString("kind")),
            title = getString("title"),
            subtitle = getString("subtitle"),
            createdAtMillis = getLong("createdAtMillis"),
            pageCount = optInt("pageCount", 1),
            fileName = getString("fileName"),
            filePath = getString("filePath"),
            mimeType = getString("mimeType"),
            thumbnailPath = optString("thumbnailPath").ifBlank { null },
            textPreview = optString("textPreview").ifBlank { null }
        )
    }
}
