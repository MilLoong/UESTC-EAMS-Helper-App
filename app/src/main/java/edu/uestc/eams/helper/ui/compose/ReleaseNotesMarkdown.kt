package edu.uestc.eams.helper.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** 将 Release 说明 Markdown 转为富文本。 */
@Composable
fun ReleaseNotesMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val annotated =
        remember(markdown, typography, colors) {
            buildReleaseNotesAnnotatedString(
                markdown = markdown,
                headerStyle = SpanStyle(
                    fontSize = typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                subHeaderStyle = SpanStyle(
                    fontSize = typography.titleSmall.fontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                bodyStyle = SpanStyle(fontSize = typography.bodySmall.fontSize),
                linkStyle =
                    SpanStyle(
                        color = colors.primary,
                        fontSize = typography.bodySmall.fontSize,
                    ),
                codeStyle =
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = typography.bodySmall.fontSize,
                        background = colors.surfaceContainerHighest,
                    ),
            )
        }
    Text(text = annotated, modifier = modifier)
}

internal fun buildReleaseNotesAnnotatedString(
    markdown: String,
    headerStyle: SpanStyle,
    subHeaderStyle: SpanStyle,
    bodyStyle: SpanStyle,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        val lines = markdown.lines()
        var firstLine = true
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                if (!firstLine) append('\n')
                continue
            }
            if (!firstLine) append('\n')
            firstLine = false
            when {
                line.startsWith("### ") ->
                    withStyle(subHeaderStyle) {
                        appendInlineMarkdown(line.removePrefix("### "), bodyStyle, linkStyle, codeStyle)
                    }
                line.startsWith("## ") ->
                    withStyle(headerStyle) {
                        appendInlineMarkdown(line.removePrefix("## "), bodyStyle, linkStyle, codeStyle)
                    }
                line.startsWith("- ") ->
                    withStyle(bodyStyle) {
                        append("• ")
                        appendInlineMarkdown(line.removePrefix("- "), bodyStyle, linkStyle, codeStyle)
                    }
                else ->
                    withStyle(bodyStyle) {
                        appendInlineMarkdown(line, bodyStyle, linkStyle, codeStyle)
                    }
            }
        }
    }

private val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
private val boldRegex = Regex("""\*\*([^*]+)\*\*""")
private val codeRegex = Regex("""`([^`]+)`""")

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    bodyStyle: SpanStyle,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle,
) {
    var pos = 0
    while (pos < text.length) {
        val link = linkRegex.find(text, pos)
        val bold = boldRegex.find(text, pos)
        val code = codeRegex.find(text, pos)
        val next =
            listOfNotNull(link, bold, code).minByOrNull { it.range.first }
                ?: run {
                    append(text.substring(pos))
                    break
                }
        if (next.range.first > pos) {
            append(text.substring(pos, next.range.first))
        }
        when {
            next == link ->
                withLink(
                    LinkAnnotation.Url(
                        next.groupValues[2],
                        TextLinkStyles(style = linkStyle),
                    ),
                ) {
                    append(next.groupValues[1])
                }
            next == bold ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = bodyStyle.fontSize)) {
                    append(next.groupValues[1])
                }
            else ->
                withStyle(codeStyle) {
                    append(next.groupValues[1])
                }
        }
        pos = next.range.last + 1
    }
}
