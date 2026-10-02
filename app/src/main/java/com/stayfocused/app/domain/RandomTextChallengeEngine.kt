package com.stayfocused.app.domain

data class ChallengeQuote(
    val text: String,
    val author: String
)

sealed interface RandomTextVerificationResult {
    data object Success : RandomTextVerificationResult
    data object PasteDetected : RandomTextVerificationResult
    data class Mismatch(val expectedLength: Int, val actualLength: Int) : RandomTextVerificationResult
}

/**
 * Pure Kotlin engine for Random Text stoic challenges during Strict Mode disarm.
 * Includes anti-paste burst detection to ensure the user physically types the quote,
 * breaking dopamine-driven craving loops through mindful, manual exertion.
 */
class RandomTextChallengeEngine(
    private val quotes: List<ChallengeQuote> = DEFAULT_QUOTES
) {

    companion object {
        const val MIN_TYPING_MS_THRESHOLD = 1_000L // Anything under 1s for > 20 chars is paste

        val DEFAULT_QUOTES = listOf(
            ChallengeQuote("We suffer more often in imagination than in reality.", "Seneca"),
            ChallengeQuote("You have power over your mind - not outside events.", "Marcus Aurelius"),
            ChallengeQuote("Waste no more time arguing what a good man should be. Be one.", "Marcus Aurelius"),
            ChallengeQuote("The impediment to action advances action. What stands in the way becomes the way.", "Marcus Aurelius"),
            ChallengeQuote("No person has the power to have everything they want, but it is in their power not to want what they don't have.", "Seneca"),
            ChallengeQuote("It's not what happens to you, but how you react to it that matters.", "Epictetus"),
            ChallengeQuote("First say to yourself what you would be; and then do what you have to do.", "Epictetus"),
            ChallengeQuote("He who fears death will never do anything worthy of a man who is alive.", "Seneca"),
            ChallengeQuote("If you are distressed by anything external, the pain is not due to the thing itself, but to your estimate of it.", "Marcus Aurelius"),
            ChallengeQuote("Difficulties strengthen the mind, as labor does the body.", "Seneca")
        )
    }

    fun getRandomQuote(): ChallengeQuote {
        return quotes.random()
    }

    /**
     * Verifies the user's input against the target challenge quote.
     * Enforces anti-paste protection when typing duration is suspiciously short.
     */
    fun verifyInput(
        challengeText: String,
        userInput: String,
        typingDurationMs: Long
    ): RandomTextVerificationResult {
        val trimmedTarget = challengeText.trim()
        val trimmedInput = userInput.trim()

        if (trimmedTarget.length > 20 && trimmedInput.length > 20 && typingDurationMs < MIN_TYPING_MS_THRESHOLD) {
            return RandomTextVerificationResult.PasteDetected
        }

        return if (trimmedTarget == trimmedInput) {
            RandomTextVerificationResult.Success
        } else {
            RandomTextVerificationResult.Mismatch(
                expectedLength = trimmedTarget.length,
                actualLength = trimmedInput.length
            )
        }
    }
}
