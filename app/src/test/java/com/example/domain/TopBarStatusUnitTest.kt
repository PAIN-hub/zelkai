package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class TopBarStatusUnitTest {

    @Test
    fun agentStatus_providesCleanStatusLabels() {
        assertEquals("Ready", AgentStatus.Idle.label)
        assertEquals("Thinking", AgentStatus.Working(AgentPhase.THINKING).label)
        assertEquals("Writing", AgentStatus.Working(AgentPhase.WRITING).label)
        assertEquals("Running command", AgentStatus.Working(AgentPhase.RUNNING_COMMAND).label)
        assertEquals("Waiting for approval", AgentStatus.Working(AgentPhase.WAITING_FOR_APPROVAL).label)
        assertEquals("Waiting for job", AgentStatus.Working(AgentPhase.WAITING_FOR_JOB).label)
        assertEquals("Loading model", AgentStatus.Working(AgentPhase.LOADING_MODEL).label)
        assertEquals("Error", AgentStatus.Error("Failed").label)
    }

    @Test
    fun stringsXml_doesNotContainObsoleteFreePlanOrGuestSignIn() {
        val resDir = File("src/main/res/values")
        val stringsFile = File(resDir, "strings.xml")
        if (stringsFile.exists()) {
            val content = stringsFile.readText()
            assertFalse("strings.xml must not contain 'Free plan'", content.contains("Free plan", ignoreCase = true))
            assertFalse("strings.xml must not contain 'Guest · Sign in'", content.contains("Guest · Sign in", ignoreCase = true))
            org.junit.Assert.assertTrue("strings.xml must define app_name as ZELKAI", content.contains(">ZELKAI<") || content.contains(">Zelkai<"))
        }
    }
}

