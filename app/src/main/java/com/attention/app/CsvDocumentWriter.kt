package com.attention.app

import java.io.OutputStream

/** Writes the analysis CSV bytes without changing any application state. */
internal fun writeUtf8Csv(csv: String, output: OutputStream) {
    output.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
}
