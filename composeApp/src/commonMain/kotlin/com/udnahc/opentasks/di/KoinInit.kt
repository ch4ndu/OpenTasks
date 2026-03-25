package com.udnahc.opentasks.di

import com.udnahc.opentasks.util.isDebugBuild
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.lighthousegames.logging.KmLogging
import org.lighthousegames.logging.LogLevel
import org.lighthousegames.logging.logging

fun initKoin(config: KoinAppDeclaration = {}) {
//    if (!isDebugBuild()) {
//        KmLogging.setLogLevel(LogLevel.Warn)
//    }
    val log = logging("KoinInit")
    log.w { "Initializing Koin (debug=${isDebugBuild()})" }
    startKoin {
        config()
        modules(sharedModule, platformModule)
    }
}
