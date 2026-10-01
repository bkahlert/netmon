package com.bkahlert.kommons.test

import java.awt.Desktop
import java.nio.file.Path

/** Opens this file with the desktop's default application, if there is a desktop. */
fun Path.open() {
    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(toFile())
}
