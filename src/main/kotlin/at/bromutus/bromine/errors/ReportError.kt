package at.bromutus.bromine.errors

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.sdclient.SDClientException
import dev.kord.core.behavior.interaction.ActionInteractionBehavior
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.*
import dev.kord.core.entity.interaction.response.MessageInteractionResponse
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import io.github.oshai.kotlinlogging.KLogger

class CommandException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

fun KLogger.logInteractionException(e: Exception) {
    when (e) {
        is CommandException -> {
            debug("Command Exception: ${e.message}")
        }
        is SDClientException -> {
            info("Error: ${e.message}")
            info("Details: ${e.details}")
            info("Errors: ${e.errors}")
        }
        else -> {
            error("Unknown error.", e)
        }
    }
}

suspend fun ActionInteractionBehavior.respondWithException(e: Exception): PublicMessageInteractionResponseBehavior =
    respondPublic {
        embed {
            buildEmbed(e)
        }
    }

suspend fun MessageInteractionResponseBehavior.respondWithException(e: Exception): MessageInteractionResponse =
    edit {
        embeds?.clear()
        embed {
            buildEmbed(e)
        }
    }

suspend fun DeferredMessageInteractionResponseBehavior.respondWithException(e: Exception): MessageInteractionResponse =
    respond {
        embed {
            buildEmbed(e)
        }
    }

private fun EmbedBuilder.buildEmbed(e: Exception) {
    title = "Something went wrong."
    description = when {
        e is CommandException -> e.message
        e.message == "OutOfMemoryError" -> "Out of memory. Please reduce the size of the requested image."
        else -> "Unknown error."
    }
    color = AppColors.error
}