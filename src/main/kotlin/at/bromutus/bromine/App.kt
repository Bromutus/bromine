package at.bromutus.bromine

import at.bromutus.bromine.commands.ChatInputCommand
import at.bromutus.bromine.commands.Txt2Img
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.createSDClient
import dev.kord.core.Kord
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun main() {
    val config = loadAppConfig()

    val sdClient = createSDClient(config.sdApiUrl)

    val kord = Kord(config.discordApiToken)

    val commands = listOf(
        Txt2Img(sdClient),
    )

    kord.createCommands(commands)
    kord.registerInteractionHandlers(commands)

    kord.login {
        logger.info("Login successful")
    }
}

private suspend fun Kord.createCommands(commands: List<ChatInputCommand>) {
    val commandsByGuildId = commands.groupBy { it.guildId }

    commandsByGuildId.forEach { (guildId, commands) ->
        if (guildId == null) {
            createGlobalApplicationCommands {
                commands.forEach {
                    input(it.name, it.description) {
                        it.buildCommand(this)
                    }
                }
            }
        } else {
            createGuildApplicationCommands(guildId) {
                commands.forEach {
                    input(it.name, it.description) {
                        it.buildCommand(this)
                    }
                }
            }
        }
    }
}

private fun Kord.registerInteractionHandlers(commands: List<ChatInputCommand>) {
    on<ChatInputCommandInteractionCreateEvent> {
        val commandName = interaction.invokedCommandName
        logger.debug("New interaction: $commandName")
        try {
            commands.find { it.name == commandName }?.handleInteraction(interaction)
                ?: throw Exception("Unable to handle command: $commandName. Maybe this command was deleted?")
        } catch (e: Exception) {
            logger.logInteractionException(e)
            interaction.respondWithException(e)
        }
    }
}

