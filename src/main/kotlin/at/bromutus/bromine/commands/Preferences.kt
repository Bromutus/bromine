package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.appdata.AppConfig
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.appdata.UserPreferences
import at.bromutus.bromine.appdata.readUserPreferences
import at.bromutus.bromine.appdata.writeUserPreferences
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.embed
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class PreferencesCommand(
    private val config: AppConfig,
) : ChatInputCommand {
    override val name = "preferences"
    override val description = "Commands related to user preferences"

    private val listCommand = "list"
    private val listCommandDescription = "List user preferences"
    private val setCommand = "set"
    private val setCommandDescription = "Set user preferences"
    private val resetCommand = "reset"
    private val resetCommandDescription = "Reset user preferences"
    private val clearCommand = "clear"
    private val clearCommandDescription = "Clear all user preferences"

    private val checkpoints get() = config.checkpoints.installed

    private object Options {
        const val CHECKPOINT = "checkpoint"
        const val CHECKPOINT_DESCRIPTION = "The default stable diffusion checkpoint"
        const val CFG = "cfg"
        const val CFG_DESCRIPTION = "The default CFG-scale value"
        const val WIDTH = "width"
        const val WIDTH_DESCRIPTION = "The default width of generated images"
        const val HEIGHT = "height"
        const val HEIGHT_DESCRIPTION = "The default height of generated images"
        const val COUNT = "count"
        const val COUNT_DESCRIPTION = "The default number of generated images"
        const val PROMPT_PREFIX = "prompt-prefix"
        const val PROMPT_DESCRIPTION = "Text to always add to the prompt"
        const val NEGATIVE_PROMPT_PREFIX = "negative-prompt-prefix"
        const val NEGATIVE_PROMPT_DESCRIPTION = "Text to always add to the negative prompt"
    }

    override suspend fun buildCommand(builder: ChatInputCreateBuilder) {
        builder.apply {
            subCommand(listCommand, listCommandDescription)
            subCommand(setCommand, setCommandDescription) {
                if (checkpoints.isNotEmpty()) {
                    string(name = Options.CHECKPOINT, description = Options.CHECKPOINT_DESCRIPTION) {
                        required = false
                        checkpoints.forEach {
                            choice(name = it.name, value = it.id)
                        }
                    }
                }
                number(name = Options.CFG, description = Options.CFG_DESCRIPTION) {
                    required = false
                    minValue = config.commands.global.cfg.min
                    maxValue = config.commands.global.cfg.max
                }
                integer(name = Options.WIDTH, description = Options.WIDTH_DESCRIPTION) {
                    required = false
                    minValue = config.commands.global.width.min.toLong()
                    maxValue = config.commands.global.width.max.toLong()
                }
                integer(name = Options.HEIGHT, description = Options.HEIGHT_DESCRIPTION) {
                    required = false
                    minValue = config.commands.global.height.min.toLong()
                    maxValue = config.commands.global.height.max.toLong()
                }
                integer(name = Options.COUNT, description = Options.COUNT_DESCRIPTION) {
                    required = false
                    minValue = config.commands.global.count.min.toLong()
                    maxValue = config.commands.global.count.max.toLong()
                }
                string(name = Options.PROMPT_PREFIX, description = Options.PROMPT_DESCRIPTION) {
                    required = false
                }
                string(name = Options.NEGATIVE_PROMPT_PREFIX, description = Options.NEGATIVE_PROMPT_DESCRIPTION) {
                    required = false
                }
            }
            subCommand(resetCommand, resetCommandDescription) {
                boolean(name = Options.CHECKPOINT, description = Options.CHECKPOINT_DESCRIPTION) {
                    required = false
                }
                boolean(name = Options.CFG, description = Options.CFG_DESCRIPTION) {
                    required = false
                }
                boolean(name = Options.WIDTH, description = Options.WIDTH_DESCRIPTION) {
                    required = false
                }
                boolean(name = Options.HEIGHT, description = Options.HEIGHT_DESCRIPTION) {
                    required = false
                }
                boolean(name = Options.COUNT, description = Options.COUNT_DESCRIPTION) {
                    required = false
                }
                boolean(name = Options.PROMPT_PREFIX, description = Options.PROMPT_DESCRIPTION) {
                    required = false
                }
                boolean(name = Options.NEGATIVE_PROMPT_PREFIX, description = Options.NEGATIVE_PROMPT_DESCRIPTION) {
                    required = false
                }
            }
            subCommand(clearCommand, clearCommandDescription)
        }
    }

    override suspend fun handleInteraction(interaction: ChatInputCommandInteraction) {
        val initialResponse = interaction.deferEphemeralResponse()

        try {
            val command = interaction.command as? SubCommand
                ?: throw IllegalStateException("Expected a SubCommand but got ${interaction.command.javaClass.simpleName}")

            when (command.name) {
                listCommand -> {
                    val user = interaction.user
                    val preferences = readUserPreferences(user.tag)
                    val preferenceEntries = buildMap {
                        val checkpoint = preferences.checkpoint?.let { id -> checkpoints.find { id == it.id } }
                        if (checkpoint != null) put(Options.CHECKPOINT, checkpoint.name)
                        if (preferences.cfg != null) put(Options.CFG, "${preferences.cfg}")
                        if (preferences.width != null) put(Options.WIDTH, "${preferences.width}px")
                        if (preferences.height != null) put(Options.HEIGHT, "${preferences.height}px")
                        if (preferences.count != null) put(Options.COUNT, "${preferences.count}")
                        if (preferences.promptPrefix != null)
                            put(Options.PROMPT_PREFIX, "\"${preferences.promptPrefix}\"")
                        if (preferences.negativePromptPrefix != null)
                            put(Options.NEGATIVE_PROMPT_PREFIX, "\"${preferences.negativePromptPrefix}\"")
                    }

                    initialResponse.respond {
                        embed {
                            title = "Preferences for ${user.username}"
                            description = if (preferenceEntries.isEmpty()) {
                                "None"
                            } else {
                                preferenceEntries.map { (key, value) ->
                                    "**$key** = $value"
                                }.joinToString("\n")
                            }
                            color = AppColors.success
                        }
                    }
                }

                setCommand -> {
                    val user = interaction.user
                    val preferences = readUserPreferences(user.tag)
                    val checkpoint = command.strings[Options.CHECKPOINT] ?: preferences.checkpoint
                    val cfg = command.numbers[Options.CFG] ?: preferences.cfg
                    val width = command.integers[Options.WIDTH]?.toInt() ?: preferences.width
                    val height = command.integers[Options.HEIGHT]?.toInt() ?: preferences.height
                    val count = command.integers[Options.COUNT]?.toInt() ?: preferences.count
                    val promptPrefix = command.strings[Options.PROMPT_PREFIX] ?: preferences.promptPrefix
                    val negativePromptPrefix =
                        command.strings[Options.NEGATIVE_PROMPT_PREFIX] ?: preferences.negativePromptPrefix
                    writeUserPreferences(
                        user.tag,
                        UserPreferences(
                            checkpoint = checkpoint,
                            cfg = cfg,
                            width = width,
                            height = height,
                            count = count,
                            promptPrefix = promptPrefix,
                            negativePromptPrefix = negativePromptPrefix,
                        ),
                    )

                    initialResponse.respond {
                        embed {
                            title = "Preferences updated"
                            color = AppColors.success
                        }
                    }
                }

                resetCommand -> {
                    val user = interaction.user
                    val preferences = readUserPreferences(user.tag)
                    val checkpoint = command.booleans[Options.CHECKPOINT] ?: false
                    val cfg = command.booleans[Options.CFG] ?: false
                    val width = command.booleans[Options.WIDTH] ?: false
                    val height = command.booleans[Options.HEIGHT] ?: false
                    val count = command.booleans[Options.COUNT] ?: false
                    val promptPrefix = command.booleans[Options.PROMPT_PREFIX] ?: false
                    val negativePromptPrefix = command.booleans[Options.NEGATIVE_PROMPT_PREFIX] ?: false
                    writeUserPreferences(
                        user.tag,
                        UserPreferences(
                            checkpoint = if (checkpoint) null else preferences.checkpoint,
                            cfg = if (cfg) null else preferences.cfg,
                            width = if (width) null else preferences.width,
                            height = if (height) null else preferences.height,
                            count = if (count) null else preferences.count,
                            promptPrefix = if (promptPrefix) null else preferences.promptPrefix,
                            negativePromptPrefix = if (negativePromptPrefix) null else preferences.negativePromptPrefix,
                        )
                    )

                    initialResponse.respond {
                        embed {
                            title = "Preferences updated"
                            color = AppColors.success
                        }
                    }
                }

                clearCommand -> {
                    val user = interaction.user
                    writeUserPreferences(user.tag, UserPreferences())
                }
            }
        } catch (e: Exception) {
            logger.logInteractionException(e)
            initialResponse.respondWithException(e)
        }
    }
}