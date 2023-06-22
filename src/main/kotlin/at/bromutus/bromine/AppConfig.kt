package at.bromutus.bromine

import io.github.cdimascio.dotenv.Dotenv

data class AppConfig(
    val discordApiToken: String,
    val sdApiUrl: String,
)

fun loadAppConfig(): AppConfig {
    val dotenv = Dotenv.load()
    return AppConfig(
        discordApiToken = dotenv.get("DISCORD_API_TOKEN"),
        sdApiUrl = dotenv.get("SD_API_URL"),
    )
}