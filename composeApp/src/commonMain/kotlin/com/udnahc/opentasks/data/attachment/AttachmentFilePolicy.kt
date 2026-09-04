package com.udnahc.opentasks.data.attachment

object AttachmentFilePolicy {
    const val MAX_SOURCE_BYTES = 32L * 1024L * 1024L
    const val MAX_SOURCE_DIMENSION = 16_384
    const val MAX_SOURCE_PIXELS = 64_000_000L
    const val MAX_LONG_EDGE = 1600
    const val QUALITY = 80
    const val MAX_UPLOAD_BYTES = 5L * 1024L * 1024L
    const val THUMBNAIL_LONG_EDGE = 320

    fun acceptsSourceDimensions(width: Int, height: Int): Boolean =
        width > 0 &&
            height > 0 &&
            width <= MAX_SOURCE_DIMENSION &&
            height <= MAX_SOURCE_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS
}
