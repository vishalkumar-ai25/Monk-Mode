package com.stayfocused.app.ui.overlay

object BlockOverlayQuotes {
    val QUOTES = listOf(
        "Focus is a muscle. Train it every day.",
        "Discipline is choosing between what you want now and what you want most.",
        "The secret of getting ahead is getting started.",
        "Deep work is the superpower of the 21st century.",
        "Your attention is your most precious currency. Spend it wisely.",
        "Starve your distractions, feed your focus.",
        "One reason so few achieve what they want is that they never direct their focus.",
        "Action cures anxiety. Procrastination feeds fear.",
        "Master your impulses or they will master you."
    )

    fun getRandomQuote(): String = QUOTES.random()
}
