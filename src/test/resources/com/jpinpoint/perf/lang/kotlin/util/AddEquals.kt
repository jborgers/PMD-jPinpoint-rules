package com.jpinpoint.perf.lang.kotlin.util

class AddEquals {
    fun welcomeMessage(): String {
        val message = "Hello, World!"
        massage += " Welcome to Kotlin."
        message += " Let's add more text."
        return message
    }
}