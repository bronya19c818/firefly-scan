package cn.yuandianai.juziscan.history

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 纯数据模型：文档清单（document.json）与增量保存规划。
 *
 * 不持有任何 Bitmap / Android 运行时对象，便于 JVM 单元测试。
 *
 * 磁盘布局（schemaVersion 2）：
 * ```
 * documents/{documentId}/
 * ├── document.json                 # 唯一提交点（原子替换）
 * ├── thumbnail.jpg                 # 文档封面缓存
 * ├── pages/{pageId}/{revision}.png # 不可变原图（一个内容版本一个文件）
 * └── cache/                        # 全部可重建缓存
 *     ├── pages/{pageId}/{cacheKey}.png
 *     ├── pages/{pageId}/{cacheKey}.jpg   # 预览缓存
 *     └── document.pdf (+ .meta 指纹)     # 按需 PDF 缓存
 * ```
 */
data class QuadPoint(val x: Float, val y: Float)

data class ManifestPageEntry(
    val id: String,
    val contentRevision: String,
    val cacheKey: String,
    val rotationDegrees: Float,
    val correctionMode: String,
    val imageStyle: String,
    val quad: List<QuadPoint>,
    val sourceExtension: String = ".png"
)

data class DocumentManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val pages: List<ManifestPageEntry>,
    val coverSignature: String,
    val recordId: String? = null,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val title: String? = null,
    val fileName: String? = null,
    val fingerprint: String? = null,
    val version: Long = 0
) {
    fun findPage(pageId: String): ManifestPageEntry? = pages.firstOrNull { it.id == pageId }

    companion object {
        const val SCHEMA_VERSION = 2
        const val LEGACY_SCHEMA_VERSION = 1
    }
}

/** 一次保存中单个页面的写入计划。 */
data class PageSavePlan(
    val rewriteSource: Boolean,
    val writeProcessedCache: Boolean,
    /** 提交成功后需要删除的旧资产（页面内容版本或缓存版本变化产生）。 */
    val obsoleteSourceFile: String? = null,
    val obsoleteCacheKey: String? = null
) {
    val isNoOp: Boolean
        get() = !rewriteSource && !writeProcessedCache && obsoleteSourceFile == null && obsoleteCacheKey == null
}

/** 规划器输入：与磁盘状态对齐后的页面快照（无 Bitmap）。 */
data class PageSaveSnapshot(
    val id: String,
    val contentRevision: String,
    val cacheKey: String,
    val rotationDegrees: Float,
    val correctionMode: String,
    val imageStyle: String,
    /** 当前期望的 source 文件（按 revision 命名）是否已存在于磁盘。 */
    val sourceFileExists: Boolean,
    /** 页面是否携带处理结果（内存位图或已生成文件）。 */
    val hasProcessedOutput: Boolean,
    /** 当前期望的 processed 缓存文件是否已存在于磁盘。 */
    val processedCacheExists: Boolean
)

/**
 * 计算页面处理缓存键：内容版本 + 影响处理输出的参数（矫正模式、风格、四角）。
 * 旋转只影响展示方向，不参与缓存键——处理图本身不旋转。
 */
fun computePageCacheKey(
    contentRevision: String,
    correctionMode: String,
    imageStyle: String,
    quad: List<QuadPoint>
): String {
    val value = buildString {
        append(contentRevision).append('|')
        append(correctionMode).append('|')
        append(imageStyle).append('|')
        quad.forEach { append("%.4f:%.4f,".format(it.x, it.y)) }
    }
    return sha256Hex(value)
}

/** 裁剪与处理参数也影响封面，但不改变不可变原图的内容版本。 */
fun computeCoverSignature(firstPage: ManifestPageEntry): String =
    sha256Hex("${firstPage.id}|${firstPage.contentRevision}|${firstPage.rotationDegrees}|${firstPage.cacheKey}")

/**
 * 增量保存规划：对比旧清单条目与新的页面快照，决定该页需要写入哪些文件。
 *
 * 关键性质：
 * - 旋转/排序仅变化元数据 → 全部跳过文件写入；
 * - 新增/替换页面 → 只写该页的 source（不可变新文件名）；
 * - 后台处理完成 → 只写该页的 processed 缓存；
 * - 未处理完成的页面 → 只保证原图落盘。
 */
fun planPageSave(old: ManifestPageEntry?, new: PageSaveSnapshot): PageSavePlan {
    // 四角 / 矫正模式 / 风格的变化已体现在缓存键上，旋转只影响清单元数据。
    val rewriteSource = old == null || old.contentRevision != new.contentRevision || !new.sourceFileExists
    // 处理缓存以「目标文件是否已存在」为准：后台队列已写好的缓存不再重复编码。
    val writeProcessedCache = new.hasProcessedOutput && !new.processedCacheExists

    val obsoleteSource = if (old != null && old.contentRevision != new.contentRevision) old.contentRevision else null
    val obsoleteCache = if (old != null && old.cacheKey != new.cacheKey) old.cacheKey else null

    return PageSavePlan(
        rewriteSource = rewriteSource,
        writeProcessedCache = writeProcessedCache,
        obsoleteSourceFile = obsoleteSource,
        obsoleteCacheKey = obsoleteCache
    )
}

internal fun sha256Hex(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

// ---------------------------------------------------------------------------
// JSON 序列化（仅在 Android 运行时使用；单元测试只覆盖纯规划逻辑）
// ---------------------------------------------------------------------------

fun parseDocumentManifest(file: File): DocumentManifest {
    val root = JSONObject(file.readText(Charsets.UTF_8))
    val schemaVersion = root.getInt("schemaVersion")
    require(schemaVersion == DocumentManifest.SCHEMA_VERSION ||
        schemaVersion == DocumentManifest.LEGACY_SCHEMA_VERSION) { "不支持的文档格式" }
    val pagesArray = root.getJSONArray("pages")
    require(pagesArray.length() > 0) { "文档没有页面" }
    val seen = HashSet<String>()
    val pages = buildList {
        for (index in 0 until pagesArray.length()) {
            val item = pagesArray.getJSONObject(index)
            val id = item.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9-]+")) && seen.add(id)) { "无效页面 ID" }
            val quadArray = item.optJSONArray("quad") ?: JSONArray()
            val quad = buildList {
                for (n in 0 until quadArray.length()) {
                    val point = quadArray.getJSONArray(n)
                    add(QuadPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
                }
            }
            val revision = item.getString("contentRevision")
            val correctionMode = item.optString("correctionMode", "AUTO")
            val imageStyle = item.optString("imageStyle", "default")
            add(
                ManifestPageEntry(
                    id = id,
                    contentRevision = revision,
                    cacheKey = item.optString("cacheKey").ifBlank {
                        // 兼容 schema 1：按条目参数推导缓存键。
                        computePageCacheKey(revision, correctionMode, imageStyle, quad)
                    },
                    rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat(),
                    correctionMode = correctionMode,
                    imageStyle = imageStyle,
                    quad = quad,
                    sourceExtension = item.optString("sourceExtension", ".png").also {
                        require(it in setOf(".png", ".jpg")) { "无效原图格式" }
                    }
                )
            )
        }
    }
    val manifest = DocumentManifest(schemaVersion = schemaVersion, pages = pages, coverSignature = root.optString("coverSignature"),
        recordId = root.optString("recordId").takeIf { it.isNotBlank() && it != "null" },
        createdAtMillis = root.optLong("createdAtMillis"), updatedAtMillis = root.optLong("updatedAtMillis"),
        title = root.optString("title").takeIf { it.isNotBlank() && it != "null" },
        fileName = root.optString("fileName").takeIf { it.isNotBlank() && it != "null" },
        fingerprint = root.optString("fingerprint").takeIf { it.isNotBlank() && it != "null" }, version = root.optLong("version"))
    return if (manifest.coverSignature.isBlank()) {
        manifest.copy(coverSignature = computeCoverSignature(pages.first()))
    } else {
        manifest
    }
}

fun DocumentManifest.toJsonBytes(): ByteArray {
    val pagesArray = JSONArray()
    pages.forEach { page ->
        pagesArray.put(
            JSONObject()
                .put("id", page.id)
                .put("contentRevision", page.contentRevision)
                .put("cacheKey", page.cacheKey)
                .put("sourceExtension", page.sourceExtension)
                .put("rotationDegrees", page.rotationDegrees.toDouble())
                .put("correctionMode", page.correctionMode)
                .put("imageStyle", page.imageStyle)
                .put(
                    "quad",
                    JSONArray().apply { page.quad.forEach { put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) } }
                )
        )
    }
    return JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("coverSignature", coverSignature)
        .put("recordId", recordId).put("createdAtMillis", createdAtMillis).put("updatedAtMillis", updatedAtMillis)
        .put("title", title).put("fileName", fileName).put("fingerprint", fingerprint).put("version", version)
        .put("pages", pagesArray)
        .toString()
        .toByteArray(Charsets.UTF_8)
}
