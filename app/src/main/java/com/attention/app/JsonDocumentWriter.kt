package com.attention.app

import java.io.OutputStream

/** Writes a complete backup document without changing application state. */
internal fun writeUtf8Json(json: String, output: OutputStream) {
    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
}
