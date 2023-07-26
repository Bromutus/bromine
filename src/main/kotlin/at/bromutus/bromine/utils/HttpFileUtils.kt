package at.bromutus.bromine.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.core.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
suspend fun downloadImageAsBase64(originalImageUrl: String, contentType: String): String {
    val bytes = HttpClient(CIO)
        .get(originalImageUrl)
        .bodyAsChannel()
        .readRemaining()
        .readBytes()
    return "data:$contentType;base64,${Base64.encode(bytes)}"
}