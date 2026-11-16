package com.wongpinter.goatreader.data.epub

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.util.xml.XmlParser

@OptIn(InternalReadiumApi::class)
class ReadiumXmlConformanceTest {
    @Test
    fun `Readium XML parser preserves namespace identities`() {
        val xml = """
            <?xml version="1.0"?>
            <opf:package xmlns:opf="http://www.idpf.org/2007/opf"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Namespaced title</dc:title>
            </opf:package>
        """.trimIndent()

        val root = XmlParser().parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("http://www.idpf.org/2007/opf", root.namespace)
        assertEquals("http://purl.org/dc/elements/1.1/", root.getFirst("title", "http://purl.org/dc/elements/1.1/")?.namespace)
        assertEquals("Namespaced title", root.collectText().trim())
    }

    @Test
    fun `Readium XML parser does not read an external DTD subset`() {
        val secret = File.createTempFile("readium-external-dtd", ".dtd")
        try {
            secret.writeText("<!ENTITY secret 'EXTERNAL_DTD_CONTENT_MUST_STAY_UNREAD'>")
            val xml = """
                <!DOCTYPE package SYSTEM "${secret.toURI().toASCIIString()}">
                <package><title>&secret;</title></package>
            """.trimIndent()

            val parsed = runCatching {
                XmlParser().parse(ByteArrayInputStream(xml.toByteArray())).collectText()
            }.getOrDefault("")

            assertFalse(parsed.contains("EXTERNAL_DTD_CONTENT_MUST_STAY_UNREAD"))
        } finally {
            secret.delete()
        }
    }
}
