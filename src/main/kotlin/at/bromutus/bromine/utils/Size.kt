package at.bromutus.bromine.utils

import kotlin.math.sqrt

data class Size(val width: UInt, val height: UInt) {
    override fun toString(): String = "${width}x${height}"

    val inPixels = width * height
    val aspectRatio = width.toDouble() / height.toDouble()

    operator fun plus(other: Size) = Size(width + other.width, height + other.height)
    operator fun minus(other: Size) = Size(width - other.width, height - other.height)
    operator fun times(other: UInt) = Size(width * other, height * other)
    operator fun times(other: Double) =
        Size(width.toDouble().times(other).toUInt(), height.toDouble().times(other).toUInt())
    operator fun UInt.times(other: Size) = other * this
    operator fun Double.times(other: Size) = other * this
    operator fun div(other: UInt) = Size(width / other, height / other)
}

fun calculateDesiredImageSize(
    specifiedWidth: UInt? = null,
    specifiedHeight: UInt? = null,
    originalWidth: UInt? = null,
    originalHeight: UInt? = null,
    defaultWidth: UInt,
    defaultHeight: UInt,
): Size {
    return Size(
        specifiedWidth ?: defaultWidth,
        specifiedHeight ?: defaultHeight
    ).let {
        Size(
            if (it.width == 0u) originalWidth ?: defaultWidth else it.width,
            if (it.height == 0u) originalHeight ?: defaultHeight else it.height,
        )
    }
}
fun Size.constrainToPixelSize(maxPixels: UInt): Size {
    return when {
        inPixels == 0u -> Size(1u, 1u)
        inPixels > maxPixels -> Size(
            sqrt(maxPixels.toDouble() * aspectRatio).toUInt(),
            sqrt(maxPixels.toDouble() / aspectRatio).toUInt(),
        )
        else -> this
    }
}