package com.attunetv.launcher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompanionProtocolTest {
    @Test fun contextKeepsConcurrencyRevisionAndBoundsViewerInput() {
        val c = CompanionProtocol.context("  Severance  ", "x".repeat(200), "y".repeat(200), "Apple TV", 7)
        assertEquals("Severance", c.getString("title")); assertEquals(7, c.getInt("revision"))
        assertEquals(120, c.getString("episode").length); assertEquals(160, c.getString("progress").length)
    }
    @Test(expected = IllegalArgumentException::class) fun blankTitleCannotReplaceViewingContext() {
        CompanionProtocol.context("  ", "", "", "", 1)
    }
    @Test fun transcriptKeepsSpeakerErrorAndPendingState() {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("text", "Who is this?").put("status", "complete"))
            .put(JSONObject().put("role", "assistant").put("status", "pending"))
        assertEquals("You\nWho is this?\n\nAttuneTV\nThinking…", CompanionProtocol.transcript(JSONObject().put("messages", messages)))
    }
}
