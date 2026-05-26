package edu.uestc.eams.helper.ui.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownTest {

    @Test
    fun build_stripsMarkdownMarkersFromPlainSegments() {
        val text =
            buildReleaseNotesAnnotatedString(
                markdown = "## Title\n- **Bold** and [link](https://example.com)",
                headerStyle = SpanStyleStub,
                subHeaderStyle = SpanStyleStub,
                bodyStyle = SpanStyleStub,
                linkStyle = SpanStyleStub,
                codeStyle = SpanStyleStub,
            ).text
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("Bold"))
        assertTrue(text.contains("link"))
        assertFalse(text.contains("##"))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("[link]"))
    }

    @Test
    fun build_renders_inline_code_without_backticks() {
        val text =
            buildReleaseNotesAnnotatedString(
                markdown = "- 支持字段 `year` 与 `vaildWeeks`",
                headerStyle = SpanStyleStub,
                subHeaderStyle = SpanStyleStub,
                bodyStyle = SpanStyleStub,
                linkStyle = SpanStyleStub,
                codeStyle = SpanStyleStub,
            ).text
        assertTrue(text.contains("year"))
        assertTrue(text.contains("vaildWeeks"))
        assertFalse(text.contains("`"))
    }

    private companion object {
        private val SpanStyleStub =
            androidx.compose.ui.text.SpanStyle(
                fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
            )
    }
}
