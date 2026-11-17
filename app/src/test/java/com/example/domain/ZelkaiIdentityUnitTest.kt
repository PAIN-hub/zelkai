package com.example.domain

import com.example.data.model.NexusUserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZelkaiIdentityUnitTest {

    @Test
    fun defaultSystemPrompt_identifiesAsZelkai_andPreservesCoreBehavioralGuidelines() {
        val prompt = NexusUserSettings.DEFAULT_SYSTEM_PROMPT
        assertTrue("DEFAULT_SYSTEM_PROMPT must mention Zelkai", prompt.contains("Zelkai"))
        assertFalse("DEFAULT_SYSTEM_PROMPT must not mention Nexus Agent", prompt.contains("Nexus Agent", ignoreCase = true))
        assertFalse("DEFAULT_SYSTEM_PROMPT must not say You are Nexus", prompt.contains("You are Nexus", ignoreCase = true))

        assertTrue(prompt.contains("Markdown"))
        assertTrue(prompt.contains("non-interactive shell command"))
        assertTrue(prompt.contains("[REDACTED]"))
        assertEquals(prompt, NexusUserSettings().agentPersona)
    }

    @Test
    fun toolAndRedactor_inheritsZelkaiPromptFromNexusUserSettings() {
        val systemPrompt = NexusToolAndRedactor.SYSTEM_PROMPT
        val firstPresetPrompt = NexusToolAndRedactor.PERSONA_PRESETS.first().prompt

        assertEquals(NexusUserSettings.DEFAULT_SYSTEM_PROMPT, systemPrompt)
        assertEquals(NexusUserSettings.DEFAULT_SYSTEM_PROMPT, firstPresetPrompt)

        assertTrue(systemPrompt.contains("Zelkai"))
        assertFalse(systemPrompt.contains("Nexus Agent", ignoreCase = true))
        assertFalse(systemPrompt.contains("You are Nexus", ignoreCase = true))

        assertTrue(firstPresetPrompt.contains("Zelkai"))
        assertFalse(firstPresetPrompt.contains("Nexus Agent", ignoreCase = true))
        assertFalse(firstPresetPrompt.contains("You are Nexus", ignoreCase = true))
    }

    @Test
    fun builtInPersonas_allFiveIdentifyAsZelkai_andNeverAsNexus() {
        val personas = NexusPersonalizationStore.BUILT_IN_PERSONAS
        assertEquals(
            listOf("default", "senior_devops", "security_auditor", "concise_coder", "teacher"),
            personas.map { it.id }
        )

        personas.forEach { persona ->
            assertTrue(
                "Persona '${persona.id}' must identify as Zelkai",
                persona.systemPrompt.contains("Zelkai")
            )
            assertFalse(
                "Persona '${persona.id}' must not contain Nexus Agent",
                persona.systemPrompt.contains("Nexus Agent", ignoreCase = true)
            )
            assertFalse(
                "Persona '${persona.id}' must not contain You are Nexus",
                persona.systemPrompt.contains("You are Nexus", ignoreCase = true)
            )
        }
    }

    @Test
    fun migrateLegacyNexusPromptIfUntouched_migratesBlankAndLegacyDefaults_whilePreservingCustomPrompts() {
        assertEquals(
            NexusUserSettings.DEFAULT_SYSTEM_PROMPT,
            NexusUserSettings.migrateLegacyNexusPromptIfUntouched("")
        )
        assertEquals(
            NexusUserSettings.DEFAULT_SYSTEM_PROMPT,
            NexusUserSettings.migrateLegacyNexusPromptIfUntouched("   \n\t  ")
        )

        assertTrue(NexusUserSettings.LEGACY_DEFAULT_SYSTEM_PROMPTS.isNotEmpty())
        NexusUserSettings.LEGACY_DEFAULT_SYSTEM_PROMPTS.forEach { legacy ->
            assertEquals(
                NexusUserSettings.DEFAULT_SYSTEM_PROMPT,
                NexusUserSettings.migrateLegacyNexusPromptIfUntouched(legacy)
            )
            assertEquals(
                NexusUserSettings.DEFAULT_SYSTEM_PROMPT,
                NexusUserSettings.migrateLegacyNexusPromptIfUntouched("  $legacy \n ")
            )
            val collapsedSpacingVariant = legacy.replace(" ", "  \n ")
            assertEquals(
                NexusUserSettings.DEFAULT_SYSTEM_PROMPT,
                NexusUserSettings.migrateLegacyNexusPromptIfUntouched(collapsedSpacingVariant)
            )
        }

        val customPromptMentionsNexus = "You are my custom assistant for the Nexus workspace. Always output diffs."
        assertEquals(
            customPromptMentionsNexus,
            NexusUserSettings.migrateLegacyNexusPromptIfUntouched(customPromptMentionsNexus)
        )

        val customEditedLegacyPrefix = "You are Nexus Agent, customized by the user for Rust tasks."
        assertEquals(
            customEditedLegacyPrefix,
            NexusUserSettings.migrateLegacyNexusPromptIfUntouched(customEditedLegacyPrefix)
        )
    }

    @Test
    fun buildEffectiveSystemPrompt_injectsZelkaiIdentity_andAppendsImmutableSafetyPolicyLast() {
        val store = NexusPersonalizationStore(context = null)

        val defaultPrompt = store.buildEffectiveSystemPrompt(NexusUserSettings.DEFAULT_SYSTEM_PROMPT)
        assertTrue(defaultPrompt.contains("You are Zelkai"))
        assertFalse(defaultPrompt.contains("Nexus Agent", ignoreCase = true))
        assertTrue(defaultPrompt.contains("<nexus:file"))
        assertTrue(defaultPrompt.endsWith(NexusPersonalizationStore.IMMUTABLE_SAFETY_POLICY))

        val migratedLegacyPrompt = store.buildEffectiveSystemPrompt(NexusUserSettings.LEGACY_DEFAULT_SYSTEM_PROMPT)
        assertTrue(migratedLegacyPrompt.contains("You are Zelkai"))
        assertFalse(migratedLegacyPrompt.contains("Nexus Agent", ignoreCase = true))
        assertTrue(migratedLegacyPrompt.endsWith(NexusPersonalizationStore.IMMUTABLE_SAFETY_POLICY))

        val customPrompt = "Custom prompt mentioning Nexus project rules."
        val customEffective = store.buildEffectiveSystemPrompt(customPrompt)
        assertTrue(customEffective.startsWith(customPrompt))
        assertTrue(customEffective.endsWith(NexusPersonalizationStore.IMMUTABLE_SAFETY_POLICY))

        listOf("senior_devops", "security_auditor", "concise_coder", "teacher").forEach { presetId ->
            store.selectPersonaPreset(presetId)
            val effective = store.buildEffectiveSystemPrompt(NexusUserSettings.DEFAULT_SYSTEM_PROMPT)
            assertTrue("Preset '$presetId' must inject Zelkai identity", effective.contains("You are Zelkai"))
            assertFalse("Preset '$presetId' must not contain Nexus Agent", effective.contains("Nexus Agent", ignoreCase = true))
            assertTrue(
                "Preset '$presetId' must end with IMMUTABLE_SAFETY_POLICY",
                effective.endsWith(NexusPersonalizationStore.IMMUTABLE_SAFETY_POLICY)
            )
        }
    }
}
