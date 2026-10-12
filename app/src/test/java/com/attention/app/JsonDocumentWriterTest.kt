package com.attention.app

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class JsonDocumentWriterTest {
    @Test
    fun writes_exact_utf8_json_bytes() {
        val output = ByteArrayOutputStream()
        val json = "{\"version\":1,\"title\":\"中文\"}"

        writeUtf8Json(json, output)

        assertArrayEquals(json.toByteArray(StandardCharsets.UTF_8), output.toByteArray())
    }
}
