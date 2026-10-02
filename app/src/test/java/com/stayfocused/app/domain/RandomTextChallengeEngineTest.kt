package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
