package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.CommandsConfig
import at.bromutus.bromine.errors.CommandException
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
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

class Img2Img(
    private val client: SDClient,
    private val config: CommandsConfig,
) : ChatInputCommand {
    override val name = "img2img"
    override val description = "Generate an image from another image"

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
        const val STEPS = "steps"
        const val CFG = "cfg"
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
                    Default: ${config.defaultWidth}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.minWidth.toLong()
                maxValue = config.maxWidth.toLong()
            }
            integer(
                name = OptionNames.HEIGHT,
                description = """
                    Height of the generated image in pixels (0 = same as original).
                    Default: ${config.defaultHeight}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.minHeight.toLong()
                maxValue = config.maxHeight.toLong()
            }
            integer(
                name = OptionNames.COUNT,
                description = """
                    Number of images to generate.
                    Default: ${config.defaultCount}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.minCount.toLong()
                maxValue = config.maxCount.toLong()
            }
            number(
                name = OptionNames.DENOISING_STRENGTH,
                description = """
                    Denoising strength. Lower values preserve more of the original image.
                    Default: ${config.defaultDenoisingStrength}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.minDenoisingStrength
                maxValue = config.maxDenoisingStrength
            }
            integer(
                name = OptionNames.RESIZE_MODE,
                description = """
                    Resize mode.
                    Default: ${ResizeMode.fromUInt(config.defaultResizeMode)!!.resizeModeText()}.
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
            integer(
                name = OptionNames.STEPS,
                description = """
                    Number of diffusion steps.
                    Default: ${config.defaultSteps}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.minSteps.toLong()
                maxValue = config.maxSteps.toLong()
            }
            number(
                name = OptionNames.CFG,
                description = """
                    Classifier-free guidance.
                    High values increase guidance, but may lead to artifacts.
                    Default: ${config.defaultCfg}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.minCfg
                maxValue = config.maxCfg
            }
            boolean(
                name = OptionNames.DISPLAY_SOURCE,
                description = """
                    Whether to display the source image as thumbnail in the output.
                    Default: ${config.displaySourceImageByDefault}.
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
                ?: config.defaultCount
            val denoisingStrength = command.numbers[OptionNames.DENOISING_STRENGTH]
                ?: config.defaultDenoisingStrength
            val resizeMode = command.integers[OptionNames.RESIZE_MODE]
                ?.let { ResizeMode.fromUInt(it.toUInt()) }
                ?: ResizeMode.fromUInt(config.defaultResizeMode)!!
            val seed = command.integers[OptionNames.SEED]?.toUInt()
                ?: Random.nextUInt()
            val samplerName = config.defaultSampler
            val steps = command.integers[OptionNames.STEPS]?.toUInt()
                ?: config.defaultSteps
            val cfg = command.numbers[OptionNames.CFG]
                ?: config.defaultCfg
            val checkpointName = config.defaultCheckpoint

            val displaySource = command.booleans[OptionNames.DISPLAY_SOURCE]
                ?: config.displaySourceImageByDefault

            val desiredSize = calculateDesiredImageSize(
                specifiedWidth = width,
                specifiedHeight = height,
                originalWidth = attachment.width?.toUInt(),
                originalHeight = attachment.height?.toUInt(),
                defaultWidth = config.defaultWidth,
                defaultHeight = config.defaultHeight,
            )
            val size = desiredSize.constrainToPixelSize(config.maxPixels)

            val params = Img2ImgParams(
                image = image,
                prompt = includeText(config.alwaysIncludedPrompt, prompt),
                negativePrompt = includeText(config.alwaysIncludedNegativePrompt, negativePrompt),
                width = size.width,
                height = size.height,
                count = count,
                denoisingStrength = denoisingStrength,
                resizeMode = resizeMode,
                seed = seed,
                samplerName = samplerName,
                steps = steps,
                cfg = cfg,
                checkpointName = checkpointName,
            )

            val response = client.img2img(params)

            val mainParams = mutableMapOf<String, String>()
            if (prompt != null) mainParams["Prompt"] = prompt
            if (negativePrompt != null) mainParams["Negative prompt"] = negativePrompt
            mainParams["Size"] = "$size"
            mainParams["Seed"] = "$seed"

            val otherParams = mutableMapOf<String, String>()
            otherParams["Steps"] = "$steps"
            otherParams["CFG"] = "$cfg"
            otherParams["Denoising strength"] = "$denoisingStrength"
            otherParams["Resize mode"] = resizeMode.resizeModeText()

            val warnings = mutableListOf<String>()
            if (desiredSize.inPixels > size.inPixels) {
                warnings.add("$desiredSize was reduced to $size due to size constraints.")
            }

            val images = if (response.images.size > 1) {
                // The first image is a grid containing all the generated images
                // We can remove it
                response.images.drop(1)
            } else {
                response.images
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
                images.forEachIndexed { index, img ->
                    addFile("${seed + index.toUInt()}.png", ChannelProvider {
                        ByteReadChannel(Base64.decode(img))
                    })
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

