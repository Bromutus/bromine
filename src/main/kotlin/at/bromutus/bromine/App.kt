package at.bromutus.bromine

import at.bromutus.bromine.commands.*
import at.bromutus.bromine.errors.CommandException
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.createSDClient
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun main() {
    val config = loadAppConfig()

    val sdClient = createSDClient(config.sd.apiUrl)

    val kord = Kord(config.discord.token)

    val commands = listOf(
        Txt2ImgCommand(sdClient, config.commands),
        Img2ImgCommand(sdClient, config.commands),
        LoraCommand(config.lora, nsfw = false),
        LoraCommand(config.lora, nsfw = true),
    )

    kord.createCommands(commands)
    kord.registerInteractionHandlers(commands)
    kord.registerAutoCompleteHandlers(commands.filterIsInstance<AutoCompleteCommand>())

    kord.login {
        logger.info("Login successful")
    }
}

private suspend fun Kord.createCommands(commands: List<ChatInputCommand>) {
    val commandsByGuildId = commands.groupBy { it.guildId }
    createGuildApplicationCommands(Snowflake(221269014247112704)) {

    }

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
                ?: throw CommandException("Unable to handle command: $commandName. Maybe this command was deleted?")
        } catch (e: Exception) {
            logger.logInteractionException(e)
            interaction.respondWithException(e)
        }
    }
}

private fun Kord.registerAutoCompleteHandlers(commands: List<AutoCompleteCommand>) {
    on<AutoCompleteInteractionCreateEvent> {
        val command = interaction.command
        commands.find { it.name == command.rootName }?.handleAutoComplete(interaction)
    }
}

