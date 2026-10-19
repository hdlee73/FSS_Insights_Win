package android.content

import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** 안드로이드 ContentResolver의 데스크톱 대역(file:// URI만 처리한다). */
class ContentResolver {
    fun openOutputStream(uri: Uri): OutputStream? {
        val file = uri.file ?: return null
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    fun openInputStream(uri: Uri) = uri.file?.takeIf { it.exists() }?.inputStream()

    fun openFileDescriptor(uri: Uri, mode: String): File? = uri.file
}
