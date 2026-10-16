package cn.yuandianai.juziscan.scan

import android.content.Context
import android.graphics.Bitmap
import cn.yuandianai.juziscan.scanner.SimpleDocumentScanner
import java.io.File
import java.io.FileOutputStream

/**
 * 单页文档处理：透视矫正 → 图像增强 → 处理图编码落盘 → 预览图。
 *
 * 供两处复用：
 * - 扫描流程的后台逐页处理队列（[PageProcessingQueue]）；
 * - 历史文档按需生成 PDF 前的缺页补处理。
 *
 * 阻塞式实现，调用方自行选择调度器。
 *
 * @param processedOutput 处理结果的目标文件；为 null 时写入应用缓存临时目录。
 * @param onIntermediate 透视矫正完成后、图像增强之前的中间结果回调（渐进预览）。
 *   回调在调用方线程执行，调用方需自行切回主线程再更新界面。
 */
fun processDocumentPage(
    context: Context,
    page: ScannedPage,
    processedOutput: File? = null,
    onIntermediate: ((ScannedPage) -> Unit)? = null
): ScannedPage {
    page.processedImagePath?.let { path ->
        val file = File(path)
        if (file.isFile && file.length() > 0) return page
    }
    val decodedSource = page.sourceImagePath?.let { decodeBitmapFromFileBounded(it, DOCUMENT_INPUT_MAX_SIDE) }
    val source = decodedSource ?: page.sourceBitmap
    requireNotNull(source) { "页面原图缺失，无法处理" }
    require(!source.isRecycled) { "页面原图已释放" }

    var corrected: Bitmap? = null
    var processed: Bitmap? = null
    // 小尺寸页面下降采样会直接返回原图，回收时必须避开预览位图本身。
    var preview: Bitmap? = null
    try {
        val quad = if (page.editableQuad.size == 4) {
            page.editableQuad
        } else {
            val working = prepareWorkingBitmap(source)
            try {
                SimpleDocumentScanner.detectDocumentQuad(context, working) ?: emptyList()
            } finally {
                if (working !== source && !working.isRecycled) working.recycle()
            }
        }
        corrected = if (quad.size == 4) {
            SimpleDocumentScanner.perspectiveCorrect(
                source,
                quad,
                page.correctionMode,
                maxOutputSide = DOCUMENT_INPUT_MAX_SIDE
            )
        } else {
            source
        }
        if (corrected !== source && onIntermediate != null) {
            // 渐进预览：先把透视矫正后的裁剪结果交给界面，再继续做图像增强，
            // 让用户提前看到页面内容，且不会误认为画面已经是最终增强结果。
            val cropPreview = preparePreviewBitmap(corrected, 1024).let {
                if (it === corrected) it.copy(Bitmap.Config.ARGB_8888, false) else it
            }
            runCatching { onIntermediate(page.copy(previewBitmap = cropPreview)) }
                .onFailure { android.util.Log.w("JuZiScan", "Could not publish crop preview", it) }
        }
        processed = SimpleDocumentScanner.enhanceDocument(corrected)
        val previewImage = preparePreviewBitmap(processed, 640)
        preview = previewImage
        val output = processedOutput ?: createTempProcessedFile(context)
        try {
            // 目标目录可能尚未创建（文档缓存按页面分目录），先保证父目录存在。
            check(output.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "无法创建页面缓存目录" }
            // tmp + 原子替换：写入文档缓存时崩溃不会留下半截文件。
            val tmp = File(output.parentFile, output.name + ".tmp")
            writeBitmapSynced(tmp, processed, Bitmap.CompressFormat.PNG, 100)
            if (output.exists() && !output.delete()) {
                tmp.delete()
                throw IllegalStateException("处理缓存替换失败")
            }
            check(tmp.renameTo(output)) { "页面暂存失败" }
            if (processedOutput != null) {
                // 直接写入文档缓存时顺带落一份预览缓存，加速下次打开。
                runCatching {
                    val previewTmp = File(output.parentFile, output.nameWithoutExtension + ".preview.tmp")
                    writeBitmapSynced(previewTmp, previewImage, Bitmap.CompressFormat.JPEG, 88)
                    val previewCache = File(output.parentFile, output.nameWithoutExtension + ".jpg")
                    if (previewCache.exists()) previewCache.delete()
                    previewTmp.renameTo(previewCache)
                }.onFailure {
                    android.util.Log.w("JuZiScan", "Could not write page preview cache", it)
                }
            }
        } catch (error: Throwable) {
            if (processedOutput == null) output.delete()
            throw error
        }
        // 处理图已编码落盘，仅保留小尺寸预览位图。
        if (preview !== processed && !processed.isRecycled) processed.recycle()
        return page.copy(
            reviewBitmap = null,
            editableQuad = quad,
            processedBitmap = null,
            processedImagePath = output.path,
            previewBitmap = previewImage
        )
    } finally {
        // 预览位图可能与处理图是同一个对象（小尺寸页面），回收时一并避开。
        if (processed != null && processed !== preview && !processed.isRecycled) processed.recycle()
        if (corrected != null && corrected !== source && !corrected.isRecycled) corrected.recycle()
        decodedSource?.takeIf { !it.isRecycled }?.recycle()
    }
}

/** 未保存文档的处理图临时目录（保存成功后由 [clearTemporaryProcessedFiles] 清理）。 */
private val tempProcessedRootName = "document-page-processing"

fun createTempProcessedFile(context: Context): File {
    val directory = File(context.cacheDir, tempProcessedRootName)
    check(directory.isDirectory || directory.mkdirs()) { "无法创建页面临时目录" }
    return File.createTempFile("page-", ".png", directory)
}

/** 删除位于应用缓存临时目录中的处理图；提交进文档目录的文件不受影响。 */
fun clearTemporaryProcessedPages(context: Context, pages: List<ScannedPage>) {
    runCatching {
        val root = File(context.cacheDir, tempProcessedRootName).canonicalPath + File.separator
        pages.mapNotNull { it.processedImagePath }.distinct().forEach { path ->
            val file = File(path)
            if (file.canonicalPath.startsWith(root) && file.exists() && !file.delete()) {
                android.util.Log.w("JuZiScan", "Could not remove temporary processed page")
            }
        }
    }.onFailure { android.util.Log.w("JuZiScan", "Could not clean temporary processed pages", it) }
}

internal fun writeBitmapSynced(file: File, bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
    FileOutputStream(file).use { output ->
        check(bitmap.compress(format, quality, output)) { "图片保存失败" }
        output.flush()
        output.fd.sync()
    }
}
