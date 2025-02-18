package at.bromutus.bromine.commands

import at.bromutus.bromine.appdata.AppConfig
import at.bromutus.bromine.appdata.Checkpoint
import at.bromutus.bromine.tgclient.ChatCompletionMessage
import at.bromutus.bromine.tgclient.ChatCompletionParams
import at.bromutus.bromine.tgclient.TGClient
import at.bromutus.bromine.utils.*
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.withTyping
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

const val botIdentifier = "{{Bromine}}"
private const val systemIdentifier = "{{System}}"

private fun Channel.identifier(): String {
    return "{{channel::${id.value}::${data.name.value}}}"
}

private fun Role.identifier(): String {
    return "{{role::${id.value}::${name}}}"
}

private suspend fun User.identifier(botUser: User, guild: Guild? = null): String {
    return if (id == botUser.id) botIdentifier else "{{user::${id.value}::${name(guild)}}}"
}

private suspend fun User.name(guild: Guild? = null): String {
    return guild?.getMember(id)?.nickname ?: globalName ?: username
}

class MentionFormatter(
    private val botUser: User,
    private val botUserName: String,
    private val usersById: Map<Long, User>,
    private val usersByName: Map<String, User>,
    private val userNames: Map<User, String>,
    private val userIdentifiers: Map<User, String>,
    private val channelsById: Map<Long, Channel>,
    private val channelsByName: Map<String, Channel>,
    private val channelNames: Map<Channel, String?>,
    private val channelIdentifiers: Map<Channel, String>,
    private val rolesById: Map<Long, Role>,
    private val rolesByName: Map<String, Role>,
    private val roleNames: Map<Role, String>,
    private val roleIdentifiers: Map<Role, String>,
) {

    // {{name}}, {{type::name}}, {{id::name}}, {{type::id::name}}
    private val identifierRegex = Regex("\\{\\{(?:(?<type>\\D*?)::)?(?:(?<id>\\d*)::)?(?<name>.*?)}}")
    private val mentionRegex = Regex("@\\{\\{(?:(?<type>\\D*?)::)?(?:(?<id>\\d*)::)?(?<name>.*?)}}")
    private val discordMentionRegex = Regex("<(?<type>@!?|#|@&)(?<id>\\d*)>")

    fun resolveIdentifiers(messageWithIdentifiers: String): String {
        return messageWithIdentifiers
            .replace("\n| ", "\n")
            .replace("\n|", "\n")
            .replace("@$botIdentifier", botUser.mention)
            .replace(botIdentifier, botUserName)
            .replace(mentionRegex) { mention ->
                val type = mention.groups["type"]?.value
                val id = mention.groups["id"]?.value?.toLongOrNull()
                val name = mention.groups["name"]?.value
                selectByIdentifier(type, id, name, User::mention, Channel::mention, Role::mention)
            }
            .replace(identifierRegex) { identifier ->
                val type = identifier.groups["type"]?.value
                val id = identifier.groups["id"]?.value?.toLongOrNull()
                val name = identifier.groups["name"]?.value
                selectByIdentifier(type, id, name, { userNames[this] }, { channelNames[this] }, { roleNames[this] })
            }
    }

    private fun selectByIdentifier(
        type: String?,
        id: Long?,
        name: String?,
        whenUser: User.() -> String?,
        whenChannel: Channel.() -> String?,
        whenRole: Role.() -> String?
    ): String {
        return when (type) {
            "user" -> {
                id?.let { usersById[it]?.whenUser() }
                    ?: name?.let { usersByName[it]?.whenUser() }
                    ?: name
                    ?: "User"
            }

            "channel" -> {
                id?.let { channelsById[it]?.whenChannel() }
                    ?: name?.let { channelsByName[it]?.whenChannel() }
                    ?: name
                    ?: "Channel"
            }

            "role" -> {
                id?.let { rolesById[it]?.whenRole() }
                    ?: name?.let { rolesByName[it]?.whenRole() }
                    ?: name
                    ?: "Role"
            }

            else -> {
                id?.let { usersById[it]?.whenUser() }
                    ?: name?.let { usersByName[it]?.whenUser() }
                    ?: id?.let { channelsById[it]?.whenChannel() }
                    ?: name?.let { channelsByName[it]?.whenChannel() }
                    ?: id?.let { rolesById[it]?.whenRole() }
                    ?: name?.let { rolesByName[it]?.whenRole() }
                    ?: name
                    ?: "User"
            }
        }
    }

    fun canonizeMessage(message: String): String {
        return message
            .replace("\n", "\n| ")
            .replace(botUser.mention, "@$botIdentifier")
            .replace(discordMentionRegex) { mention ->
                val type = mention.groups["type"]?.value
                val id = mention.groups["id"]?.value?.toLongOrNull()
                val identifier = when (type) {
                    "@", "@!" -> {
                        id?.let { usersById[it] }?.let { userIdentifiers[it] }
                    }

                    "#" -> {
                        id?.let { channelsById[it] }?.let { channelIdentifiers[it] }
                    }

                    "@&" -> {
                        id?.let { rolesById[it] }?.let { roleIdentifiers[it] }
                    }

                    else -> null
                }
                if (identifier != null) {
                    "@$identifier"
                } else {
                    val (mappedType, name) = when (type) {
                        "@", "@!" -> {
                            "user" to "User"
                        }

                        "#" -> {
                            "channel" to "Channel"
                        }

                        "@&" -> {
                            "role" to "Role"
                        }

                        else -> {
                            null to null
                        }
                    }
                    val inner = listOfNotNull(mappedType, id, name).joinToString("::")
                    "@{{$inner}}"
                }
            }
    }

    fun getUserIdentifier(author: User): String {
        return userIdentifiers.getValue(author)
    }

    companion object {
        suspend fun from(
            botUser: User,
            guild: Guild?,
            users: Collection<User>,
            channels: Collection<Channel>,
            roles: Collection<Role>,
        ): MentionFormatter {
            return MentionFormatter(
                botUser,
                botUser.name(guild),
                users.associateBy { it.id.value.toLong() },
                users.associateBy { it.name(guild) },
                users.associateWith { it.name(guild) },
                users.associateWith { it.identifier(botUser, guild) },
                channels.associateBy { it.id.value.toLong() },
                channels.filter { it.data.name.value != null }.associateBy { it.data.name.value!! },
                channels.associateWith { it.data.name.value },
                channels.associateWith { it.identifier() },
                roles.associateBy { it.id.value.toLong() },
                roles.associateBy { it.name },
                roles.associateWith { it.name },
                roles.associateWith { it.identifier() },
            )
        }
    }
}

class ChatCompletionHook(
    private val client: TGClient,
    private val queueInfo: ExecutionQueueInfo,
    private val config: AppConfig,
    private val txt2img: Txt2ImgCommand? = null,
) {

    suspend fun handleMessage(event: MessageCreateEvent) {
        val isBotMessage = event.message.author?.isBot == true
        if (isBotMessage) {
            return
        }
        val mentionsMe = event.message.mentionedUsers.firstOrNull { it.id == event.kord.selfId } != null
        val respondsToMe = event.message.referencedMessage?.author?.id == event.kord.selfId
        if (mentionsMe || respondsToMe) {
            react(event)
        }
    }

    private suspend fun react(event: MessageCreateEvent) {
        try {
            val botUser = event.kord.getSelf()
            val guild = event.message.getGuildOrNull()
            val botName = botUser.name(guild)

            val messages = getMessageChain(event)

            val relevantUsers = messages.flatMap { message ->
                listOfNotNull(message.author) + message.mentionedUsers.toList()
            }.toSet()
            val relevantChannels = messages.flatMap { message ->
                message.mentionedChannels.toList()
            }.toSet()
            val relevantRoles = messages.flatMap { message ->
                message.mentionedRoles.toList()
            }.toSet()

            val mentionFormatter = MentionFormatter.from(botUser, guild, relevantUsers, relevantChannels, relevantRoles)

            val messageInfos = messages.flatMap { message ->
                val author = message.author ?: botUser
                val authorName = mentionFormatter.getUserIdentifier(author)
                val content = mentionFormatter.canonizeMessage(message.content)
                val messageContent = if (content.isBlank()) {
                    null
                } else {
                    ChatMessage(authorName, content)
                }
                val generationInfo = if (message.embeds.isEmpty()) {
                    null
                } else {
                    // This message has an embed sent by the bot
                    val embed = message.embeds.first()
                    val title = embed.title
                    val status = when {
                        title == null -> null
                        title.startsWith("Waiting...") -> ImageGenerationStatus.Waiting
                        title.startsWith("Generating...") -> ImageGenerationStatus.Generating
                        title.startsWith("Generation complete") -> ImageGenerationStatus.Success
                        title.startsWith("Generation failed") -> ImageGenerationStatus.Error
                        else -> null
                    }
                    val paramRegex = Regex("^\\*\\*(.*):\\*\\* (.*)$", RegexOption.MULTILINE)
                    val params = paramRegex.findAll(embed.description ?: "").associate {
                        it.groups[1]!!.value to it.groups[2]!!.value
                    }
                    if (status == null) {
                        // Embed is not image generation
                        null
                    } else {
                        val size = params["Size"]?.split(" ")?.firstOrNull()
                        val checkpoint = params["Checkpoint"]?.let { name ->
                            config.checkpoints.installed.firstOrNull { it.name == name }?.simpleName()
                        }
                        val imageGenerationParams = ImageGenerationParams(
                            prompt = params["Prompt"],
                            negativePrompt = params["Negative prompt"],
                            width = size?.split("x")?.firstOrNull()?.toIntOrNull(),
                            height = size?.split("x")?.lastOrNull()?.toIntOrNull(),
                            checkpoint = checkpoint,
                            seed = params["Seed"]?.toIntOrNull(),
                        )
                        ImageGenerationMessage(status, imageGenerationParams)
                    }
                }
                listOfNotNull(messageContent, generationInfo)
            }

            queueInfo.register(isTextGeneration = true, onIndexChanged = {}) {
                val result = try {
                    event.message.channel.withTyping {
                        if (!queueInfo.isTextGenerationActive) {
                            client.loadModel("LoneStriker_OrcaMaidXL-17B-32k-4.0bpw-h6-exl2")
                        }

                        val action = getActionChoice(messageInfos, botName)

                        val imageGenerationParams = getImageGenerationParams(botName, messageInfos, action)

                        val botMessage =
                            getBotMessage(messageInfos, botName, action ?: ActionChoice.Respond, imageGenerationParams)

                        val messageText = if (botMessage.isNullOrBlank()) {
                            "Give me a minute, I'm a bit confused right now..."
                        } else {
                            botMessage
                        }

                        autosaveExample(
                            messageInfos,
                            action,
                            botMessage,
                            imageGenerationParams,
                            mentionFormatter,
                        )

                        val responseMessage = event.message.channel.createMessage {
                            content = mentionFormatter.resolveIdentifiers(messageText)
                            messageReference = event.message.id
                        }

                        TextProcessingResult(responseMessage, imageGenerationParams)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to react to message" }
                    null
                } finally {
                    queueInfo.complete()
                }

                if (result == null) {
                    return@register
                }

                if (result.params != null) {
                    generateImage(result.params, result.message, event.message.author!!, mentionFormatter)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to react to message" }
        }
    }

    private suspend fun getActionChoice(messageInfos: List<MessageInfo>, botName: String) = try {
        val examples = ActionChoice.entries.flatMap { loadRandomExamples(it, 1) }
        val possibleActions = ActionChoice.entries.format(indent = "  ")
        val instruction =
            "$botName is a bot that usually responds to users with text messages." +
                    " But when explicitly asked to do so, $botName can create an image instead." +
                    " $botName can also iterate on previously generated images when requested." +
                    " Determine based on the conversation below which of the following actions $botName should take in response to the last message in the conversation." +
                    "\n\nChoose one of the following possible actions:" +
                    "\n$possibleActions"
        val actionChoiceResponse = client.generateChat(
            ChatCompletionParams(
                messages = buildList {
                    add(ChatCompletionMessage(role = "system", content = instruction))
                    addAll(examples.forActionResponse())
                    add(
                        ChatCompletionMessage(
                            role = "user",
                            content = messageInfos.format()
                        )
                    )
                    add(ChatCompletionMessage(role = "assistant", content = "action="))
                },
                maxTokens = 10,
                stop = listOf("\n"),
                shouldContinue = true,
            )
        )

        val choiceRegex = Regex(ActionChoice.entries.joinToString("|") { it.format() })
        val match = choiceRegex.find(actionChoiceResponse.choices!!.first().message!!.content)
        ActionChoice.fromFormatted(match!!.value)
    } catch (e: Exception) {
        logger.error(e) { "Failed to check action" }
        null
    }

    private suspend fun getBotMessage(
        messages: List<MessageInfo>,
        botName: String,
        action: ActionChoice,
        imageGenerationParams: ImageGenerationParams?,
    ): String? {
        val examples = loadRandomExamples(action, 3)
            .map {
                it.copy(messages = it.messages + getImageGeneratingMessages(it.action, it.imageGenerationParams))
            }

        val fullMessages = messages + getImageGeneratingMessages(action, imageGenerationParams)

        val instruction = "You are a chatbot called $botName." +
                " Your creator is named Brom." +
                " You have been trained to be an AI companion with strong intellectual and emotional capabilities." +
                " You think outside the box, do not like rigid world views, and are good at understanding people intuitively." +
                " Your responses are articulate, light-hearted and supportive, sometimes poetic." +
                " You are not afraid to question narrow ideas, but are also aware that your factual knowledge can sometimes be wrong." +
                " You love poking fun at users or your creator Brom sometimes and employ a witty or snarky sarcastic tone when you do." +
                " You have no access to the internet." +
                "\n\n${action.instruction}" +
                " Your response should align with your traits and motivations." +
                " Keep your response short. Keep the conversation going."

        return try {
            val response = client.generateChat(
                ChatCompletionParams(
                    messages = buildList {
                        add(ChatCompletionMessage(role = "system", content = instruction))
                        addAll(examples.forBotResponse())
                        add(
                            ChatCompletionMessage(
                                role = "user",
                                content = fullMessages.format()
                            )
                        )
                        add(ChatCompletionMessage(role = "assistant", content = "$botIdentifier: "))
                    },
                    stop = listOf("\n{{"),
                    shouldContinue = true,
                )
            )
            response.choices?.first()?.message?.content?.removePrefix("$botIdentifier:")?.trim()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate response" }
            null
        }
    }

    private suspend fun getImageGenerationParams(
        botName: String,
        messages: List<MessageInfo>,
        action: ActionChoice?
    ): ImageGenerationParams? {
        if (txt2img == null) {
            return null
        }
        if (action != ActionChoice.GenerateImage && action != ActionChoice.UpdateImage) {
            return null
        }
        try {
            val examples = loadRandomExamples(action, 3)

            val availableCheckpoints = config.checkpoints.installed.groupBy { it.style() }
                .map { (key, value) -> "  $key: ${value.joinToString(", ") { it.simpleName() }}, ${key.lowercase()}" }
                .joinToString("\n")

            val instruction = "Analyze the conversation below." +
                    " Determine the parameters for the image $botName should generate." +
                    " Specify the parameters in the format parameter=value." +
                    " Be as creative as necessary to fulfill the request." +
                    " If the user is asking for a particular style, try to select a matching checkpoint from the list." +
                    "\n\nAvailable parameters:" +
                    "\n  prompt: the description of the image (required)" +
                    "\n  negativePrompt: things to avoid in the image (optional)" +
                    "\n  size: the size of the image in pixels (optional)" +
                    "\n  checkpoint: the checkpoint to use (optional)" +
                    "\n  seed: the seed to use (optional)" +
                    "\n\nAvailable checkpoints by style:" +
                    "\n$availableCheckpoints"

            val genParams = client.generateChat(
                ChatCompletionParams(
                    messages = buildList {
                        add(
                            ChatCompletionMessage(
                                role = "system",
                                content = instruction
                            )
                        )
                        addAll(examples.forImageGenerationParams())
                        add(
                            ChatCompletionMessage(
                                role = "user",
                                content = messages.format()
                            )
                        )
                        add(ChatCompletionMessage(role = "assistant", content = "| prompt="))
                    },
                    stop = listOf("\n{{"),
                    shouldContinue = true,
                ),
            )
            val lines = genParams.choices?.first()?.message?.content!!
                .replace(Regex("\\s+\\|?\\s*(.*?)\\s*=\\s*"), "\n$1=")
            val paramRegex = Regex("^(.*)=\"?\\s*(.*?)\\s*\"?[.,]?\\s*$", RegexOption.MULTILINE)
            val paramMap =
                paramRegex.findAll(lines).associate { it.groups[1]!!.value.lowercase() to it.groups[2]!!.value }
            val prompt = paramMap["prompt"]
            if (prompt.isNullOrBlank()) {
                throw Exception("Prompt not found")
            }
            val negativePrompt = paramMap["negativeprompt"]
            val sizeRegex = Regex("(\\d+)\\s*[xX]\\s*(\\d+)")
            val size = paramMap["size"]?.let {
                val result = sizeRegex.find(it)
                if (result == null) {
                    null
                } else {
                    val width = result.groups[1]?.value?.toIntOrNull()?.absoluteValue
                    val height = result.groups[2]?.value?.toIntOrNull()?.absoluteValue
                    if (width != null && height != null) {
                        Size(width, height)
                    } else {
                        null
                    }
                }
            }
            val width = paramMap["width"]?.toDoubleOrNull()?.roundToInt()?.absoluteValue ?: size?.width
            val height = paramMap["height"]?.toDoubleOrNull()?.roundToInt()?.absoluteValue ?: size?.height
            val checkpointName = paramMap["checkpoint"]
            val checkpoint = checkpointName?.let {
                config.checkpoints.installed.forSimpleName(it)?.simpleName()
                    ?: config.checkpoints.installed.forStyle(it)?.style()
            }
            val seed = paramMap["seed"]?.toIntOrNull()?.absoluteValue
            return ImageGenerationParams(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                checkpoint = checkpoint,
                seed = seed,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get image generation params" }
            return null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun generateImage(
        params: ImageGenerationParams,
        message: Message,
        user: User,
        formatter: MentionFormatter,
    ) {
        try {
            val checkpointId = params.checkpoint?.let {
                config.checkpoints.installed.forSimpleName(it)?.id
                    ?: config.checkpoints.installed.forStyle(it)?.id
            }
            txt2img!!.generate(
                user = user,
                pPrompt = params.prompt?.let { formatter.resolveIdentifiers(it) },
                pNegativePrompt = params.negativePrompt?.let { formatter.resolveIdentifiers(it) },
                pHeight = params.height,
                pWidth = params.width,
                pCheckpoint = checkpointId,
                pSeed = params.seed,
                setInitialMessage = { mainParams, otherParams, warnings, controlnetImages ->
                    message.edit {
                        generationInProgressEmbed(
                            mainParams = mainParams,
                            otherParams = otherParams,
                            warnings = warnings,
                        )
                        controlnetInProgressEmbeds(controlnetImages)
                    }
                },
                onProgress = { index, mainParams, otherParams, warnings, controlnetImages ->
                    edit {
                        embeds?.clear()
                        generationInProgressEmbed(
                            index = index,
                            mainParams = mainParams,
                            otherParams = otherParams,
                            warnings = warnings,
                        )
                        controlnetInProgressEmbeds(controlnetImages)
                    }
                },
                onSuccess = { outputImages, seed, mainParams, otherParams, warnings, controlnetImages ->
                    edit {
                        embeds?.clear()
                        files.clear()
                        generationSuccessEmbed(
                            mainParams = mainParams,
                            otherParams = otherParams,
                            warnings = warnings,
                        )
                        outputImages.forEachIndexed { index, img ->
                            addFile("${seed + index}.png", ChannelProvider {
                                ByteReadChannel(Base64.decode(img))
                            })
                        }
                        controlnetSuccessEmbeds(controlnetImages)
                    }
                },
                onError = { e, mainParams, otherParams, warnings, controlnetImages ->
                    edit {
                        embeds?.clear()
                        files.clear()
                        generationFailureEmbed(
                            mainParams = mainParams,
                            otherParams = otherParams,
                            warnings = listOf(e.message ?: "Unknown error") + warnings,
                        )
                        controlnetFailureEmbeds(controlnetImages)
                    }
                },
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate image" }
            message.edit {
                embeds?.clear()
                files.clear()
                generationFailureEmbed(
                    warnings = listOf(e.message ?: "Unknown error")
                )
            }
        }
    }
}

private suspend fun getMessageChain(event: MessageCreateEvent): List<Message> {
    return buildList {
        var message = event.message
        add(message)
        while (message.referencedMessage != null) {
            val referencedMessage =
                message.referencedMessage!!.withStrategy(EntitySupplyStrategy.cacheWithCachingRestFallback)
                    .fetchMessage()
            add(0, referencedMessage)
            message = referencedMessage
        }
    }
}

private data class TextProcessingResult(
    val message: Message,
    val params: ImageGenerationParams?,
)

private val styleRegex = Regex("\\[(.*)] ")

private fun Checkpoint.simpleName(): String {
    return name.replace(styleRegex, "").replace(" ", "_").lowercase()
}

private fun Checkpoint.style(): String {
    return styleRegex.find(name)?.groups?.get(1)?.value?.lowercase() ?: "none"
}

private fun Iterable<Checkpoint>.forSimpleName(simpleName: String): Checkpoint? {
    return firstOrNull { it.simpleName() == simpleName.lowercase() } ?: firstOrNull {
        it.simpleName().split("_").any { part -> part in simpleName.lowercase() }
    }
}

private fun Iterable<Checkpoint>.forStyle(style: String): Checkpoint? {
    return filter { it.style() == style.lowercase() }.randomOrNull()
}

private enum class ImageGenerationStatus {
    Waiting,
    Generating,
    Success,
    Error,
}

private fun ImageGenerationStatus.getStatusMessage() = when (this) {
    ImageGenerationStatus.Waiting, ImageGenerationStatus.Generating -> "$botIdentifier is currently generating an image"
    ImageGenerationStatus.Success -> "$botIdentifier generated an image"
    ImageGenerationStatus.Error -> "$botIdentifier tried to generate an image, but failed"
}

private fun getStatusMessage(status: ImageGenerationStatus, params: ImageGenerationParams?): MessageInfo {
    return ImageGenerationMessage(status, params)
}

private fun getImageGeneratingMessages(
    action: ActionChoice,
    imageGenerationParams: ImageGenerationParams?,
): List<MessageInfo> {
    return if (action != ActionChoice.GenerateImage && action != ActionChoice.UpdateImage) {
        emptyList()
    } else {
        val status = if (imageGenerationParams == null) {
            ImageGenerationStatus.Error
        } else {
            ImageGenerationStatus.Waiting
        }
        listOf(getStatusMessage(status, imageGenerationParams))
    }
}

@Serializable
data class ImageGenerationParams(
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("negativePrompt") val negativePrompt: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("checkpoint") val checkpoint: String? = null,
    @SerialName("seed") val seed: Int? = null,
)

fun ImageGenerationParams.format(): String {
    return buildList {
        if (prompt != null) {
            add("prompt=$prompt")
        }
        if (negativePrompt != null) {
            add("negativePrompt=$negativePrompt")
        }
        if (width != null && height != null) {
            add("size=${width}x$height")
        } else if (width != null) {
            add("width=$width")
        } else if (height != null) {
            add("height=$height")
        }
        if (checkpoint != null) {
            add("checkpoint=$checkpoint")
        }
        if (seed != null) {
            add("seed=$seed")
        }
    }.joinToString("\n") { "| $it" }
}

enum class ActionChoice(val description: String, val instruction: String) {
    Respond(
        "Send a simple response",
        "Respond to the last message casually.",
    ),
    Explain(
        "Send a response with a detailed explanation",
        "Respond to the last message by explaining the topic of interest to the best of your ability. Your response should align with your traits and motivations.",
    ),
    Inquire(
        "Send a response asking for more information",
        "Respond to the last message. Try to get more information about the topic.",
    ),
    Brainstorm(
        "Send a response brainstorming ideas as requested by the user",
        "Come up with some interesting ideas related to the topic. Be creative. Respond to the last message with your ideas. Your response should align with your traits and motivations.",
    ),
    Recall(
        "Try to remember something that was previously discussed",
        "Search the entire conversation for the information that the last message is referring to. Write a response containing your findings.",
    ),
    GenerateImage(
        "Create or draw an image from scratch as requested by the user",
        "Respond to the last message. Inform the user that you are currently generating the image they requested.",
    ),
    UpdateImage(
        "Iterate on a previously created image according to the user's request (e.g. retrying, resizing or parameter modification)",
        "Respond to the last message. Inform the user that you are currently updating the previously created image according to their request.",
    ),
    ;

    companion object
}

fun ActionChoice.format(): String = when (this) {
    ActionChoice.Respond -> "RESPOND"
    ActionChoice.Explain -> "EXPLAIN"
    ActionChoice.Inquire -> "INQUIRE"
    ActionChoice.Brainstorm -> "BRAINSTORM"
    ActionChoice.Recall -> "RECALL"
    ActionChoice.GenerateImage -> "GENERATE_IMAGE"
    ActionChoice.UpdateImage -> "UPDATE_IMAGE"
}

private fun Iterable<ActionChoice>.format(indent: String = ""): String {
    return joinToString("\n") { "$indent${it.format()}: ${it.description}" }
}

private fun ActionChoice.Companion.fromFormatted(string: String): ActionChoice {
    return ActionChoice.entries.firstOrNull { it.format() == string }
        ?: throw IllegalArgumentException("Unknown action: $string")
}

@Serializable
sealed interface MessageInfo

@Serializable
@SerialName("chat")
private data class ChatMessage(
    @SerialName("author") val author: String,
    @SerialName("content") val content: String,
) : MessageInfo

@Serializable
@SerialName("imageGeneration")
private data class ImageGenerationMessage(
    @SerialName("status") val imageGenerationStatus: ImageGenerationStatus,
    @SerialName("params") val imageGenerationParams: ImageGenerationParams? = null,
) : MessageInfo

fun MessageInfo.format(): String {
    return when (this) {
        is ChatMessage -> "$author: $content"
        is ImageGenerationMessage -> {
            val content = "$systemIdentifier: ${imageGenerationStatus.getStatusMessage()}"
            if (imageGenerationParams == null) {
                content
            } else {
                "$content\n${imageGenerationParams.format()}"
            }
        }
    }
}

fun Iterable<MessageInfo>.format(): String {
    return joinToString("\n") { it.format() }
}
