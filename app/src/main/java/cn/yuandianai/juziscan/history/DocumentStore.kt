package cn.yuandianai.juziscan.history

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import androidx.core.util.AtomicFile
import cn.yuandianai.juziscan.scan.*
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 独立页面资产管理：
 * - 原图是不可变资产（一个内容版本一个文件），处理图 / 预览图 / PDF 全部是可重建缓存；
 * - 保存只写入新增或真正变化的页面资产，清单（document.json）原子替换作为唯一提交点；
 * - 不再复制整份文档实现回退，失败时旧清单仍指向旧资产，文档保持最后一次有效状态。
 */
internal class DocumentStore(private val root: File) {

    fun save(documentDir: File, pages: List<ScannedPage>, metadata: DocumentManifest? = null,
             reliableOnly: Boolean = false): DocumentManifest {
        check(documentDir.canonicalPath.startsWith(root.canonicalPath + File.separator) ||
            documentDir.canonicalPath == root.canonicalPath) { "文档目录越界" }
        require(pages.isNotEmpty()) { "文档没有页面" }
        require(pages.distinctBy { it.id }.size == pages.size) { "页面 ID 重复" }
        val pagesDir = File(documentDir, PAGES_DIR)
        check(pagesDir.exists() || pagesDir.mkdirs()) { "无法创建文档目录" }

        val oldManifest = readManifestOrNull(documentDir)
        val oldEntries = oldManifest?.pages?.associateBy { it.id } ?: emptyMap()
        val obsoleteFiles = mutableListOf<File>()

        val entries = pages.map { page ->
            require(page.id.matches(Regex("[a-zA-Z0-9-]+"))) { "无效页面 ID" }
            val entry = buildEntry(page)
            val sourceFile = pageSourceFile(documentDir, entry)
            val processedFile = processedCacheFile(documentDir, entry)
            val processedOutputAvailable = page.processedImagePath?.let {
                File(it).isFile && File(it).length() > 0
            } == true || page.processedBitmap != null

            val plan = planPageSave(
                old = oldEntries[page.id],
                new = PageSaveSnapshot(
                    id = entry.id,
                    contentRevision = entry.contentRevision,
                    cacheKey = entry.cacheKey,
                    rotationDegrees = entry.rotationDegrees,
                    correctionMode = entry.correctionMode,
                    imageStyle = entry.imageStyle,
                    sourceFileExists = sourceFile.isFile && sourceFile.length() > 0,
                    hasProcessedOutput = processedOutputAvailable,
                    processedCacheExists = processedFile.isFile && processedFile.length() > 0
                )
            )

            if (plan.rewriteSource) {
                check(File(pagesDir, page.id).isDirectory || File(pagesDir, page.id).mkdirs()) { "无法创建页面目录" }
                writeSourceAtomically(sourceFile, page)
            }
            if (plan.writeProcessedCache && !reliableOnly) {
                writeProcessedCacheAtomically(processedFile, page)
            }
            plan.obsoleteSourceFile?.let { revision ->
                obsoleteFiles += File(File(pagesDir, page.id), revision + (oldEntries[page.id]?.sourceExtension ?: SOURCE_EXT))
            }
            plan.obsoleteCacheKey?.let { key ->
                obsoleteFiles += File(processedCacheDir(documentDir, page.id), "$key${PROCESSED_EXT}")
                obsoleteFiles += File(processedCacheDir(documentDir, page.id), "$key${PREVIEW_EXT}")
            }
            entry
        }

        // 唯一提交点：所有页面资产写入成功后，原子替换清单。
        val manifest = (metadata ?: oldManifest ?: DocumentManifest(pages = entries, coverSignature = ""))
            .copy(schemaVersion = DocumentManifest.SCHEMA_VERSION, pages = entries, coverSignature = computeCoverSignature(entries.first()))
        writeManifestAtomically(documentDir, manifest)
        if (reliableOnly) return manifest
        ensureThumbnail(documentDir, manifest, oldManifest?.coverSignature)
        invalidatePdfCache(documentDir)

        // 清理必须发生在提交之后，不能把一次成功保存变成失败。
        runCatching { cleanupObsoleteAssets(documentDir, manifest, obsoleteFiles) }
            .onFailure { Log.w("JuZiScan", "Could not clean obsolete page assets", it) }
        return manifest
    }

    fun loadPages(manifestPath: String): List<ScannedPage> {
        val manifestFile = File(manifestPath)
        check(manifestFile.canonicalPath.startsWith(root.canonicalPath + File.separator))
        val manifest = parseDocumentManifest(manifestFile)
        val legacy = manifest.schemaVersion == DocumentManifest.LEGACY_SCHEMA_VERSION
        return manifest.pages.map { entry ->
            val sourceFile = if (legacy) {
                File(manifestFile.parentFile, "${entry.id}/source$SOURCE_EXT")
            } else {
                pageSourceFile(manifestFile.parentFile!!, entry)
            }
            require(sourceFile.isFile && sourceFile.length() > 0) { "文档页面文件缺失" }
            val processedFile = if (legacy) {
                File(manifestFile.parentFile, "${entry.id}/processed$PROCESSED_EXT")
            } else {
                processedCacheFile(manifestFile.parentFile!!, entry)
            }
            val processedPath = processedFile.takeIf { it.isFile && it.length() > 0 }?.path
            val previewFile = if (legacy) {
                File(manifestFile.parentFile, "${entry.id}/preview.jpg")
            } else {
                File(processedFile.parentFile, processedFile.nameWithoutExtension + PREVIEW_EXT)
            }
            // 预览优先用缓存；缓存缺失（可重建数据被清理）时降级解码原图，页面随后按未处理继续后台补处理。
            val preview = requireNotNull(decodeDisplayBitmap(previewFile, sourceFile)) { "无法读取页面预览" }
            ScannedPage(
                id = entry.id,
                sourceBitmap = preview,
                previewBitmap = preview,
                sourceImagePath = sourceFile.path,
                processedImagePath = processedPath,
                detectedQuad = readDetectedQuad(processedFile),
                contentRevision = entry.contentRevision,
                imageStyle = entry.imageStyle,
                rotationDegrees = entry.rotationDegrees,
                correctionMode = SimpleDocumentScanner.CorrectionMode.valueOf(entry.correctionMode),
                editableQuad = entry.quad.map { PointF(it.x, it.y) }
            )
        }
    }

    /** 为已保存文档的页面计算处理缓存目标文件（后台处理直接写入文档缓存）。 */
    fun processedCacheFileFor(documentDir: File, page: ScannedPage): File =
        processedCacheFile(documentDir, buildEntry(page))

    /** 已保存文档页面的原图文件路径（保存提交后重绑定内存页面使用）。 */
    fun pageSourcePathFor(documentDir: File, page: ScannedPage): String =
        pageSourceFile(documentDir, buildEntry(page)).path

    fun maintainCaches(documentDir: File) {
        val manifest = readManifestOrNull(documentDir) ?: return
        val signature = File(documentDir, "$CACHE_DIR/thumbnail.meta").takeIf { it.isFile }?.readText()
            ?: manifest.coverSignature.takeIf { manifest.recordId == null }
        if (processedCacheFile(documentDir, manifest.pages.first()).isFile) {
            ensureThumbnail(documentDir, manifest, signature)
        }
        cleanupObsoleteAssets(documentDir, manifest, emptyList())
    }

    fun pdfCacheFile(documentDir: File): File = File(File(documentDir, CACHE_DIR), PDF_NAME)

    fun pdfMetaFile(pdfFile: File): File = File(pdfFile.path + PDF_META_SUFFIX)

    /**
     * 收敛清单（不写页面资产、不做清理）：按页面当前参数重写 document.json，
     * 用于 PDF 按需生成时把后台补处理得到的四角 / 缓存键落盘。
     * 兼容 schema 1：确保 v2 路径上存在不可变原图。
     */
    fun refreshManifest(documentDir: File, pages: List<ScannedPage>): String {
        require(pages.isNotEmpty()) { "文档没有页面" }
        val oldCoverSignature = readManifestOrNull(documentDir)?.coverSignature
        val entries = pages.map { page ->
            val entry = buildEntry(page)
            val target = pageSourceFile(documentDir, entry)
            if (!target.isFile) {
                val existing = page.sourceImagePath?.let { File(it) }
                if (existing != null && existing.isFile) {
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建页面目录" }
                    runCatching { copyFileAtomically(existing, target) }
                        .onFailure { Log.w("JuZiScan", "Could not migrate legacy page source", it) }
                }
            }
            entry
        }
        val manifest = (readManifestOrNull(documentDir) ?: DocumentManifest(pages = entries, coverSignature = ""))
            .copy(schemaVersion = DocumentManifest.SCHEMA_VERSION, pages = entries, coverSignature = computeCoverSignature(entries.first()))
        writeManifestAtomically(documentDir, manifest)
        ensureThumbnail(documentDir, manifest, oldCoverSignature)
        return File(documentDir, MANIFEST_NAME).absolutePath
    }

    fun invalidatePdfCache(documentDir: File) {
        pdfCacheFile(documentDir).delete()
        pdfMetaFile(pdfCacheFile(documentDir)).delete()
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private fun buildEntry(page: ScannedPage): ManifestPageEntry {
        val quad = page.editableQuad.map { QuadPoint(it.x, it.y) }
        return ManifestPageEntry(
            id = page.id,
            contentRevision = page.contentRevision,
            cacheKey = computePageCacheKey(page.contentRevision, page.correctionMode.name, page.imageStyle, quad),
            rotationDegrees = normalizePageRotation(page.rotationDegrees),
            correctionMode = page.correctionMode.name,
            imageStyle = page.imageStyle,
            quad = quad,
            sourceExtension = if (page.sourceImagePath?.endsWith(".jpg") == true) ".jpg" else ".png"
        )
    }

    fun readManifestOrNull(documentDir: File): DocumentManifest? {
        val file = File(documentDir, MANIFEST_NAME)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            parseDocumentManifest(file)
        } catch (error: Exception) {
            Log.w("JuZiScan", "Document manifest unreadable, rebuilding", error)
            null
        }
    }

    private fun pageSourceFile(documentDir: File, entry: ManifestPageEntry): File =
        File(File(documentDir, "$PAGES_DIR/${entry.id}"), entry.contentRevision + entry.sourceExtension)

    private fun processedCacheDir(documentDir: File, pageId: String): File =
        File(documentDir, "$CACHE_DIR/$PAGES_DIR/$pageId")

    private fun processedCacheFile(documentDir: File, entry: ManifestPageEntry): File =
        File(processedCacheDir(documentDir, entry.id), entry.cacheKey + PROCESSED_EXT)

    private fun writeSourceAtomically(target: File, page: ScannedPage) {
        if (target.isFile && target.length() > 0) return
        val existing = page.sourceImagePath?.let { File(it) }
        require(page.sourceImagePath == null || (existing?.isFile == true && existing.length() > 0)) {
            "原图文件缺失，无法保存页面"
        }
        if (existing != null && existing.isFile) {
            if (existing.canonicalPath == target.canonicalPath) return
            // CameraX staging and permanent assets share a filesystem; move the encoded original once.
            if (!existing.canonicalPath.startsWith(File(root.parentFile, "captures").canonicalPath + File.separator) ||
                !existing.renameTo(target)) copyFileAtomically(existing, target)
        } else {
            val bitmap = requireNotNull(page.sourceBitmap) { "页面尚未处理完成" }
            writeBitmapFileAtomically(target, bitmap, Bitmap.CompressFormat.PNG, 100)
        }
    }

    private fun writeProcessedCacheAtomically(target: File, page: ScannedPage) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建页面缓存目录" }
        val existing = page.processedImagePath?.let { File(it) }
        when {
            existing != null && existing.isFile -> {
                if (existing.canonicalPath == target.canonicalPath) {
                    // 后台处理已直接写入文档缓存。
                } else {
                    copyFileAtomically(existing, target)
                }
            }
            else -> {
                val bitmap = requireNotNull(page.processedBitmap) { "页面处理结果缺失" }
                writeBitmapFileAtomically(target, bitmap, Bitmap.CompressFormat.PNG, 100)
            }
        }
        // 处理图就位后同步落一份预览缓存，加速下次打开；缓存统一降采样，
        // 保证重开文档时常驻位图不会比 640 缩图更大。
        val previewSource = page.previewBitmap
        if (previewSource != null) {
            val previewFile = File(target.parentFile, target.nameWithoutExtension + PREVIEW_EXT)
            runCatching {
                val small = preparePreviewBitmap(previewSource, PREVIEW_MAX_SIDE)
                try {
                    writeBitmapFileAtomically(previewFile, small, Bitmap.CompressFormat.JPEG, 88)
                } finally {
                    if (small !== previewSource && !small.isRecycled) small.recycle()
                }
            }.onFailure { Log.w("JuZiScan", "Could not write page preview cache", it) }
        }
    }

    private fun writeManifestAtomically(documentDir: File, manifest: DocumentManifest) {
        writeBytesAtomically(File(documentDir, MANIFEST_NAME), manifest.toJsonBytes())
    }

    /**
     * 索引写入失败时的补偿：把上一次有效清单原子写回，避免「内容已提交、索引仍指向旧状态」的不一致。
     * 本次已写入的新页面资产变成未引用数据，会在下一次成功保存的清理阶段被回收。
     */
    fun restoreManifest(documentDir: File, manifestBytes: ByteArray) {
        writeBytesAtomically(File(documentDir, MANIFEST_NAME), manifestBytes)
    }

    private fun ensureThumbnail(documentDir: File, manifest: DocumentManifest, oldCoverSignature: String?) {
        val thumbnail = File(documentDir, THUMBNAIL_NAME)
        if (thumbnail.isFile && oldCoverSignature == manifest.coverSignature) return
        val first = manifest.pages.first()
        val sourceFile = pageSourceFile(documentDir, first)
        val previewCache = File(processedCacheDir(documentDir, first.id), first.cacheKey + PREVIEW_EXT)
        val bitmap = decodeDisplayBitmap(previewCache, sourceFile) ?: run {
            Log.w("JuZiScan", "Could not decode page for document thumbnail")
            return
        }
        var rotated: Bitmap = bitmap
        try {
            if (first.rotationDegrees % 360f != 0f) {
                rotated = SimpleDocumentScanner.rotate(bitmap, first.rotationDegrees)
            }
            val small = preparePreviewBitmap(rotated, 360)
            try {
                writeBitmapFileAtomically(thumbnail, small, Bitmap.CompressFormat.JPEG, 88)
                val signature = File(documentDir, "$CACHE_DIR/thumbnail.meta")
                check(signature.parentFile!!.isDirectory || signature.parentFile!!.mkdirs())
                writeBytesAtomically(signature, manifest.coverSignature.toByteArray(Charsets.UTF_8))
            } finally {
                if (small !== rotated && !small.isRecycled) small.recycle()
            }
        } finally {
            if (rotated !== bitmap && !rotated.isRecycled) rotated.recycle()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** 提交后清理：未被新清单引用的页面目录 / 旧版本资产 / 遗留的版本目录（schema 1）。 */
    private fun cleanupObsoleteAssets(documentDir: File, manifest: DocumentManifest, obsoleteFiles: List<File>) {
        val referencedIds = manifest.pages.map { it.id }.toSet()
        val pagesDir = File(documentDir, PAGES_DIR)
        pagesDir.listFiles()?.forEach { pageDir ->
            if (pageDir.name !in referencedIds) {
                if (!pageDir.deleteRecursively()) Log.w("JuZiScan", "Could not remove unreferenced page dir")
            } else {
                // 只保留当前内容版本的不可变原图。
                val revision = manifest.pages.first { it.id == pageDir.name }.contentRevision
                pageDir.listFiles()?.forEach { file ->
                    val extension = manifest.pages.first { it.id == pageDir.name }.sourceExtension
                    if (file.name != revision + extension && (file.name.endsWith(".png") || file.name.endsWith(".jpg"))) {
                        if (!file.delete()) Log.w("JuZiScan", "Could not remove obsolete page source")
                    }
                }
            }
        }
        manifest.pages.forEach { entry ->
            val cacheDir = processedCacheDir(documentDir, entry.id)
            if (cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    val abandonedWrite = (file.name.endsWith(".tmp") || file.name.endsWith(".new")) &&
                        file.lastModified() < System.currentTimeMillis() - 24L * 60 * 60 * 1000
                    if (!file.name.startsWith(entry.cacheKey) || abandonedWrite) {
                        if (!file.delete()) Log.w("JuZiScan", "Could not remove obsolete page cache")
                    }
                }
            }
        }
        // 未提交页面的后台处理缓存（退出时放弃的页面）属于可重建缓存，随提交清理。
        File(documentDir, "$CACHE_DIR/$PAGES_DIR").listFiles()?.forEach { dir ->
            if (dir.name !in referencedIds) {
                if (!dir.deleteRecursively()) Log.w("JuZiScan", "Could not remove orphan page cache")
            }
        }
        obsoleteFiles.forEach { it.delete() }
        // schema 1 的版本目录不再是合法子目录，直接清除。
        val allowed = setOf(MANIFEST_NAME, THUMBNAIL_NAME, PAGES_DIR, CACHE_DIR)
        documentDir.listFiles()?.forEach { child ->
            if (child.name !in allowed) child.deleteRecursively()
        }
    }

    private fun decodeDisplayBitmap(previewCache: File, sourceFile: File): Bitmap? {
        if (previewCache.isFile && previewCache.length() > 0) {
            BitmapFactory.decodeFile(previewCache.path)?.let { return it }
        }
        // 预览缓存缺失（可重建数据被清理）时降级解码原图，尺寸仍受 PREVIEW_MAX_SIDE 约束。
        return decodeBitmapFromFileBounded(sourceFile.path, PREVIEW_MAX_SIDE)
    }

    private fun copyFileAtomically(source: File, target: File) {
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        try {
            source.inputStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            replaceFile(tmp, target)
        } finally { tmp.delete() }
    }

    private fun writeBitmapFileAtomically(target: File, bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        try {
            writeBitmapSynced(tmp, bitmap, format, quality)
            replaceFile(tmp, target)
        } finally { tmp.delete() }
    }

    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            output.flush()
            output.fd.sync()
            atomic.finishWrite(output)
            check(!File(target.path + ".new").exists()) { "文档清单提交失败" }
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun replaceFile(tmp: File, target: File) {
        check(tmp.renameTo(target)) { "无法提交文件: ${target.name}" }
    }

    private companion object {
        const val MANIFEST_NAME = "document.json"
        const val THUMBNAIL_NAME = "thumbnail.jpg"
        const val PAGES_DIR = "pages"
        const val CACHE_DIR = "cache"
        const val PDF_NAME = "document.pdf"
        const val PDF_META_SUFFIX = ".meta"
        const val SOURCE_EXT = ".png"
        const val PROCESSED_EXT = ".png"
        const val PREVIEW_EXT = ".jpg"
        const val TMP_SUFFIX = ".tmp"
        /** 常驻预览图的边长上限：持久化预览缓存与降级解码统一受此约束，控制历史列表/预览的内存峰值。 */
        const val PREVIEW_MAX_SIDE = 640
    }
}
