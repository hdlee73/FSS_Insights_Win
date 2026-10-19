package android.content

import android.net.Uri
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** 데스크톱 셸 동작(브라우저 열기·파일 열기·클립보드) 모음. */
object Desktop {

    fun browse(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI(url))
            } else {
                openCommand(url)
            }
        }.onFailure { openCommand(url) }
    }

    private fun openCommand(target: String) {
        runCatching { ProcessBuilder("cmd", "/c", "start", "", target).start() }
    }

    fun openFile(file: File) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            } else {
                openCommand(file.absolutePath)
            }
        }.onFailure { openCommand(file.absolutePath) }
    }

    fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }
}

/** 안드로이드 ClipData의 데스크톱 대역. */
class ClipData private constructor(val label: String, val uri: Uri?) {
    companion object {
        @JvmStatic
        fun newRawUri(label: CharSequence?, uri: Uri): ClipData = ClipData(label?.toString().orEmpty(), uri)
    }
}
