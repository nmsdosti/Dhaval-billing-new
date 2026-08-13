package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedItem(
    val file: File,
    val name: String,
    val sizeString: String,
    val dateString: String,
    val uri: Uri
)

object DownloadManagerHelper {

    fun downloadFromUrl(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        onDownloadStarted: (String, File) -> Unit
    ) {
        try {
            if (url.startsWith("data:")) {
                handleDataUriDownload(context, url, mimeType, onDownloadStarted)
                return
            }

            val fileName = getFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (!userAgent.isNullOrEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setMimeType(mimeType ?: "application/pdf")
                setTitle("Downloading $fileName")
                setDescription("RAM Billing Invoice / Document")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            val destinationFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            onDownloadStarted(fileName, destinationFile)
            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleDataUriDownload(
        context: Context,
        dataUrl: String,
        mimeType: String?,
        onDownloadStarted: (String, File) -> Unit
    ) {
        try {
            val parts = dataUrl.split(",")
            if (parts.size < 2) return

            val header = parts[0]
            val base64Data = parts[1]

            val extractedMime = if (header.contains(";")) {
                header.substring(5, header.indexOf(";"))
            } else {
                mimeType ?: "application/pdf"
            }

            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(extractedMime) ?: "pdf"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "RAM_Invoice_$timestamp.$ext"

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)

            val pdfBytes = Base64.decode(base64Data, Base64.DEFAULT)
            FileOutputStream(file).use { fos ->
                fos.write(pdfBytes)
            }

            onDownloadStarted(fileName, file)
            Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        var fileName = ""

        if (!contentDisposition.isNullOrEmpty()) {
            val dispositionParts = contentDisposition.split(";")
            for (part in dispositionParts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("filename=", ignoreCase = true)) {
                    fileName = trimmed.substring(9).replace("\"", "")
                } else if (trimmed.startsWith("filename*=", ignoreCase = true)) {
                    fileName = trimmed.substring(10).replace("\"", "")
                    if (fileName.contains("''")) {
                        fileName = fileName.split("''").last()
                    }
                }
            }
        }

        if (fileName.isEmpty()) {
            fileName = Uri.parse(url).lastPathSegment ?: ""
        }

        if (fileName.isEmpty() || !fileName.contains(".")) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType ?: "application/pdf") ?: "pdf"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            fileName = "RAM_Billing_$timestamp.$ext"
        }

        return fileName
    }

    fun openFile(context: Context, file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val extension = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/pdf"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_LONG).show()
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val extension = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/pdf"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "RAM Billing Invoice / PDF")
                putExtra(Intent.EXTRA_TEXT, "Invoice document from RAM Billing.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Document via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getRecentDownloads(context: Context): List<DownloadedItem> {
        val list = mutableListOf<DownloadedItem>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists() && downloadsDir.isDirectory) {
            val files = downloadsDir.listFiles { file ->
                file.isFile && (file.name.contains("RAM", ignoreCase = true) || file.extension.lowercase() == "pdf")
            } ?: emptyArray()

            files.sortByDescending { it.lastModified() }

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            for (f in files.take(20)) {
                val sizeKb = f.length() / 1024
                val sizeStr = if (sizeKb > 1024) String.format(Locale.getDefault(), "%.1f MB", sizeKb / 1024f) else "$sizeKb KB"
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                list.add(
                    DownloadedItem(
                        file = f,
                        name = f.name,
                        sizeString = sizeStr,
                        dateString = sdf.format(Date(f.lastModified())),
                        uri = uri
                    )
                )
            }
        }
        return list
    }
}
