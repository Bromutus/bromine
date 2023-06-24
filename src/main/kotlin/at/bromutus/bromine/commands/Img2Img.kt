package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.Img2ImgParams
import at.bromutus.bromine.sdclient.ResizeMode
import at.bromutus.bromine.sdclient.SDClient
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

private val logger = KotlinLogging.logger {}

class Img2Img(
    private val client: SDClient,
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

    private object Defaults {
        const val CHECKPOINT_NAME = "bd2150_calico25_03"
        const val NEGATIVE_PROMPT = "bad-picture-chill-75v, easynegative"
        const val WIDTH = 512
        const val HEIGHT = 512
        const val COUNT = 1
        const val DENOISING_STRENGTH = 0.6
        val RESIZE_MODE = ResizeMode.Crop
        const val SAMPLER_NAME = "DPM++ SDE Karras"
        const val STEPS = 20
        const val CFG = 7.0
        const val DISPLAY_SOURCE = true
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
                    Width of the generated image in pixels.
                    Default: ${Defaults.WIDTH}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1
                maxValue = 4096
            }
            integer(
                name = OptionNames.HEIGHT,
                description = """
                    Height of the generated image in pixels.
                    Default: ${Defaults.HEIGHT}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1
                maxValue = 4096
            }
            integer(
                name = OptionNames.COUNT,
                description = """
                    Number of images to generate.
                    Default: ${Defaults.COUNT}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1
                maxValue = 9
            }
            number(
                name = OptionNames.DENOISING_STRENGTH,
                description = """
                    Denoising strength. Lower values preserve more of the original image.
                    Default: ${Defaults.DENOISING_STRENGTH}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 0.0
                maxValue = 1.0
            }
            integer(
                name = OptionNames.RESIZE_MODE,
                description = """
                    Resize mode.
                    Default: ${Defaults.RESIZE_MODE.resizeModeText()}.
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
                    Default: ${Defaults.STEPS}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1
                maxValue = 30
            }
            number(
                name = OptionNames.CFG,
                description = """
                    Classifier-free guidance.
                    High values increase guidance, but may lead to artifacts.
                    Default: ${Defaults.CFG}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1.0
                maxValue = 30.0
            }
            boolean(
                name = OptionNames.DISPLAY_SOURCE,
                description = """
                    Whether to display the source image as thumbnail in the output.
                    Default: ${Defaults.DISPLAY_SOURCE}.
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

            val attachment = command.attachments[OptionNames.IMAGE]
                ?: throw Exception("No image specified")
            if (!attachment.isImage || attachment.contentType == null) {
                throw Exception("Image must be an image")
            }
            val originalImageUrl = attachment.url
            val image = downloadImageAsBase64(originalImageUrl, attachment.contentType!!)

            val prompt = command.strings[OptionNames.PROMPT]
            val negativePrompt = command.strings[OptionNames.NEGATIVE_PROMPT]
            val actualNegativePrompt = if (negativePrompt != null) {
                "${Defaults.NEGATIVE_PROMPT}, $negativePrompt"
            } else {
                Defaults.NEGATIVE_PROMPT
            }
            val width = command.integers[OptionNames.WIDTH]?.toInt()
                ?: Defaults.WIDTH
            val height = command.integers[OptionNames.HEIGHT]?.toInt()
                ?: Defaults.HEIGHT
            val count = command.integers[OptionNames.COUNT]?.toInt()
                ?: Defaults.COUNT
            val denoisingStrength = command.numbers[OptionNames.DENOISING_STRENGTH]
                ?: Defaults.DENOISING_STRENGTH
            val resizeMode = command.integers[OptionNames.RESIZE_MODE]
                ?.let { ResizeMode.fromInt(it.toInt()) }
                ?: Defaults.RESIZE_MODE
            val seed = command.integers[OptionNames.SEED]?.toInt()
                ?: Random.nextInt(from = 0, until = Int.MAX_VALUE)
            val samplerName = Defaults.SAMPLER_NAME
            val steps = command.integers[OptionNames.STEPS]?.toInt()
                ?: Defaults.STEPS
            val cfg = command.numbers[OptionNames.CFG]
                ?: Defaults.CFG
            val checkpointName = Defaults.CHECKPOINT_NAME

            val displaySource = command.booleans[OptionNames.DISPLAY_SOURCE]
                ?: Defaults.DISPLAY_SOURCE

            val params = Img2ImgParams(
                image = image,
                prompt = prompt,
                negativePrompt = actualNegativePrompt,
                width = width,
                height = height,
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
            mainParams["Size"] = "${width}x${height}"
            mainParams["Seed"] = "$seed"

            val otherParams = mutableMapOf<String, String>()
            otherParams["Steps"] = "$steps"
            otherParams["CFG"] = "$cfg"
            otherParams["Denoising strength"] = "$denoisingStrength"
            otherParams["Resize mode"] = resizeMode.resizeModeText()

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
                    addFile("${seed + index}.png", ChannelProvider {
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