package io.github.hdlee73.financenewsradar.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/** 데스크톱 파일 선택 창(안드로이드 저장/열기 대화상자 대역). */
object FileChooser {

    /** 저장할 경로를 고른다. 취소하면 null. */
    fun save(title: String, defaultName: String, extension: String? = null): File? {
        return onEdt {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            chooser.selectedFile = File(defaultName)
            if (!extension.isNullOrBlank()) chooser.fileFilter = FileNameExtensionFilter("$extension 파일", extension)
            when (chooser.showSaveDialog(null)) {
                JFileChooser.APPROVE_OPTION -> ensureExtension(chooser.selectedFile, extension)
                else -> null
            }
        }
    }

    /** 열 파일을 고른다. 취소하면 null. */
    fun open(title: String, extension: String? = null): File? {
        return onEdt {
            val chooser = JFileChooser()
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (!extension.isNullOrBlank()) chooser.fileFilter = FileNameExtensionFilter("$extension 파일", extension)
            when (chooser.showOpenDialog(null)) {
                JFileChooser.APPROVE_OPTION -> chooser.selectedFile
                else -> null
            }
        }
    }

    /** 폴더를 고른다. 취소하면 null. */
    fun directory(title: String): File? = onEdt {
        val chooser = JFileChooser()
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        when (chooser.showSaveDialog(null)) {
            JFileChooser.APPROVE_OPTION -> chooser.selectedFile
            else -> null
        }
    }

    /** 파일 이름을 고르는 AWT 대화상자(프레임 없이 쓸 때). */
    fun saveWithDialog(title: String, defaultName: String): File? = onEdt {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file) else null
    }

    private fun ensureExtension(file: File, extension: String?): File {
        if (extension.isNullOrBlank()) return file
        return if (file.name.lowercase().endsWith(".${extension.lowercase()}")) file
        else File(file.parentFile, "${file.name}.$extension")
    }

    private fun <T> onEdt(block: () -> T): T? {
        if (SwingUtilities.isEventDispatchThread()) return runCatching(block).getOrNull()
        var result: T? = null
        runCatching { SwingUtilities.invokeAndWait { result = block() } }
        return result
    }
}
