package com.llamacpp.mobile

import com.llamacpp.mobile.data.repo.buildSystemPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * With tools enabled the model must be told the current date/time so it can
 * resolve relative wording ("latest", "last week") after a web search.
 */
class SystemPromptTest {

    @Test
    fun includesCurrentDateWhenToolsEnabled() {
        val prompt = buildSystemPrompt("You are a helpful assistant.", toolsEnabled = true)
        assertTrue(prompt.startsWith("Current date and time:"))
        assertTrue(prompt.contains("You are a helpful assistant."))
        assertTrue(prompt.contains("interpret relative dates"))
    }

    @Test
    fun leavesSystemPromptUnchangedWhenToolsDisabled() {
        assertEquals(
            "You are a helpful assistant.",
            buildSystemPrompt("You are a helpful assistant.", toolsEnabled = false),
        )
    }

    @Test
    fun handlesBlankSystemPrompt() {
        val prompt = buildSystemPrompt("", toolsEnabled = false)
        assertEquals("", prompt)
    }
}
