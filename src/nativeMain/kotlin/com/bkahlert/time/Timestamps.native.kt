package com.bkahlert.time

import kotlinx.cinterop.convert
import platform.posix.time

actual fun timestamp(): Long = time(null).convert()
