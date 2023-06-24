package at.bromutus.bromine.commands

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder

interface ChatInputCommand {
    val name: String
    val description: String
    val guildId: Snowflake?
        get() = null

    suspend fun buildCommand(builder: ChatInputCreateBuilder)

    suspend fun handleInteraction(interaction: ChatInputCommandInteraction)
}