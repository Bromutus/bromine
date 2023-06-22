package at.bromutus.bromine

import dev.kord.core.Kord
import io.github.cdimascio.dotenv.Dotenv

suspend fun main() {
    val dotenv = Dotenv.load()
    val kord = Kord(dotenv.get("DISCORD_API_TOKEN"))

    kord.login {
        println("Login successful")
    }
}
