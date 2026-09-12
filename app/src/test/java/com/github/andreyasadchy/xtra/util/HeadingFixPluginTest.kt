package com.github.andreyasadchy.xtra.util

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingFixPluginTest {
    private val parser = Parser.builder().also { HeadingFixPlugin().configureParser(it) }.build()
    private fun html(markdown: String) = HtmlRenderer.builder().build().render(parser.parse(markdown))

    @Test fun `compact headings preserve every character and level`() {
        for (level in 1..6) {
            val heading = parser.parse("#".repeat(level) + "Hello!").firstChild as Heading
            assertEquals(level, heading.level)
            assertEquals("Hello!", (heading.firstChild as Text).literal)
        }
    }

    @Test fun `single character and final Unicode survive`() {
        for (text in listOf("A", "é", "Hello 😀", "漢字")) {
            assertEquals("<h1>$text</h1>\n", html("#$text"))
        }
    }

    @Test fun `standard spaced tab empty and setext headings stay supported`() {
        assertEquals("<h2>Title</h2>\n", html("## Title"))
        assertEquals("<h2>Title</h2>\n", html("##\tTitle"))
        assertEquals("<h1></h1>\n", html("#"))
        assertEquals("<h1>Title</h1>\n", html("Title\n====="))
        assertEquals("<h2>Title</h2>\n", html("Title\n-----"))
    }

    @Test fun `headings in quotes and lists preserve their containers`() {
        assertEquals("<blockquote>\n<h2>Quote</h2>\n</blockquote>\n", html("> ##Quote"))
        assertEquals("<ul>\n<li>\n<h1>Item</h1>\n</li>\n</ul>\n", html("- #Item"))
    }

    @Test fun `fenced indented and inline code are not rewritten`() {
        val fenced = parser.parse("```\n#code\n```").firstChild as FencedCodeBlock
        assertEquals("#code\n", fenced.literal)
        val indented = parser.parse("    #code").firstChild as IndentedCodeBlock
        assertEquals("#code\n", indented.literal)
        assertEquals("<p><code>#code</code></p>\n", html("`#code`"))
    }

    @Test fun `inline heading links retain their full destination`() {
        val heading = parser.parse("#[Link](https://example.com/path)").firstChild as Heading
        val link = heading.firstChild as Link
        assertEquals("https://example.com/path", link.destination)
        assertEquals("Link", (link.firstChild as Text).literal)
    }

    @Test fun `closing markers escaped hashes and seven hashes retain CommonMark behavior`() {
        assertEquals("<h2>Title</h2>\n", html("##Title ##"))
        assertTrue(parser.parse("#######text").firstChild is Paragraph)
        assertEquals("<p>#literal</p>\n", html("\\#literal"))
        assertEquals("<p>Text #tag</p>\n", html("Text #tag"))
    }
}
