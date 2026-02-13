package com.metrolist.music.ui.utils

import kotlin.math.roundToInt

private val GOOGLE_IMAGE_SIZE_REGEX = Regex("""=w(\d+)-h(\d+)(-[^?]*)?""")
private val YT3_IMAGE_SIZE_REGEX = Regex("""=s(\d+)(-[^?]*)?""")
private val YTIMG_THUMB_REGEX =
    Regex("""(https?://i\.ytimg\.com/(?:vi|vi_webp)/[^/]+/)(default|mqdefault|hqdefault|sddefault|maxresdefault)(\.(?:jpg|webp))(\?.*)?""")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    GOOGLE_IMAGE_SIZE_REGEX.find(this)?.let { match ->
        val sourceWidth = match.groupValues[1].toInt()
        val sourceHeight = match.groupValues[2].toInt()
        val sourceSuffix = match.groups[3]?.value.orEmpty()

        var targetWidth = width
        var targetHeight = height
        if (targetWidth != null && targetHeight == null) {
            targetHeight =
                (targetWidth.toFloat() * sourceHeight.toFloat() / sourceWidth.toFloat())
                    .roundToInt()
                    .coerceAtLeast(1)
        } else if (targetWidth == null && targetHeight != null) {
            targetWidth =
                (targetHeight.toFloat() * sourceWidth.toFloat() / sourceHeight.toFloat())
                    .roundToInt()
                    .coerceAtLeast(1)
        }

        return buildString(length + 16) {
            append(this@resize.substring(0, match.range.first))
            append("=w")
            append(targetWidth ?: sourceWidth)
            append("-h")
            append(targetHeight ?: sourceHeight)
            append(sourceSuffix)
            append(this@resize.substring(match.range.last + 1))
        }
    }

    if (contains("yt3.ggpht.com") || contains("yt3.googleusercontent.com")) {
        YT3_IMAGE_SIZE_REGEX.find(this)?.let { match ->
            val sourceSuffix = match.groups[2]?.value.orEmpty()
            val targetSize = (width ?: height)?.coerceAtLeast(1) ?: return this
            return buildString(length + 8) {
                append(this@resize.substring(0, match.range.first))
                append("=s")
                append(targetSize)
                append(sourceSuffix)
                append(this@resize.substring(match.range.last + 1))
            }
        }
    }

    YTIMG_THUMB_REGEX.matchEntire(this)?.let { match ->
        val targetWidth = (width ?: height)?.coerceAtLeast(1) ?: return this
        val quality = when {
            targetWidth <= 120 -> "default"
            targetWidth <= 320 -> "mqdefault"
            else -> "hqdefault"
        }
        return buildString(length + 8) {
            append(match.groupValues[1])
            append(quality)
            append(match.groupValues[3])
            append(match.groups[4]?.value.orEmpty())
        }
    }

    return this
}
