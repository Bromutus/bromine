package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.SDClient
import at.bromutus.bromine.sdclient.Txt2ImgParams
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class Txt2Img(
    private val client: SDClient,
) : ChatInputCommand {
    override val name = "txt2img"
    override val description = "Generate an image using the given parameters"

    private object OptionNames {
        const val PROMPT = "prompt"
        const val NEGATIVE_PROMPT = "negative-prompt"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val COUNT = "count"
        const val SEED = "seed"
        const val STEPS = "steps"
        const val CFG = "cfg"
        const val HIRES_FACTOR = "hires-factor"
        const val HIRES_STEPS = "hires-steps"
        const val HIRES_DENOISING = "hires-denoising"
    }

    private object Defaults {
        const val CHECKPOINT_NAME = "bd2150_calico25_03"
        const val NEGATIVE_PROMPT = "bad-picture-chill-75v, easynegative"
        const val WIDTH = 512
        const val HEIGHT = 512
        const val COUNT = 1
        const val SAMPLER_NAME = "DPM++ SDE Karras"
        const val STEPS = 20
        const val CFG = 7.0
        const val HIRES_DENOISING = 0.7
        const val HIRES_STEPS = 0
        const val HIRES_UPSCALER = "Latent"
    }

    override suspend fun buildCommand(builder: ChatInputCreateBuilder) {
        builder.apply {
            string(
                name = OptionNames.PROMPT,
                description = """
                    List of keywords or descriptions, comma-separated.
                    Example: "ocean, (boat:1.1), multiple girls".
                    """.trimIndent().replace("\n", " ")
            ) {
                required = true
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
                    Width of the generated image in pixels (before hires-fix).
                    Default: 512.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 32
                maxValue = 4096
            }
            integer(
                name = OptionNames.HEIGHT,
                description = """
                    Height of the generated image in pixels (before hires-fix).
                    Default: 512.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 32
                maxValue = 4096
            }
            integer(
                name = OptionNames.COUNT,
                description = """
                    Number of images to generate.
                    Default: 1.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1
                maxValue = 9
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
                    Default: 20.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1
                maxValue = 150
            }
            number(
                name = OptionNames.CFG,
                description = """
                    Classifier-free guidance.
                    High values increase guidance, but may lead to artifacts.
                    Default: 7.0.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1.0
                maxValue = 30.0
            }
            number(
                name = OptionNames.HIRES_FACTOR,
                description = """
                    If set, upscale by this factor using hires-fix.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 1.0
                maxValue = 4.0
            }
            integer(
                name = OptionNames.HIRES_STEPS,
                description = """
                    Number of diffusion steps for hires-fix (0 = same as steps).
                    Default: 0.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 0
                maxValue = 150
            }
            number(
                name = OptionNames.HIRES_DENOISING,
                description = """
                    Denoising strength for hires-fix.
                    Default: 0.7.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = 0.0
                maxValue = 1.0
            }
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
            val prompt = command.strings[OptionNames.PROMPT]
                ?: throw Exception("No prompt specified")
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
            val seed = command.integers[OptionNames.SEED]?.toInt()
                ?: Random.nextInt(from = 0, until = Int.MAX_VALUE)
            val samplerName = Defaults.SAMPLER_NAME
            val steps = command.integers[OptionNames.STEPS]?.toInt()
                ?: Defaults.STEPS
            val cfg = command.numbers[OptionNames.CFG]
                ?: Defaults.CFG
            val hiresFactor = command.numbers[OptionNames.HIRES_FACTOR]
            val hiresUpscaler = Defaults.HIRES_UPSCALER
            val hiresSteps = command.integers[OptionNames.HIRES_STEPS]?.toInt()
                ?: Defaults.HIRES_STEPS
            val hiresDenoising = command.numbers[OptionNames.HIRES_DENOISING]
                ?: Defaults.HIRES_DENOISING
            val checkpointName = Defaults.CHECKPOINT_NAME

            val params = Txt2ImgParams(
                prompt = prompt,
                negativePrompt = actualNegativePrompt,
                width = width,
                height = height,
                count = count,
                seed = seed,
                samplerName = samplerName,
                steps = steps,
                cfg = cfg,
                hiresFactor = hiresFactor,
                hiresSteps = hiresSteps,
                hiresUpscaler = hiresUpscaler,
                hiresDenoising = hiresDenoising,
                checkpointName = checkpointName,
            )

            val response = client.txt2img(params)

            val mainParams = mutableMapOf<String, String>()
            mainParams["Prompt"] = prompt
            if (negativePrompt != null) mainParams["Negative prompt"] = negativePrompt
            mainParams["Size"] = "${width}x${height}"
            mainParams["Seed"] = "$seed"

            val otherParams = mutableMapOf<String, String>()
            otherParams["Steps"] = "$steps"
            otherParams["CFG"] = "$cfg"
            if (hiresFactor != null) {
                otherParams["Hires factor"] = "$hiresFactor"
                otherParams["Hires steps"] = "$hiresSteps"
                otherParams["Hires denoising"] = "$hiresDenoising"
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
                    footer {
                        text = otherParams.map { "${it.key}: ${it.value}" }.joinToString(", ")
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
}

