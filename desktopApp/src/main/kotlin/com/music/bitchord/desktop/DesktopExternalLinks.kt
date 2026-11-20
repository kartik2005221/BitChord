package com.music.bitchord.desktop

import java.awt.Desktop
import java.net.URI

object DesktopExternalLinks {
    /** Puts [text] on the system clipboard. */
    fun copy(text: String): Result<Unit> = runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }

    fun open(uri: String): Result<Unit> = runCatching {
        check(Desktop.isDesktopSupported()) { "This desktop environment cannot open links" }
        Desktop.getDesktop().browse(URI(uri))
    }

    fun openFolder(path: java.nio.file.Path): Result<Unit> = runCatching {
        java.nio.file.Files.createDirectories(path)
        check(Desktop.isDesktopSupported()) { "This desktop environment cannot open folders" }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.OPEN)) { "Opening folders is not supported" }
        desktop.open(path.toFile())
    }
}
