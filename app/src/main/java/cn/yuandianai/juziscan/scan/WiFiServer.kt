package cn.yuandianai.juziscan.scan

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WiFiServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    private val transferDir = File(context.filesDir, "wifi_transfer").apply {
        if (!exists()) mkdirs()
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                
                val params = session.parameters
                // Handle file upload
                if (files.containsKey("file")) {
                    val tmpFilePath = files["file"]
                    val originalFileName = params["file"]?.firstOrNull() ?: "uploaded_file_${System.currentTimeMillis()}"
                    if (tmpFilePath != null) {
                        val tmpFile = File(tmpFilePath)
                        val destFile = File(transferDir, originalFileName)
                        tmpFile.copyTo(destFile, overwrite = true)
                    }
                }
                
                return getRedirectResponse()
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${e.message}")
            }
        }

        if (uri.startsWith("/download/")) {
            val fileName = uri.removePrefix("/download/")
            val file = File(transferDir, fileName)
            if (file.exists() && !file.isDirectory) {
                return try {
                    val mime = getMimeTypeForFile(fileName)
                    val res = newChunkedResponse(Response.Status.OK, mime, FileInputStream(file))
                    res.addHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
                    res
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error downloading file.")
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found.")
        }
        
        if (uri.startsWith("/delete/")) {
            val fileName = uri.removePrefix("/delete/")
            val file = File(transferDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            return getRedirectResponse()
        }

        // Return HTML
        val html = buildHtmlPage()
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun getRedirectResponse(): Response {
        val res = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "<html><body>Redirecting...</body></html>")
        res.addHeader("Location", "/")
        return res
    }

    private fun buildHtmlPage(): String {
        val files = transferDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        
        val fileListHtml = StringBuilder()
        for (file in files) {
            val sizeKb = file.length() / 1024
            fileListHtml.append("""
                <tr>
                    <td>${file.name}</td>
                    <td>$sizeKb KB</td>
                    <td>
                        <a href="/download/${file.name}" class="btn btn-primary">下载</a>
                        <a href="/delete/${file.name}" class="btn btn-danger" onclick="return confirm('确定要删除吗？');">删除</a>
                    </td>
                </tr>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JuZiScan WiFi 传图</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f6f7fb; margin: 0; padding: 20px; color: #1e2029; }
                    .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
                    h1 { color: #3b5998; text-align: center; }
                    .upload-box { border: 2px dashed #bac7e6; padding: 40px; text-align: center; border-radius: 8px; margin-bottom: 30px; background-color: #fcfdff; }
                    .upload-box input[type="file"] { margin-bottom: 15px; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
                    th { background-color: #f9f9fa; font-weight: 600; }
                    .btn { display: inline-block; padding: 6px 12px; border-radius: 6px; text-decoration: none; font-size: 14px; margin-right: 5px; }
                    .btn-primary { background-color: #4b68ff; color: white; border: none; cursor: pointer; }
                    .btn-primary:hover { background-color: #3550e0; }
                    .btn-danger { background-color: #ff4b4b; color: white; }
                    .btn-danger:hover { background-color: #e03535; }
                    .empty-state { text-align: center; padding: 30px; color: #888; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>JuZiScan WiFi 传图</h1>
                    
                    <div class="upload-box">
                        <h3>上传文件到手机</h3>
                        <form action="/" method="post" enctype="multipart/form-data">
                            <input type="file" name="file" required>
                            <br>
                            <input type="submit" value="开始上传" class="btn btn-primary" style="padding: 10px 20px; font-size: 16px;">
                        </form>
                    </div>

                    <h3>手机上的文件 (${files.size})</h3>
                    ${if (files.isEmpty()) "<div class='empty-state'>暂无文件</div>" else """
                    <table>
                        <thead>
                            <tr>
                                <th>文件名</th>
                                <th>大小</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            $fileListHtml
                        </tbody>
                    </table>
                    """}
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
