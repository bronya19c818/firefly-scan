package cn.yuandianai.juziscan.history

data class HistoryRecord(
    val id: String,
    val kind: HistoryRecordKind,
    val title: String,
    val subtitle: String,
    val createdAtMillis: Long,
    val pageCount: Int,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val thumbnailPath: String? = null,
    val textPreview: String? = null
)

enum class HistoryRecordKind {
    Document,
    IdCard,
    Ocr
}
