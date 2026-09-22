package com.llamacpp.mobile.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.parseMarkdown
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Markdown typography tuned for chat. The renderer's defaults map headings to
 * very large display styles; these are scaled down to fit a message bubble.
 */
@Composable
private fun llamaMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.headlineSmall,
    h2 = MaterialTheme.typography.titleLarge,
    h3 = MaterialTheme.typography.titleMedium,
    h4 = MaterialTheme.typography.titleSmall,
    h5 = MaterialTheme.typography.bodyLarge,
    h6 = MaterialTheme.typography.bodyMedium,
    paragraph = MaterialTheme.typography.bodyLarge,
    quote = MaterialTheme.typography.bodyMedium,
    code = MaterialTheme.typography.bodySmall,
    inlineCode = MaterialTheme.typography.bodySmall,
)

private val components = markdownComponents(
    codeFence = highlightedCodeFence,
    codeBlock = highlightedCodeBlock,
)

/** Renders complete markdown with syntax-highlighted fenced code blocks. */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberMarkdownState(content = content, retainState = true)
    Markdown(
        state,
        colors = markdownColor(),
        typography = llamaMarkdownTypography(),
        components = components,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Renders markdown while it is still being generated.
 *
 * The document is parsed **synchronously** in composition, so the measured
 * height reflects the current tokens in the same frame. The library's async /
 * streaming parser emits its result a frame (or several) later, and with the
 * list pinned to the bottom that lag shows up as continuous jitter while
 * generating. Parsing inline removes that lag entirely.
 */
@Composable
fun StreamingMarkdownText(
    content: String,
    modifier: Modifier = Modifier,
) {
    val state = remember(content) { parseMarkdown(content) }
    Markdown(
        state,
        colors = markdownColor(),
        typography = llamaMarkdownTypography(),
        components = components,
        modifier = modifier.fillMaxWidth(),
    )
}
