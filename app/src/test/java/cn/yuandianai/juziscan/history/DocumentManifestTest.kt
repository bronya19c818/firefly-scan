package cn.yuandianai.juziscan.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentManifestTest {

    private fun entry(
        id: String = "page-1",
        revision: String = "rev-1",
        cacheKey: String = "key-1",
        rotation: Float = 0f,
        quad: List<QuadPoint> = emptyList()
    ) = ManifestPageEntry(
        id = id,
        contentRevision = revision,
        cacheKey = cacheKey,
        rotationDegrees = rotation,
        correctionMode = "AUTO",
        imageStyle = "default",
        quad = quad
    )

    private fun snapshot(
        id: String = "page-1",
        revision: String = "rev-1",
        cacheKey: String = "key-1",
        rotation: Float = 0f,
        sourceExists: Boolean = true,
        hasOutput: Boolean = true,
        cacheExists: Boolean = true
    ) = PageSaveSnapshot(
        id = id,
        contentRevision = revision,
        cacheKey = cacheKey,
        rotationDegrees = rotation,
        correctionMode = "AUTO",
        imageStyle = "default",
        sourceFileExists = sourceExists,
        hasProcessedOutput = hasOutput,
        processedCacheExists = cacheExists
    )

    @Test
    fun unchangedPageIsFullySkipped() {
        val plan = planPageSave(old = entry(), new = snapshot())
        assertTrue("未变化页面不应触碰任何文件", plan.isNoOp)
    }

    @Test
    fun newPageWritesSourceAndReusesNothing() {
        val plan = planPageSave(old = null, new = snapshot(sourceExists = false, cacheExists = false))
        assertTrue(plan.rewriteSource)
        assertTrue(plan.writeProcessedCache)
        assertEquals(null, plan.obsoleteSourceFile)
    }

    @Test
    fun unprocessedPageOnlyPersistsSource() {
        // 原图已保存但处理未完成时，不能写处理缓存，也不能判定为完成。
        val plan = planPageSave(old = null, new = snapshot(hasOutput = false, cacheExists = false))
        assertTrue(plan.rewriteSource)
        assertFalse(plan.writeProcessedCache)
    }

    @Test
    fun rotationChangeDoesNotRewriteImages() {
        val plan = planPageSave(old = entry(rotation = 0f), new = snapshot(rotation = 90f))
        assertFalse("旋转只更新参数", plan.rewriteSource)
        assertFalse("旋转不重建处理图", plan.writeProcessedCache)
        assertEquals(null, plan.obsoleteCacheKey)
    }

    @Test
    fun replacedPageRewritesSourceAndDropsOldAssets() {
        val plan = planPageSave(
            old = entry(revision = "rev-1", cacheKey = "key-1"),
            new = snapshot(revision = "rev-2", cacheKey = "key-2", cacheExists = false)
        )
        assertTrue(plan.rewriteSource)
        assertEquals("rev-1", plan.obsoleteSourceFile)
        assertEquals("key-1", plan.obsoleteCacheKey)
    }

    @Test
    fun completedProcessingOnlyWritesProcessedCache() {
        // 后台处理完成后补写缓存，不重复复制原图。
        val plan = planPageSave(
            old = entry(cacheKey = "key-empty-quad"),
            new = snapshot(cacheKey = "key-detected-quad", cacheExists = false)
        )
        assertFalse(plan.rewriteSource)
        assertTrue(plan.writeProcessedCache)
    }

    @Test
    fun staleCacheIsDroppedWhenOutputMissing() {
        val plan = planPageSave(
            old = entry(cacheKey = "key-old"),
            new = snapshot(cacheKey = "key-new", hasOutput = false, cacheExists = false)
        )
        assertFalse(plan.writeProcessedCache)
        assertEquals("key-old", plan.obsoleteCacheKey)
    }

    @Test
    fun missingSourceIsRepairedWithoutRecopyingCache() {
        val plan = planPageSave(old = entry(), new = snapshot(sourceExists = false))
        assertTrue(plan.rewriteSource)
        assertFalse("缓存仍有效时不重复写处理图", plan.writeProcessedCache)
    }

    @Test
    fun cacheWrittenByBackgroundIsNotReEncoded() {
        // 后台逐页处理已把结果写入文档缓存目录，首次提交清单时不应再编码一次处理图。
        val plan = planPageSave(old = null, new = snapshot(sourceExists = true, cacheExists = true))
        assertFalse("缓存已存在时不重复写处理图", plan.writeProcessedCache)
    }

    @Test
    fun cacheKeyIgnoresRotationButTracksQuadAndMode() {
        val quad = listOf(QuadPoint(0.1f, 0.2f), QuadPoint(0.9f, 0.2f), QuadPoint(0.9f, 0.8f), QuadPoint(0.1f, 0.8f))
        val base = computePageCacheKey("rev-1", "AUTO", "default", quad)
        val rotated = computePageCacheKey("rev-1", "AUTO", "default", quad)
        val otherQuad = computePageCacheKey("rev-1", "AUTO", "default", quad.drop(1))
        val otherMode = computePageCacheKey("rev-1", "DOCUMENT", "default", quad)
        val otherStyle = computePageCacheKey("rev-1", "AUTO", "gray", quad)
        assertEquals("旋转不参与缓存键", base, rotated)
        assertNotEquals(base, otherQuad)
        assertNotEquals(base, otherMode)
        assertNotEquals(base, otherStyle)
    }

    @Test
    fun coverSignatureTracksFirstPageIdentity() {
        val first = entry(id = "a", revision = "r1", rotation = 0f)
        val second = entry(id = "b", revision = "r1", rotation = 0f)
        assertEquals(computeCoverSignature(first), computeCoverSignature(first.copy()))
        assertNotEquals(computeCoverSignature(first), computeCoverSignature(second))
        assertNotEquals(
            computeCoverSignature(first),
            computeCoverSignature(first.copy(rotationDegrees = 90f))
        )
    }
}
