package cn.yuandianai.juziscan.scan

/** Stable file identifiers; legacy documents retain their existing cache identity. */
enum class DocumentImageStyle(val id: String, val label: String) {
    Original("original", "原图"), Color("color", "彩色增强"), White("white", "白纸增强"),
    Grayscale("grayscale", "灰度"), BlackWhite("bw", "黑白文档");

    companion object {
        const val PREFERENCE_KEY = "default_image_style"
        fun parse(value: String?): DocumentImageStyle =
            if (value == "default") Grayscale else entries.firstOrNull { it.id == value } ?: Color
    }
}

/** Rotation is presentation metadata; all image-processing parameters must match. */
fun ScannedPage.hasSameProcessingParameters(other: ScannedPage): Boolean =
    id == other.id && contentRevision == other.contentRevision && imageStyle == other.imageStyle &&
        correctionMode == other.correctionMode &&
        editableQuad.map { it.x to it.y } == other.editableQuad.map { it.x to it.y }
