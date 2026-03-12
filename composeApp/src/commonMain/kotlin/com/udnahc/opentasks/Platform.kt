package com.udnahc.opentasks

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform