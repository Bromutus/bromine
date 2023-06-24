package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.Txt2ImgCommandConfig
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
import kotlin.random.nextUInt

private val logger = KotlinLogging.logger {}

class Txt2Img(
    private val client: SDClient,
    private val config: Txt2ImgCommandConfig,
) : ChatInputCommand {
    override val name = "txt2img"
    override val description = "Generate an image from text"

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
                    Default: ${config.width.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.width.min.toLong()
                maxValue = config.width.max.toLong()
            }
            integer(
                name = OptionNames.HEIGHT,
                description = """
                    Height of the generated image in pixels (before hires-fix).
                    Default: ${config.height.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.height.min.toLong()
                maxValue = config.height.max.toLong()
            }
            integer(
                name = OptionNames.COUNT,
                description = """
                    Number of images to generate.
                    Default: ${config.count.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.count.min.toLong()
                maxValue = config.count.max.toLong()
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
                    Default: ${config.steps.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.steps.min.toLong()
                maxValue = config.steps.max.toLong()
            }
            number(
                name = OptionNames.CFG,
                description = """
                    Classifier-free guidance.
                    High values increase guidance, but may lead to artifacts.
                    Default: ${config.cfg.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.cfg.min
                maxValue = config.cfg.max
            }
            number(
                name = OptionNames.HIRES_FACTOR,
                description = """
                    If set, upscale by this factor using hires-fix.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.hiresFactor.min
                maxValue = config.hiresFactor.max
            }
            integer(
                name = OptionNames.HIRES_STEPS,
                description = """
                    Number of diffusion steps for hires-fix (0 = same as steps).
                    Default: ${config.hiresSteps.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.hiresSteps.min.toLong()
                maxValue = config.hiresSteps.max.toLong()
            }
            number(
                name = OptionNames.HIRES_DENOISING,
                description = """
                    Denoising strength for hires-fix.
                    Default: ${config.hiresDenoising.default}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = config.hiresDenoising.min
                maxValue = config.hiresDenoising.max
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
            val negativePrompt = command.strings[OptionNames.NEGATIVE_PROMPT]
            val width = command.integers[OptionNames.WIDTH]?.toUInt()
                ?: config.width.default
            val height = command.integers[OptionNames.HEIGHT]?.toUInt()
                ?: config.height.default
            val count = command.integers[OptionNames.COUNT]?.toUInt()
                ?: config.count.default
            val seed = command.integers[OptionNames.SEED]?.toUInt()
                ?: Random.nextUInt()
            val samplerName = config.samplerDefault
            val steps = command.integers[OptionNames.STEPS]?.toUInt()
                ?: config.steps.default
            val cfg = command.numbers[OptionNames.CFG]
                ?: config.cfg.default
            val hiresFactor = command.numbers[OptionNames.HIRES_FACTOR]
                ?: config.hiresFactor.default
            val hiresUpscaler = config.hiresUpscalerDefault
            val hiresSteps = command.integers[OptionNames.HIRES_STEPS]?.toUInt()
                ?: config.hiresSteps.default
            val hiresDenoising = command.numbers[OptionNames.HIRES_DENOISING]
                ?: config.hiresDenoising.default
            val checkpointName = config.defaultCheckpoint

            val params = Txt2ImgParams(
                prompt = listOfNotNull(config.promptAlwaysInclude, prompt)
                    .joinToString(", "),
                negativePrompt = listOfNotNull(config.negativePromptAlwaysInclude, negativePrompt)
                    .joinToString(", "),
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
            if (prompt != null) mainParams["Prompt"] = prompt
            if (negativePrompt != null) mainParams["Negative prompt"] = negativePrompt
            mainParams["Size"] = "${width}x${height}"
            mainParams["Seed"] = "$seed"

            val otherParams = mutableMapOf<String, String>()
            otherParams["Steps"] = "$steps"
            otherParams["CFG"] = "$cfg"
            if (hiresFactor > 1.0) {
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
}

