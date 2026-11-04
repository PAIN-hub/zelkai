package com.example.domain

import com.example.data.model.TranscriptEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTimelineUnitTest {

    @Test
    fun titleSanitizer_stripsShellMetachars_truncatesTo60Chars_andRedactsSecrets() {
        // Metacharacters rejected
        val rejected1 = TitleSanitizer.sanitize("rm -rf /; echo hack")
        assertNull("Shell metacharacters like semicolon should be rejected as title", rejected1)

        val rejected2 = TitleSanitizer.sanitize("cat /etc/passwd | grep root")
        assertNull("Pipes should be rejected as title", rejected2)

        val rejected3 = TitleSanitizer.sanitize("eval \$(whoami)")
        assertNull("Command substitutions should be rejected as title", rejected3)

        // Valid titles sanitized
        val valid = TitleSanitizer.sanitize("  Verify pygame installed  ")
        assertEquals("Verify pygame installed", valid)

        // Truncation at 60 chars
        val longTitle = "A".repeat(100)
        val sanitizedLong = TitleSanitizer.sanitize(longTitle)
        assertNotNull(sanitizedLong)
        assertTrue(sanitizedLong!!.length <= 60)

        // Redaction of secrets in title
        val secretTitle = TitleSanitizer.sanitize("Install AI key AIzaSyA1234567890abcdef1234567890abcde")
        assertNotNull(secretTitle)
        assertFalse(secretTitle!!.contains("AIzaSyA1234567890abcdef1234567890abcde"))
    }

    @Test
    fun activityLabeler_producesDeterministicPlainLanguageTitles() {
        assertEquals("List files", ActivityLabeler.label("ls -la"))
        assertEquals("Print working directory", ActivityLabeler.label("pwd"))
        assertEquals("Install dependencies", ActivityLabeler.label("npm install"))
        assertEquals("Install pygame", ActivityLabeler.label("pip install pygame"))
        assertEquals("Install react", ActivityLabeler.label("npm install react"))
        assertEquals("Run game.py", ActivityLabeler.label("python game.py"))
        assertEquals("Check git status", ActivityLabeler.label("git status"))
        assertEquals("Commit changes", ActivityLabeler.label("git commit -m 'Initial'"))
    }

    @Test
    fun partitionIntoTurns_andBuildTimelineBlocks_groupsThinking_text_andToolGroupsCorrectly() {
        val entries = listOf(
            TranscriptEntry(id = "u1", type = "user", text = "Write a game in Python"),
            TranscriptEntry(
                id = "a1",
                type = "agent",
                text = "I'll write a polished, complete Python game for you.",
                reasoningText = "First check if pygame is installed, then write snake.py",
                thinkingDurationSec = 8L
            ),
            TranscriptEntry(
                id = "c1",
                type = "command",
                command = "python -m pip show pygame",
                title = "Verify pygame installed",
                commandStatus = "approved"
            ),
            TranscriptEntry(
                id = "o1",
                type = "output",
                command = "python -m pip show pygame",
                output = "Name: pygame\nVersion: 2.6.1",
                exitCode = 0L,
                title = "Verify pygame installed"
            )
        )

        val turns = TurnTimelineBuilder.partitionIntoTurns(entries, isAgentRunning = false)
        assertEquals(1, turns.size)
        val turn = turns.first()

        // User bubble
        assertEquals("Write a game in Python", turn.userEntry?.text)

        // Build blocks for the turn
        val blocks = TurnTimelineBuilder.build(turn.assistantEntries)
        val thinkingBlock = blocks.filterIsInstance<TimelineBlock.ThinkingBlock>().firstOrNull()
        val textBlock = blocks.filterIsInstance<TimelineBlock.TextBlock>().firstOrNull()
        val toolGroup = blocks.filterIsInstance<TimelineBlock.ToolGroup>().firstOrNull()

        // Thought process block
        assertNotNull(thinkingBlock)
        assertEquals(8L, thinkingBlock?.durationSec)
        assertTrue(thinkingBlock?.text?.contains("pygame") == true)

        // Assistant introductory text block
        assertNotNull(textBlock)
        assertTrue(textBlock?.markdownText?.contains("polished, complete Python game") == true)

        // Tool group block
        assertNotNull(toolGroup)
        assertEquals(1, toolGroup?.steps?.size)
        val step = toolGroup!!.steps.first()
        assertEquals("Verify pygame installed", step.title)
        assertEquals("python -m pip show pygame", step.command)
        assertEquals(0L, step.exitCode)
        assertEquals(TimelineBlock.ToolStepStatus.SUCCESS, step.status)
    }

    @Test
    fun toolGroup_calculatesHeaderLabelAndFailureCorrectly() {
        val entries = listOf(
            TranscriptEntry(id = "u1", type = "user", text = "Run build"),
            TranscriptEntry(id = "c1", type = "command", command = "gradle test", title = "Run tests"),
            TranscriptEntry(id = "o1", type = "output", command = "gradle test", exitCode = 1L, output = "FAILED"),
            TranscriptEntry(id = "c2", type = "command", command = "ls", title = "List files"),
            TranscriptEntry(id = "o2", type = "output", command = "ls", exitCode = 0L, output = "file.txt")
        )

        val turns = TurnTimelineBuilder.partitionIntoTurns(entries, isAgentRunning = false)
        val blocks = TurnTimelineBuilder.build(turns.first().assistantEntries)
        val group = blocks.filterIsInstance<TimelineBlock.ToolGroup>().firstOrNull()
        assertNotNull(group)
        assertEquals(2, group!!.steps.size)
        assertEquals(1, group.failedCount)
        assertEquals("Ran 2 commands · 1 failed", group.headerLabel())
    }
}
