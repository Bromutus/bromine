package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.AppConfig
import at.bromutus.bromine.CommandsConfig
import at.bromutus.bromine.errors.CommandException
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.ControlnetUnitParams
import at.bromutus.bromine.sdclient.Img2ImgParams
import at.bromutus.bromine.sdclient.ResizeMode
import at.bromutus.bromine.sdclient.SDClient
import at.bromutus.bromine.utils.calculateDesiredImageSize
import at.bromutus.bromine.utils.constrainToPixelSize
import at.bromutus.bromine.utils.includeText
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.random.nextUInt

private val logger = KotlinLogging.logger {}

class Img2ImgCommand(
    private val client: SDClient,
    private val config: AppConfig,
) : ChatInputCommand {
    override val name = "img2img"
    override val description = "Generate an image from another image"

    private val commandsConfig get() = config.commands
    private val checkpoints get() = config.checkpoints.installed
    private val controlnetTypes get() = config.controlnet.installed

    private object OptionNames {
        const val IMAGE = "image"
        const val PROMPT = "prompt"
        const val NEGATIVE_PROMPT = "negative-prompt"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val RESIZE_MODE = "resize-mode"
        const val COUNT = "count"
        const val DENOISING_STRENGTH = "denoising-strength"
        const val SEED = "seed"
        const val CHECKPOINT = "checkpoint"
        const val STEPS = "steps"
        const val CFG = "cfg"
        const val CONTROLNET1_IMAGE = "controlnet1-image"
        const val CONTROLNET1_TYPE = "controlnet1-type"
        const val CONTROLNET1_WEIGHT = "controlnet1-weight"
        const val CONTROLNET2_IMAGE = "controlnet2-image"
        const val CONTROLNET2_TYPE = "controlnet2-type"
        const val CONTROLNET2_WEIGHT = "controlnet2-weight"
        const val DISPLAY_SOURCE = "display-source"
    }

    private fun ResizeMode.resizeModeText() = when (this) {
        ResizeMode.Stretch -> "Just Resize"
        ResizeMode.Crop -> "Crop & Resize"
        ResizeMode.Fill -> "Resize & Fill"
        ResizeMode.Latent -> "Just Resize (Latent Upscale)"
    }

    override suspend fun buildCommand(builder: ChatInputCreateBuilder) {
        builder.apply {
            attachment(
                name = OptionNames.IMAGE,
                description = """
                    The image to use as reference.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = true
            }
            string(
                name = OptionNames.PROMPT,
                description = """
                    List of keywords or descriptions, comma-separated.
                    Example: "ocean, (boat:1.1), multiple girls".
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
            }
            string(
                name = OptionNames.NEGATIVE_PROMPT,
                description = """
                    List of keywords or descriptions to avoid, comma-separated.
                    Example: "bad anatomy, low quality".
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
            }
            integer(
                name = OptionNames.WIDTH,
                description = """
                    Width of the generated image in pixels (0 = same as original).
                    Default: ${commandsConfig.defaultWidth}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minWidth.toLong()
                maxValue = commandsConfig.maxWidth.toLong()
            }
            integer(
                name = OptionNames.HEIGHT,
                description = """
                    Height of the generated image in pixels (0 = same as original).
                    Default: ${commandsConfig.defaultHeight}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minHeight.toLong()
                maxValue = commandsConfig.maxHeight.toLong()
            }
            integer(
                name = OptionNames.COUNT,
                description = """
                    Number of images to generate.
                    Default: ${commandsConfig.defaultCount}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minCount.toLong()
                maxValue = commandsConfig.maxCount.toLong()
            }
            number(
                name = OptionNames.DENOISING_STRENGTH,
                description = """
                    Denoising strength. Lower values preserve more of the original image.
                    Default: ${commandsConfig.defaultDenoisingStrength}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minDenoisingStrength
                maxValue = commandsConfig.maxDenoisingStrength
            }
            integer(
                name = OptionNames.RESIZE_MODE,
                description = """
                    Resize mode.
                    Default: ${ResizeMode.fromUInt(commandsConfig.defaultResizeMode)!!.resizeModeText()}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                ResizeMode.values().forEach {
                    choice(name = it.resizeModeText(), value = it.intValue.toLong())
                }
            }
            integer(
                name = OptionNames.SEED,
                description = """
                    Seed for the random number generator.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 0
            }
            if (checkpoints.isNotEmpty()) {
                string(
                    name = OptionNames.CHECKPOINT,
                    description = """
                    Checkpoint to use.
                    """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                    for (ckpt in checkpoints) {
                        choice(name = ckpt.name, value = ckpt.id)
                    }
                }
            }
            integer(
                name = OptionNames.STEPS,
                description = """
                    Number of diffusion steps.
                    Default: ${commandsConfig.defaultSteps}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minSteps.toLong()
                maxValue = commandsConfig.maxSteps.toLong()
            }
            number(
                name = OptionNames.CFG,
                description = """
                    Classifier-free guidance.
                    High values increase guidance, but may lead to artifacts.
                    Default: ${commandsConfig.defaultCfg}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minCfg
                maxValue = commandsConfig.maxCfg
            }
            if (controlnetTypes.isNotEmpty()) {
                attachment(
                    name = OptionNames.CONTROLNET1_IMAGE,
                    description = """
                        Image to use for ControlNet 1.
                        Enables ControlNet 1.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                }
                string(
                    name = OptionNames.CONTROLNET1_TYPE,
                    description = """
                        Type of ControlNet 1.
                        Default: ${controlnetTypes.first().name}.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                    for (controlnet in controlnetTypes) {
                        choice(name = controlnet.name, value = controlnet.name)
                    }
                }
                number(
                    name = OptionNames.CONTROLNET1_WEIGHT,
                    description = """
                        Weight of ControlNet 1.
                        Default: ${config.controlnet.weight.default}.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                    minValue = config.controlnet.weight.min
                    maxValue = config.controlnet.weight.max
                }
                attachment(
                    name = OptionNames.CONTROLNET2_IMAGE,
                    description = """
                        Image to use for ControlNet 2.
                        Enables ControlNet 2.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                }
                string(
                    name = OptionNames.CONTROLNET2_TYPE,
                    description = """
                        Type of ControlNet 2.
                        Default: ${controlnetTypes.first().name}.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                    for (controlnet in controlnetTypes) {
                        choice(name = controlnet.name, value = controlnet.name)
                    }
                }
                number(
                    name = OptionNames.CONTROLNET2_WEIGHT,
                    description = """
                        Weight of ControlNet 2.
                        Default: ${config.controlnet.weight.default}.
                        """.trimIndent().replace("\n", " ")
                ) {
                    required = false
                    minValue = config.controlnet.weight.min
                    maxValue = config.controlnet.weight.max
                }
            }
            boolean(
                name = OptionNames.DISPLAY_SOURCE,
                description = """
                    Whether to display the source image as thumbnail in the output.
                    Default: ${commandsConfig.displaySourceImageByDefault}.
                    """.trimIndent().replace("\n", " ")
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun handleInteraction(interaction: ChatInputCommandInteraction) {
        val initialResponse = interaction.respondPublic {
            embed {
                title = "Generating..."
                description = "Depending on the input parameters, this may take a while..."
                color = AppColors.processing
            }
        }

        try {
            val command = interaction.command

            val attachment = command.attachments[OptionNames.IMAGE]!!
            if (!attachment.isImage || attachment.contentType == null) {
                throw CommandException("Image must be an image")
            }
            val originalImageUrl = attachment.url
            val image = downloadImageAsBase64(originalImageUrl, attachment.contentType!!)

            val prompt = command.strings[OptionNames.PROMPT]
            val negativePrompt = command.strings[OptionNames.NEGATIVE_PROMPT]
            val width = command.integers[OptionNames.WIDTH]?.toUInt()
            val height = command.integers[OptionNames.HEIGHT]?.toUInt()
            val count = command.integers[OptionNames.COUNT]?.toUInt()
                ?: commandsConfig.defaultCount
            val denoisingStrength = command.numbers[OptionNames.DENOISING_STRENGTH]
                ?: commandsConfig.defaultDenoisingStrength
            val resizeMode = command.integers[OptionNames.RESIZE_MODE]
                ?.let { ResizeMode.fromUInt(it.toUInt()) }
                ?: ResizeMode.fromUInt(commandsConfig.defaultResizeMode)!!
            val seed = command.integers[OptionNames.SEED]?.toUInt()
                ?: Random.nextUInt()
            val checkpointId = command.strings[OptionNames.CHECKPOINT]
                ?: commandsConfig.defaultCheckpoint
            val samplerName = commandsConfig.defaultSampler
            val steps = command.integers[OptionNames.STEPS]?.toUInt()
                ?: commandsConfig.defaultSteps
            val cfg = command.numbers[OptionNames.CFG]
                ?: commandsConfig.defaultCfg

            val controlnet1Attachment = command.attachments[OptionNames.CONTROLNET1_IMAGE]
            val controlnet1Image = controlnet1Attachment?.let {
                if (!it.isImage || it.contentType == null) {
                    throw CommandException("ControlNet 1 image must be an image")
                }
                downloadImageAsBase64(it.url, it.contentType!!)
            }
            val controlnet1Type = command.strings[OptionNames.CONTROLNET1_TYPE]
                ?.let { type -> controlnetTypes.find { it.name == type } }
                ?: controlnetTypes.first()
            val controlnet1Weight = command.numbers[OptionNames.CONTROLNET1_WEIGHT]
                ?: config.controlnet.weight.default

            val controlnet2Attachment = command.attachments[OptionNames.CONTROLNET2_IMAGE]
            val controlnet2Image = controlnet2Attachment?.let {
                if (!it.isImage || it.contentType == null) {
                    throw CommandException("ControlNet 2 image must be an image")
                }
                downloadImageAsBase64(it.url, it.contentType!!)
            }
            val controlnet2Type = command.strings[OptionNames.CONTROLNET2_TYPE]
                ?.let { type -> controlnetTypes.find { it.name == type } }
                ?: controlnetTypes.first()
            val controlnet2Weight = command.numbers[OptionNames.CONTROLNET2_WEIGHT]
                ?: config.controlnet.weight.default

            val displaySource = command.booleans[OptionNames.DISPLAY_SOURCE]
                ?: commandsConfig.displaySourceImageByDefault

            val desiredSize = calculateDesiredImageSize(
                specifiedWidth = width,
                specifiedHeight = height,
                originalWidth = attachment.width?.toUInt(),
                originalHeight = attachment.height?.toUInt(),
                defaultWidth = commandsConfig.defaultWidth,
                defaultHeight = commandsConfig.defaultHeight,
            )
            val size = desiredSize.constrainToPixelSize(commandsConfig.maxPixels)

            val controlnets = buildList {
                if (controlnet1Image != null) {
                    add(ControlnetUnitParams(
                        image = controlnet1Image,
                        type = controlnet1Type,
                        weight = controlnet1Weight,
                    ))
                }
                if (controlnet2Image != null) {
                    add(ControlnetUnitParams(
                        image = controlnet2Image,
                        type = controlnet2Type,
                        weight = controlnet2Weight,
                    ))
                }
            }

            val params = Img2ImgParams(
                image = image,
                prompt = includeText(commandsConfig.alwaysIncludedPrompt, prompt),
                negativePrompt = includeText(commandsConfig.alwaysIncludedNegativePrompt, negativePrompt),
                width = size.width,
                height = size.height,
                count = count,
                denoisingStrength = denoisingStrength,
                resizeMode = resizeMode,
                seed = seed,
                samplerName = samplerName,
                steps = steps,
                cfg = cfg,
                checkpointId = checkpointId,
                controlnets = controlnets,
            )

            val response = client.img2img(params)

            val mainParams = mutableMapOf<String, String>()
            if (prompt != null) mainParams["Prompt"] = prompt
            if (negativePrompt != null) mainParams["Negative prompt"] = negativePrompt
            mainParams["Size"] = "$size"
            mainParams["Seed"] = "$seed"
            val checkpoint = checkpoints.find { it.id == checkpointId }
            if (checkpoint != null) mainParams["Checkpoint"] = checkpoint.name

            val otherParams = mutableMapOf<String, String>()
            otherParams["Steps"] = "$steps"
            otherParams["CFG"] = "$cfg"
            otherParams["Denoising strength"] = "$denoisingStrength"
            otherParams["Resize mode"] = resizeMode.resizeModeText()

            val warnings = mutableListOf<String>()
            if (desiredSize.inPixels > size.inPixels) {
                warnings.add("$desiredSize was reduced to $size due to size constraints.")
            }

            val images = if (count > 1u) {
                // The first image is a grid containing all the generated images
                // We can remove it
                response.images.drop(1)
            } else {
                response.images
            }
            val outputImages = images.take(count.toInt())
            val extraImages = images.drop(count.toInt())
            val controlnetImages = controlnets.mapIndexed { index, net ->
                net to extraImages.getOrNull(index)
            }

            initialResponse.edit {
                embeds?.clear()
                embed {
                    title = "Generation completed."
                    description = mainParams.map { "**${it.key}**: ${it.value}" }.joinToString("\n")
                    if (warnings.isNotEmpty()) {
                        description += "\n\n_Warnings:_\n" + warnings.joinToString("\n") { "- $it" }
                    }
                    footer {
                        text = otherParams.map { "${it.key}: ${it.value}" }.joinToString(", ")
                    }
                    if (displaySource) {
                        thumbnail {
                            url = originalImageUrl
                        }
                    }
                    color = AppColors.success
                }
                outputImages.forEachIndexed { index, img ->
                    addFile("${seed + index.toUInt()}.png", ChannelProvider {
                        ByteReadChannel(Base64.decode(img))
                    })
                }
                controlnetImages.forEachIndexed { index, (net, img) ->
                    val fileName = "controlnet${index + 1}.png"
                    if (img != null) {
                        addFile(fileName, ChannelProvider {
                            ByteReadChannel(Base64.decode(img))
                        })
                    }
                    embed {
                        title = "ControlNet Unit ${index + 1}"
                        description = listOf(
                            "**Type**: ${net.type.name}",
                            "**Weight**: ${net.weight}",
                        ).joinToString("\n")
                        color = AppColors.success
                        if (img != null) {
                            thumbnail {
                                url = "attachment://$fileName"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.logInteractionException(e)
            initialResponse.respondWithException(e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun downloadImageAsBase64(originalImageUrl: String, contentType: String): String {
        val bytes = HttpClient(CIO)
            .get(originalImageUrl)
            .bodyAsChannel()
            .readRemaining()
            .readBytes()
        return "data:$contentType;base64,${Base64.encode(bytes)}"
    }
}

private val CommandsConfig.defaultCheckpoint get() = img2img.defaultCheckpoint ?: global.defaultCheckpoint
private val CommandsConfig.alwaysIncludedPrompt get() = img2img.alwaysIncludedPrompt ?: global.alwaysIncludedPrompt
private val CommandsConfig.alwaysIncludedNegativePrompt get() = img2img.alwaysIncludedNegativePrompt
    ?: global.alwaysIncludedNegativePrompt
private val CommandsConfig.minWidth get() = img2img.width?.min ?: global.width.min
private val CommandsConfig.maxWidth get() = img2img.width?.max ?: global.width.max
private val CommandsConfig.defaultWidth get() = img2img.width?.default ?: global.width.default
private val CommandsConfig.minHeight get() = img2img.height?.min ?: global.height.min
private val CommandsConfig.maxHeight get() = img2img.height?.max ?: global.height.max
private val CommandsConfig.defaultHeight get() = img2img.height?.default ?: global.height.default
private val CommandsConfig.maxPixels get() = img2img.maxPixels ?: global.maxPixels
private val CommandsConfig.minCount get() = img2img.count?.min ?: global.count.min
private val CommandsConfig.maxCount get() = img2img.count?.max ?: global.count.max
private val CommandsConfig.defaultCount get() = img2img.count?.default ?: global.count.default
private val CommandsConfig.minDenoisingStrength get() = img2img.denoisingStrength.min
private val CommandsConfig.maxDenoisingStrength get() = img2img.denoisingStrength.max
private val CommandsConfig.defaultDenoisingStrength get() = img2img.denoisingStrength.default
private val CommandsConfig.defaultResizeMode get() = img2img.defaultResizeMode
private val CommandsConfig.defaultSampler get() = img2img.defaultSampler ?: global.defaultSampler
private val CommandsConfig.minSteps get() = img2img.steps?.min ?: global.steps.min
private val CommandsConfig.maxSteps get() = img2img.steps?.max ?: global.steps.max
private val CommandsConfig.defaultSteps get() = img2img.steps?.default ?: global.steps.default
private val CommandsConfig.minCfg get() = img2img.cfg?.min ?: global.cfg.min
private val CommandsConfig.maxCfg get() = img2img.cfg?.max ?: global.cfg.max
private val CommandsConfig.defaultCfg get() = img2img.cfg?.default ?: global.cfg.default
private val CommandsConfig.displaySourceImageByDefault get() = img2img.displaySourceImageByDefault

