package cn.yuandianai.juziscan.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapUtilsTest {
    @Test
    fun boundedDimensions_keepSmallImageUnchanged() {
        assertEquals(1600 to 1200, calculateBoundedDimensions(1600, 1200, 2200))
    }

    @Test
    fun boundedDimensions_scaleLandscapeImageToLimit() {
        assertEquals(2200 to 1650, calculateBoundedDimensions(4000, 3000, 2200))
    }

    @Test
    fun boundedDimensions_scalePortraitImageToLimit() {
        val (width, height) = calculateBoundedDimensions(3024, 4032, 2200)
        assertEquals(1650, width)
        assertEquals(2200, height)
        assertTrue(width > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundedDimensions_rejectInvalidLimit() {
        calculateBoundedDimensions(100, 100, 0)
    }

    @Test
    fun bitmapSampleSize_downsamplesHighResolutionCameraImage() {
        assertEquals(4, calculateBitmapSampleSize(8192, 6144, 2200))
        assertEquals(2, calculateBitmapSampleSize(4032, 3024, 2200))
        assertEquals(1, calculateBitmapSampleSize(1920, 1080, 2200))
    }
}
