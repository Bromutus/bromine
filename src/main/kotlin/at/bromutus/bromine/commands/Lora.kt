package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.Lora
import at.bromutus.bromine.LoraConfig
import at.bromutus.bromine.errors.CommandException
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.embed
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class LoraCommand(
    private val config: LoraConfig,
    private val nsfw: Boolean,
) : AutocompleteCommand {
    override val name = "lora${if (nsfw) "-nsfw" else ""}"
    override val description = "LoRA-related commands${if (nsfw) " (NSFW)" else ""}"

    private val listCommand = "list"
    private val listCommandDescription = "List available LoRAs."
    private val infoCommand = "info"
    private val infoCommandDescription = "Get information about a LoRA."

    private object OptionNames {
        object List {
            const val TAG = "tag"
        }

        object Info {
            const val LORA = "lora"
        }
    }

    override suspend fun buildCommand(builder: ChatInputCreateBuilder) {
        builder.apply {
            nsfw = this@LoraCommand.nsfw
            /* Not implemented for now
            subCommand(listCommand, listCommandDescription) {
                string(
                    name = OptionNames.List.TAG,
                    description = """
                        Only look for LoRAs with the given tag.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                    tags.forEach {
                        choice(it, it)
                    }
                }
            }
             */
            subCommand(infoCommand, infoCommandDescription) {
                string(
                    name = OptionNames.Info.LORA,
                    description = """
                        The LoRA to get information about.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = true
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun handleInteraction(interaction: ChatInputCommandInteraction) {
        val initialResponse = interaction.deferPublicResponse()

        try {
            val command = interaction.command as? SubCommand
                ?: throw IllegalStateException("Expected a SubCommand but got ${interaction.command.javaClass.simpleName}")

            when (command.name) {
                listCommand -> {
                    val tag = command.strings[OptionNames.List.TAG]

                    initialResponse.respond {
                        embed {
                            title = "Not implemented"
                            description = """
                                Coming soon...
                                For now, you can use `$name $infoCommand` to get information about the available LoRAs.
                            """.trimIndent()
                            color = AppColors.error
                        }
                    }
                }

                infoCommand -> {
                    val id = command.strings[OptionNames.Info.LORA]
                    val lora = loras.find { it.id == id }
                        ?: throw CommandException("That LoRA does not exist.")

                    initialResponse.respond {
                        embed {
                            title = lora.name
                            description = StringBuilder().apply {
                                appendLine("### __How to use this LoRA__")
                                appendLine("Add the activation key below to your prompt and adjust its weight.")
                                if (lora.keywords.isNotEmpty()) {
                                    appendLine("_This LoRA has keywords. Adding them to your prompt will make it more likely to have an effect._")
                                }
                                appendLine()
                                appendLine("**Activation key:** <lora:${lora.id}:${lora.recommendedWeights?.default ?: 1.0}>")
                                if (lora.keywords.isNotEmpty()) {
                                    appendLine("**Keywords:** ${lora.keywords.joinToString(", ")}")
                                }
                                if (lora.recommendedWeights?.high != null
                                    || lora.recommendedWeights?.low != null
                                    || lora.recommendedWeights?.default != null
                                ) {
                                    val weightString =
                                        if (lora.recommendedWeights.high == null && lora.recommendedWeights.low == null) {
                                            "around ${lora.recommendedWeights.default}"
                                        } else if (lora.recommendedWeights.high == null) {
                                            "${lora.recommendedWeights.low} or higher"
                                        } else if (lora.recommendedWeights.low == null) {
                                            "${lora.recommendedWeights.high} or lower"
                                        } else {
                                            "${lora.recommendedWeights.low} - ${lora.recommendedWeights.high}"
                                        }
                                    appendLine("**Recommended weights:** $weightString")
                                }
                            }.toString()
                            if (lora.tags.isNotEmpty() || !lora.url.isNullOrBlank()) {
                                footer {
                                    text = StringBuilder().apply {
                                        if (lora.tags.isNotEmpty()) {
                                            appendLine("Tags: ${lora.tags.joinToString(", ")}")
                                        }
                                        if (!lora.url.isNullOrBlank()) {
                                            appendLine("Source: ${lora.url}")
                                        }
                                    }.toString()
                                }
                            }
                            if (!lora.thumbnailUrl.isNullOrBlank()) {
                                thumbnail {
                                    url = lora.thumbnailUrl
                                }
                            }
                            color = AppColors.success
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.logInteractionException(e)
            initialResponse.respondWithException(e)
        }
    }

    override suspend fun handleAutocomplete(interaction: AutoCompleteInteraction) {
        val command = interaction.command as? SubCommand
        if (command?.name != infoCommand) return
        val searchText = interaction.focusedOption.value
        val autoCompleteOptions = searchKeys
            .mapValues { it.value.split(searchText.lowercase()).size - 1 }
            .filterValues { it > 0 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(25)
        interaction.suggestString {
            autoCompleteOptions.forEach {
                choice(it.name, it.id)
            }
        }
    }

    private val loras: List<Lora> by lazy {
        config.installed.filter { it.nsfw == nsfw }
    }


    private val tags: List<String> by lazy {
        config.tags.filter { tag ->
            loras.any { it.tags.contains(tag) }
        }
    }

    private val searchKeys: Map<Lora, String> by lazy {
        loras.associateWith {
            (listOf(it.id.lowercase(), it.name.lowercase()) + it.tags.map(String::lowercase)).joinToString(" | ")
        }
    }
}