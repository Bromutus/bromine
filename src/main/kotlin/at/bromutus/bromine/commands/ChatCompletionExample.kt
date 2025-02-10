package at.bromutus.bromine.commands

import at.bromutus.bromine.tgclient.ChatCompletionMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

@Serializable
data class Example(
    @SerialName("messages") val messages: List<MessageInfo>,
    @SerialName("action") val action: ActionChoice,
    @SerialName("response") val response: String,
    @SerialName("imageGenerationParams") val imageGenerationParams: ImageGenerationParams? = null
)

private fun Example.forActionResponse(): List<ChatCompletionMessage> {
    return listOf(
        ChatCompletionMessage(role = "user", content = messages.format()),
        ChatCompletionMessage(role = "assistant", content = "action=${action.format()}"),
    )
}

fun Iterable<Example>.forActionResponse(): List<ChatCompletionMessage> {
    return flatMap { it.forActionResponse() }
}

private fun Example.forBotResponse(): List<ChatCompletionMessage> {
    return listOf(
        ChatCompletionMessage(role = "user", content = messages.format()),
        ChatCompletionMessage(role = "assistant", content = "$botIdentifier: $response"),
    )
}

fun Iterable<Example>.forBotResponse(): List<ChatCompletionMessage> {
    return flatMap { it.forBotResponse() }
}

private fun Example.forImageGenerationParams(): List<ChatCompletionMessage> {
    return if (imageGenerationParams == null) {
        emptyList()
    } else {
        listOf(
            ChatCompletionMessage(role = "user", content = messages.format()),
            ChatCompletionMessage(role = "assistant", content = imageGenerationParams.format()),
        )
    }
}

fun Iterable<Example>.forImageGenerationParams(): List<ChatCompletionMessage> {
    return flatMap { it.forImageGenerationParams() }
}

private fun examplesDirectory() = Paths.get("data", "examples")

private fun ActionChoice.fileSystemName(): String = when (this) {
    ActionChoice.Respond -> "respond"
    ActionChoice.Explain -> "explain"
    ActionChoice.Inquire -> "inquire"
    ActionChoice.Brainstorm -> "brainstorm"
    ActionChoice.Recall -> "recall"
    ActionChoice.GenerateImage -> "generate_image"
    ActionChoice.UpdateImage -> "update_image"
}

private fun examplesDirectoryForAction(action: ActionChoice) = examplesDirectory().resolve(action.fileSystemName())

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

private fun saveExample(example: Example, dateTime: LocalDateTime) {
    val exampleDir = examplesDirectoryForAction(example.action).also { it.toFile().mkdirs() }
    val fileName =
        "ex_${example.action.fileSystemName()}_autosave_${dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.json"
    val exampleFile = exampleDir.resolve(fileName)
    exampleFile.writeText(json.encodeToString(example))
}

fun loadRandomExamples(action: ActionChoice, count: Int): List<Example> {
    val examplesDir = examplesDirectoryForAction(action).also { it.toFile().mkdirs() }
    return examplesDir
        .listDirectoryEntries()
        .shuffled()
        .mapNotNull {
            try {
                json.decodeFromString<Example>(it.readText())
            } catch (_: Exception) {
                null
            }
        }
        .take(count)
}

private const val maxExampleLength = 2000

fun autosaveExample(
    messages: List<MessageInfo>,
    action: ActionChoice?,
    response: String?,
    imageGenerationParams: ImageGenerationParams?,
    formatter: MentionFormatter,
) {
    try {
        if (action == null || response == null) {
            logger.warn { "No action or response to autosave" }
            return
        }
        if ((action == ActionChoice.GenerateImage || action == ActionChoice.UpdateImage) && imageGenerationParams == null) {
            logger.warn { "No image generation params to autosave" }
            return
        }
        val resolvedResponse = formatter.resolveIdentifiers(response)
        val canonicalResponse = formatter.canonizeMessage(resolvedResponse)
        val truncatedMessages = buildList {
            var length = canonicalResponse.length
            for (message in messages.reversed()) {
                length += message.format().length
                if (length > maxExampleLength) {
                    break
                }
                add(0, message)
            }
        }
        if (truncatedMessages.isEmpty()) {
            logger.warn { "No messages to autosave" }
            return
        }
        saveExample(
            Example(truncatedMessages, action, canonicalResponse, imageGenerationParams),
            LocalDateTime.now(),
        )
    } catch (e: Exception) {
        logger.warn(e) { "Failed to autosave example" }
    }
}