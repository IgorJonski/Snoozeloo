package com.igoyon.snoozeloo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform