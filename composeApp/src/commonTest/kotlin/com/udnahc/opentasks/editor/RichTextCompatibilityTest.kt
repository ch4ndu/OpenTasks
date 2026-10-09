package com.udnahc.opentasks.editor

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Protects the HTML persistence contract shared by notes and task descriptions. */
class RichTextCompatibilityTest {
    @Test
    fun savedHtmlRetainsTextAndFormattingAfterReopening() {
        val state = RichTextState().setHtml(
            "<p><b>bold</b> <i>italic</i> <u>underline</u> <s>strike</s> " +
                "<code>code</code> &amp; &lt;tag&gt; café 🎉</p>" +
                "<ul><li>bullet</li></ul><ol><li>numbered</li></ol>",
        )
        val reopened = RichTextState().setHtml(state.toHtml())

        assertEquals(state.annotatedString.text, reopened.annotatedString.text)
        assertTrue(reopened.annotatedString.text.contains("& <tag> café 🎉"))
        reopened.select("bold")
        assertEquals(FontWeight.Bold, reopened.currentSpanStyle.fontWeight)
        reopened.select("italic")
        assertEquals(FontStyle.Italic, reopened.currentSpanStyle.fontStyle)
        reopened.select("underline")
        assertTrue(reopened.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true)
        reopened.select("strike")
        assertTrue(reopened.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true)
        reopened.select("code")
        assertTrue(reopened.isCodeSpan)
        reopened.select("bullet")
        assertTrue(reopened.isUnorderedList)
        reopened.select("numbered")
        assertTrue(reopened.isOrderedList)
    }

    @Test
    fun toolbarFormattingSurvivesSaveAndReopen() {
        val styles = listOf(
            SpanStyle(fontWeight = FontWeight.Bold),
            SpanStyle(fontStyle = FontStyle.Italic),
            SpanStyle(textDecoration = TextDecoration.Underline),
            SpanStyle(textDecoration = TextDecoration.LineThrough),
        )
        for (style in styles) {
            val state = RichTextState().setHtml("<p>draft</p>")
            state.select("draft")
            state.toggleSpanStyle(style)

            val reopened = RichTextState().setHtml(state.toHtml())
            reopened.select("draft")
            assertEquals(state.currentSpanStyle, reopened.currentSpanStyle)
            reopened.toggleSpanStyle(style)
            assertEquals("draft", RichTextState().setHtml(reopened.toHtml()).annotatedString.text)
        }
    }

    @Test
    fun toolbarListAndCodeTogglesSurviveSaveAndReopen() {
        val unordered = RichTextState().setHtml("<p>bullet</p>")
        unordered.select("bullet")
        unordered.toggleUnorderedList()
        val reopenedBullet = RichTextState().setHtml(unordered.toHtml())
        reopenedBullet.select("bullet")
        assertTrue(reopenedBullet.isUnorderedList)

        val ordered = RichTextState().setHtml("<p>numbered</p>")
        ordered.select("numbered")
        ordered.toggleOrderedList()
        val reopenedNumber = RichTextState().setHtml(ordered.toHtml())
        reopenedNumber.select("numbered")
        assertTrue(reopenedNumber.isOrderedList)

        val code = RichTextState().setHtml("<p>code</p>")
        code.select("code")
        code.toggleCodeSpan()
        val reopenedCode = RichTextState().setHtml(code.toHtml())
        reopenedCode.select("code")
        assertTrue(reopenedCode.isCodeSpan)
    }

    @Test
    fun markdownParserRemainsCompatibleWithRichEditor() {
        val state = RichTextState().setMarkdown(
            "# Heading\n\n**bold** and *italic* with `code`\n\n- bullet\n\n1. numbered",
        )
        val reopened = RichTextState().setMarkdown(state.toMarkdown())
        assertEquals(state.annotatedString.text, reopened.annotatedString.text)
        reopened.select("bold")
        assertEquals(FontWeight.Bold, reopened.currentSpanStyle.fontWeight)
        reopened.select("italic")
        assertEquals(FontStyle.Italic, reopened.currentSpanStyle.fontStyle)
        reopened.select("code")
        assertTrue(reopened.isCodeSpan)
        reopened.select("bullet")
        assertTrue(reopened.isUnorderedList)
        reopened.select("numbered")
        assertTrue(reopened.isOrderedList)
    }

    private fun RichTextState.select(text: String) {
        val start = annotatedString.text.indexOf(text)
        assertTrue(start >= 0, "Missing text after conversion: $text")
        selection = TextRange(start, start + text.length)
    }
}
