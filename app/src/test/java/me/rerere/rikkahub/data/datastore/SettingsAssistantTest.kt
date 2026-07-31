package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAssistantTest {
    @Test
    fun `empty assistant settings fall back to the default assistant`() {
        val settings = Settings(assistants = emptyList())

        assertEquals(DEFAULT_ASSISTANT_ID, settings.getCurrentAssistant().id)
    }
}
