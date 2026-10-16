package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomTextChallengeEngineTest {

    private val engine = RandomTextChallengeEngine()

    @Test
    fun getRandomQuote_returnsNonEmptyQuoteAndAuthor() {
        val quote = engine.getRandomQuote()
        assertNotNull(quote)
        assertTrue(quote.text.isNotBlank())
        assertTrue(quote.author.isNotBlank())
    }

    @Test
    fun verifyInput_matchesExactText() {
        val quote = "We suffer more often in imagination than in reality."
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = "We suffer more often in imagination than in reality.",
            typingDurationMs = 5000L
        )
        assertTrue(result is RandomTextVerificationResult.Success)
    }

    @Test
    fun verifyInput_ignoresLeadingAndTrailingWhitespace() {
        val quote = "You have power over your mind - not outside events."
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = "   You have power over your mind - not outside events.  \n",
            typingDurationMs = 4000L
        )
        assertTrue(result is RandomTextVerificationResult.Success)
    }

    @Test
    fun verifyInput_rejectsMismatchedTypo() {
        val quote = "Waste no more time arguing what a good man should be. Be one."
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = "Waste no more time arguing what a good man should be. Be on.", // typo at the end
            typingDurationMs = 5000L
        )
        assertTrue(result is RandomTextVerificationResult.Mismatch)
    }

    @Test
    fun verifyInput_antiPasteBurstDetection_rejectsInstantTyping() {
        val quote = "The impediment to action advances action. What stands in the way becomes the way."
        // Typing 80 chars in 300ms is impossible for a human -> clipboard paste!
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = quote,
            typingDurationMs = 300L
        )
        assertTrue(result is RandomTextVerificationResult.PasteDetected)
    }

    @Test
    fun verifyInput_rejectsActiveClipboardMatching() {
        val quote = "Difficulties strengthen the mind, as labor does the body."
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = quote,
            typingDurationMs = 6000L,
            keystrokeCount = quote.length,
            clipboardText = "Difficulties strengthen the mind, as labor does the body."
        )
        assertTrue(result is RandomTextVerificationResult.PasteDetected)
    }

    @Test
    fun verifyInput_rejectsLowKeystrokeCountWhenPastedInChunks() {
        val quote = "Difficulties strengthen the mind, as labor does the body."
        // User pasted in 2 chunks -> only 2 keystrokes for 57 characters
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = quote,
            typingDurationMs = 12000L,
            keystrokeCount = 2
        )
        assertTrue(result is RandomTextVerificationResult.PasteDetected)
    }

    @Test
    fun verifyInput_rejectsPhysiologicallyImpossibleTypingSpeed() {
        val quote = "Difficulties strengthen the mind, as labor does the body."
        // 57 chars in 1500ms = 38 chars/second > 15 chars/second limit
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = quote,
            typingDurationMs = 1500L,
            keystrokeCount = quote.length
        )
        assertTrue(result is RandomTextVerificationResult.PasteDetected)
    }

    @Test
    fun verifyInput_acceptsRealisticManualTypingWithTelemetry() {
        val quote = "Difficulties strengthen the mind, as labor does the body."
        // 57 chars typed in 8000ms (~7 chars/sec), 55 keystrokes (some backspaces/edits), empty clipboard
        val result = engine.verifyInput(
            challengeText = quote,
            userInput = quote,
            typingDurationMs = 8000L,
            keystrokeCount = 55,
            clipboardText = ""
        )
        assertTrue(result is RandomTextVerificationResult.Success)
    }
}
