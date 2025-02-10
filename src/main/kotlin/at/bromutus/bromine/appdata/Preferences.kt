package at.bromutus.bromine.appdata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path

@Serializable
data class UserPreferences(
    val checkpoint: String? = null,
    val steps: Int? = null,
    val cfg: Double? = null,
    val enableADetailer: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val count: Int? = null,
    val promptPrefix: String? = null,
    val negativePromptPrefix: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
fun readUserPreferences(userId: String): UserPreferences {
    val file = userPreferenceFilePath(userId).toFile()
    if (!file.exists()) {
        return UserPreferences()
    }
    try {
        file.inputStream().use {
            return Json.decodeFromStream(it)
        }
    } catch (e: Exception) {
        throw IllegalStateException("Failed to read user preferences for $userId", e)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun writeUserPreferences(userId: String, userPreferences: UserPreferences) {
    val file = userPreferenceFilePath(userId).toFile().also {
        it.parentFile.mkdirs()
        it.createNewFile()
    }
    try {
        file.outputStream().use {
            Json.encodeToStream(userPreferences, it)
        }
    } catch (e: Exception) {
        throw IllegalStateException("Failed to write user preferences for $userId", e)
    }
}

private const val USER_PREFERENCES_DIR = "user-preferences"

private fun userPreferenceFilePath(userId: String) = Path.of(APP_DATA_DIR, USER_PREFERENCES_DIR, "$userId.json")
