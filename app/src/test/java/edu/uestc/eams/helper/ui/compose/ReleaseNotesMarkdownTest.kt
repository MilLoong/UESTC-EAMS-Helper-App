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
            ).text
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("Bold"))
        assertTrue(text.contains("link"))
        assertFalse(text.contains("##"))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("[link]"))
    }

    private companion object {
        private val SpanStyleStub =
            androidx.compose.ui.text.SpanStyle(
                fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
            )
    }
}
