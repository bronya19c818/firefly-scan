package cn.yuandianai.juziscan.history

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import cn.yuandianai.juziscan.scan.*
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Immutable version directories; records.json is the sole commit point. */
internal class DocumentStore(private val root: File) {
    fun writeVersion(directory: File, pages: List<ScannedPage>) {
        check(directory.mkdirs()) { "无法创建文档目录" }
        val metadata = JSONArray()
        pages.forEach { page ->
            val pageDir = File(directory, page.id)
            require(page.id.matches(Regex("[a-zA-Z0-9-]+"))) { "无效页面 ID" }
            check(pageDir.mkdir()) { "页面 ID 重复" }
            saveImage(File(pageDir, "source.png"), page.sourceImagePath, page.sourceBitmap)
            saveImage(File(pageDir, "processed.png"), page.processedImagePath, page.processedBitmap)
            val preview = requireNotNull(page.previewBitmap) { "页面尚未处理完成" }
            val small = preparePreviewBitmap(preview, 640)
            try { saveBitmap(File(pageDir, "preview.jpg"), small, Bitmap.CompressFormat.JPEG) }
            finally { if (small !== preview) small.recycle() }
            metadata.put(JSONObject().put("id", page.id).put("contentRevision", page.contentRevision)
                .put("rotationDegrees", page.rotationDegrees).put("correctionMode", page.correctionMode.name)
                .put("imageStyle", page.imageStyle).put("quad", JSONArray().apply {
                    page.editableQuad.forEach { put(JSONArray().put(it.x).put(it.y)) }
                }))
        }
        val manifest = JSONObject().put("schemaVersion", 1).put("pages", metadata)
        writeSynced(File(directory, "document.json")) { it.write(manifest.toString().toByteArray(Charsets.UTF_8)) }
        writeSynced(File(directory, "document.pdf")) { writePdfToStream(it, pages, ExportFileSize.Actual) }
        val first = pages.first()
        val preview = requireNotNull(first.previewBitmap)
        val rotated = if (first.rotationDegrees == 0f) preview else SimpleDocumentScanner.rotate(preview, first.rotationDegrees)
        val thumb = preparePreviewBitmap(rotated, 360)
        try { saveBitmap(File(directory, "thumbnail.jpg"), thumb, Bitmap.CompressFormat.JPEG) }
        finally {
            if (thumb !== rotated) thumb.recycle()
            if (rotated !== preview) rotated.recycle()
        }
    }

    fun loadPages(manifestPath: String): List<ScannedPage> {
        val manifestFile = File(manifestPath)
        require(manifestFile.canonicalPath.startsWith(root.canonicalPath + File.separator))
        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.getInt("schemaVersion") == 1) { "不支持的文档格式" }
        val entries = manifest.getJSONArray("pages")
        require(entries.length() > 0) { "文档没有页面" }
        val ids = HashSet<String>()
        return buildList {
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val id = item.getString("id")
                require(id.matches(Regex("[a-zA-Z0-9-]+")) && ids.add(id)) { "无效页面 ID" }
                val dir = File(manifestFile.parentFile, id)
                val source = File(dir, "source.png")
                val processed = File(dir, "processed.png")
                require(source.isFile && source.length() > 0 && processed.isFile && processed.length() > 0) { "文档页面文件缺失" }
                val preview = requireNotNull(BitmapFactory.decodeFile(File(dir, "preview.jpg").path)) { "无法读取页面预览" }
                val quad = item.getJSONArray("quad")
                add(ScannedPage(id = id, sourceBitmap = preview, previewBitmap = preview,
                    sourceImagePath = source.path, processedImagePath = processed.path,
                    contentRevision = item.getString("contentRevision"), imageStyle = item.getString("imageStyle"),
                    rotationDegrees = item.getDouble("rotationDegrees").toFloat(),
                    correctionMode = SimpleDocumentScanner.CorrectionMode.valueOf(item.getString("correctionMode")),
                    editableQuad = (0 until quad.length()).map { n ->
                        val point = quad.getJSONArray(n)
                        PointF(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                    }))
            }
        }
    }

    private fun saveImage(file: File, existingPath: String?, bitmap: Bitmap?) {
        if (existingPath != null) {
            File(existingPath).inputStream().use { input -> writeSynced(file) { input.copyTo(it) } }
        } else {
            saveBitmap(file, requireNotNull(bitmap) { "页面尚未处理完成" }, Bitmap.CompressFormat.PNG)
        }
    }

    private fun saveBitmap(file: File, bitmap: Bitmap, format: Bitmap.CompressFormat) = writeSynced(file) {
        check(bitmap.compress(format, 92, it)) { "图片保存失败" }
    }

    private fun writeSynced(file: File, write: (FileOutputStream) -> Unit) {
        FileOutputStream(file).use { output -> write(output); output.flush(); output.fd.sync() }
    }
}
