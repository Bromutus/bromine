package at.bromutus.bromine

import at.bromutus.bromine.appdata.loadAppConfig
import at.bromutus.bromine.commands.*
import at.bromutus.bromine.errors.CommandException
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.createSDClient
import at.bromutus.bromine.tgclient.createTGClient
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.cache.lruCache
import dev.kord.core.event.interaction.AutoCompleteInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun main() {
    val config = loadAppConfig()

    val sdClient = config.sd.apiUrl?.let { createSDClient(it) }

    val tgClient = config.tg.apiUrl?.let { createTGClient(it) }

    val kord = Kord(config.discord.token) {
        cache {
            messages(lruCache(1000))
        }
    }

    val executionQueueInfo = ExecutionQueueInfo()

    val txt2img = sdClient?.let { Txt2ImgCommand(it, config, tgClient, executionQueueInfo) }
    val img2img = sdClient?.let { Img2ImgCommand(it, config, tgClient, executionQueueInfo) }

    val commands = buildList {
        if (txt2img != null) {
            add(txt2img)
        }
        if (img2img != null) {
            add(img2img)
        }
        add(LoraCommand(config.lora, nsfw = false))
        add(LoraCommand(config.lora, nsfw = true))
        PreferencesCommand(config)
    }

    kord.createCommands(commands)
    kord.registerInteractionHandlers(commands)
    kord.registerAutoCompleteHandlers(commands.filterIsInstance<AutoCompleteCommand>())

    if (tgClient != null) {
        val chatCompletionHook = ChatCompletionHook(tgClient, executionQueueInfo, config, txt2img)
        kord.registerChatCompletionHook(chatCompletionHook)
    }

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

private fun Kord.registerChatCompletionHook(hook: ChatCompletionHook) {
    on<MessageCreateEvent> {
        hook.handleMessage(this)
    }
}
