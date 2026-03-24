package com.udnahc.opentasks.data.extensions

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun uuid4(): String = Uuid.random().toString()
