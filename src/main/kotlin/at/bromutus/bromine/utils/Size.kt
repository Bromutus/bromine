package at.bromutus.bromine.utils

import kotlin.math.sqrt

data class Size(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x${height}"

    val inPixels = width * height
    val aspectRatio = width.toDouble() / height.toDouble()

    operator fun plus(other: Size) = Size(width + other.width, height + other.height)
    operator fun minus(other: Size) = Size(width - other.width, height - other.height)
    operator fun times(other: Int) = Size(width * other, height * other)
    operator fun times(other: Double) =
        Size(width.toDouble().times(other).toInt(), height.toDouble().times(other).toInt())
    operator fun Int.times(other: Size) = other * this
    operator fun Double.times(other: Size) = other * this
    operator fun div(other: Int) = Size(width / other, height / other)
}

fun calculateDesiredImageSize(
    specifiedWidth: Int? = null,
    specifiedHeight: Int? = null,
    originalWidth: Int? = null,
    originalHeight: Int? = null,
    defaultWidth: Int,
    defaultHeight: Int,
): Size {
    return Size(
        specifiedWidth ?: defaultWidth,
        specifiedHeight ?: defaultHeight
    ).let {
        Size(
            if (it.width == 0) originalWidth ?: defaultWidth else it.width,
            if (it.height == 0) originalHeight ?: defaultHeight else it.height,
        )
    }
}
fun Size.constrainToPixelSize(maxPixels: Int): Size {
    return when {
        inPixels == 0 -> Size(1, 1)
        inPixels > maxPixels -> Size(
            sqrt(maxPixels.toDouble() * aspectRatio).toInt(),
            sqrt(maxPixels.toDouble() / aspectRatio).toInt(),
        )
        else -> this
    }
}