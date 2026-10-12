package com.attention.app

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CsvDocumentWriterTest {
    @Test
    fun writes_exact_utf8_csv_bytes() {
        val output = ByteArrayOutputStream()
        val csv = "\uFEFFplanning_date,note\n2026-10-08,\"中文\"\n"

        writeUtf8Csv(csv, output)

        assertArrayEquals(csv.toByteArray(StandardCharsets.UTF_8), output.toByteArray())
    }
}
