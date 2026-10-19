package androidx.core.content

import android.content.Context
import android.net.Uri
import java.io.File

/** 안드로이드 FileProvider의 데스크톱 대역(file:// URI를 그대로 돌려준다). */
object FileProvider {
    @JvmStatic
    fun getUriForFile(context: Context, authority: String, file: File): Uri = Uri.fromFile(file)
}
