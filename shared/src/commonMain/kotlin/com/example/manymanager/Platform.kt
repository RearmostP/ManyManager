package com.example.manymanager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform