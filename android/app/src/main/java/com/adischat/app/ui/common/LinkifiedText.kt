package com.adischat.app.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val UrlRegex = Regex("""https?://[^\s<>()]+""")

/**
 * Text that turns URLs into tappable links AND forwards single-tap / long-press on the
 * rest of the text to the caller. This is what lets a long press anywhere on a chat
 * bubble select the message, while tapping a link still opens it - instead of the old
 * ClickableText, which swallowed long presses so only the bottom meta line responded.
 */
@Composable
fun LinkifiedText(
    text: String,
    color: Color,
    linkColor: Color,
    linkBackground: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 22.sp,
    fontWeight: FontWeight? = null,
    onOpenUrl: (String) -> Unit,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val annotated = remember(text, color, linkColor, linkBackground) {
        buildAnnotatedString {
        append(text)
        UrlRegex.findAll(text).forEach { match ->
        val url = match.value.trimEnd('.', ',', ')', ']')
        val start = match.range.first
        val end = start + url.length
        addStyle(
        // Clean, professional link styling: a tinted, medium-weight label -
        // no highlight box and no underline (those read as "unprofessional").
        SpanStyle(
        color = linkColor,
        background = linkBackground,
        fontWeight = FontWeight.SemiBold,
        ),
        start,
        end,
        )
        addStringAnnotation(tag = "URL", annotation = url, start = start, end = end)
        }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        modifier = modifier.pointerInput(annotated, onTap, onLongPress) {
        detectTapGestures(
        onLongPress = if (onLongPress != null) { _ -> onLongPress() } else null,
        onTap = { pos ->
        val offset = layout?.getOffsetForPosition(pos)
        val url = offset?.let { off ->
        annotated.getStringAnnotations("URL", off, off).firstOrNull()?.item
        }
        when {
        url != null   -> onOpenUrl(url)   // tapping a link still opens it
        onTap != null -> onTap()      // tapping elsewhere - message actions
        }
        },
        )
        },
        style = TextStyle(
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        ),
        onTextLayout = { layout = it },
    )
}
