package io.github.hdlee73.financenewsradar.platform

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * 안드로이드 알림의 데스크톱 대역. 작업 표시줄 트레이의 풍선 알림으로 보여 준다.
 * 트레이를 못 쓰는 환경에서는 콘솔에 남긴다.
 */
object Notifications {
    @Volatile
    private var trayIcon: TrayIcon? = null

    @Synchronized
    fun init() {
        if (trayIcon != null) return
        runCatching {
            if (!SystemTray.isSupported()) return
            val tray = SystemTray.getSystemTray()
            val icon = TrayIcon(appImage(), "FSS Insights")
            icon.isImageAutoSize = true
            tray.add(icon)
            trayIcon = icon
        }.onFailure { println("[FSS Insights] 트레이 알림을 준비하지 못했습니다: ${it.message}") }
    }

    fun notify(title: String, body: String) {
        val icon = trayIcon
        if (icon != null) {
            runCatching { icon.displayMessage(title, body, TrayIcon.MessageType.INFO) }
                .onFailure { println("[알림] $title - $body") }
        } else {
            println("[알림] $title - $body")
        }
    }

    fun notifyLines(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        notify(title, lines.joinToString("\n"))
    }

    private fun appImage(): BufferedImage {
        val size = 64
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x2A7DE1)
        g.fillRoundRect(0, 0, size, size, 18, 18)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 38)
        val text = "F"
        val metrics = g.fontMetrics
        val x = (size - metrics.stringWidth(text)) / 2
        val y = (size - metrics.height) / 2 + metrics.ascent
        g.drawString(text, x, y)
        g.dispose()
        return image
    }
}
