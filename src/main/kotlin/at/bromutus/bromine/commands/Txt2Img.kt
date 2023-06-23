package at.bromutus.bromine.commands

import at.bromutus.bromine.sdclient.SDClient
import at.bromutus.bromine.sdclient.Txt2ImgRequest
import dev.kord.common.Color
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

object Txt2Img {
    const val COMMAND_NAME = "txt2img"

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

    fun GlobalMultiApplicationCommandBuilder.registerTxt2ImgCommand() {
        input(COMMAND_NAME, "Generate an image using the given parameters") {
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
    suspend fun handle(interaction: ChatInputCommandInteraction, sdClient: SDClient) {
        val initialResponse = interaction.respondPublic {
            embed {
                title = "Generating..."
                description = "Depending on the input parameters, this may take a while..."
                color = Color(0x00cc99)
            }
        }

        try {
            val prompt = interaction.command.strings[OptionNames.PROMPT]
            val negativePrompt = interaction.command.strings[OptionNames.NEGATIVE_PROMPT]
            val width = interaction.command.integers[OptionNames.WIDTH]?.toInt()
            val height = interaction.command.integers[OptionNames.HEIGHT]?.toInt()
            val count = interaction.command.integers[OptionNames.COUNT]?.toInt()
            val seed = interaction.command.integers[OptionNames.SEED]?.toInt()
            val steps = interaction.command.integers[OptionNames.STEPS]?.toInt()
            val cfg = interaction.command.numbers[OptionNames.CFG]
            val hiresFactor = interaction.command.numbers[OptionNames.HIRES_FACTOR]
            val hiresSteps =
                interaction.command.integers[OptionNames.HIRES_STEPS]?.toInt()
            val hiresDenoising =
                interaction.command.numbers[OptionNames.HIRES_DENOISING]

            val request = Txt2ImgRequest(
                prompt = prompt,
                negativePrompt = listOfNotNull(
                    Defaults.NEGATIVE_PROMPT,
                    negativePrompt
                ).joinToString(", "),
                width = width ?: Defaults.WIDTH,
                height = height ?: Defaults.HEIGHT,
                nIter = count ?: Defaults.COUNT,
                seed = seed ?: Random.nextInt(from = 0, until = Int.MAX_VALUE),
                samplerName = Defaults.SAMPLER_NAME,
                steps = steps ?: Defaults.STEPS,
                cfgScale = cfg ?: Defaults.CFG,
                enableHr = hiresFactor != null && hiresFactor > 1.0,
                hrScale = hiresFactor,
                hrSecondPassSteps = hiresSteps ?: Defaults.HIRES_STEPS,
                hrUpscaler = Defaults.HIRES_UPSCALER,
                denoisingStrength = hiresDenoising ?: Defaults.HIRES_DENOISING,
                overrideSettings = JsonObject(mapOf("sd_model_checkpoint" to JsonPrimitive(Defaults.CHECKPOINT_NAME))),
                saveImages = true,
            )

            val response = sdClient.txt2img(request)

            if (response.images.isEmpty()) {
                throw Txt2ImgException(response.error)
            }
            val mainParams = mutableMapOf<String, String>()
            mainParams["Prompt"] = prompt ?: ""
            if (negativePrompt != null) mainParams["Negative prompt"] = negativePrompt
            mainParams["Size"] = "${request.width}x${request.height}"
            mainParams["Seed"] = "${request.seed}"
            val otherParams = mutableMapOf<String, String>()
            otherParams["Steps"] = "${request.steps}"
            otherParams["CFG"] = "${request.cfgScale}"
            if (hiresFactor != null) {
                otherParams["Hires factor"] = "$hiresFactor"
                otherParams["Hires steps"] = "${hiresSteps ?: request.steps}"
                otherParams["Hires denoising"] = "${request.denoisingStrength}"
            }
            val hasMultipleImages = response.images.size > 1
            initialResponse.edit {
                embeds?.clear()
                embed {
                    title = "Generation completed."
                    description = mainParams.map { "**${it.key}**: ${it.value}" }.joinToString("\n")
                    footer {
                        text = otherParams.map { "${it.key}: ${it.value}" }.joinToString(", ")
                    }
                    color = Color(0x33ee33)
                }
                response.images
                    .drop(if (hasMultipleImages) 1 else 0)
                    .forEachIndexed { index, img ->
                        addFile("image$index.png", ChannelProvider {
                            ByteReadChannel(Base64.decode(img))
                        })
                    }
            }
        } catch (e: Exception) {
            initialResponse.edit {
                embeds?.clear()
                embed {
                    title = "Something went wrong."
                    description = if (e is Txt2ImgException) {
                        println("Error: ${e.message} (${e.details ?: "No details"})")
                        when (e.message) {
                            "OutOfMemoryError" -> "Out of memory. Please reduce the size of the requested image."
                            null -> "Unknown error."
                            else -> e.message
                        }
                    } else {
                        e.printStackTrace()
                        "Unknown error."
                    }
                    color = Color(0xff0000)
                }
            }
        }
    }
}

class Txt2ImgException(message: String? = null, cause: Throwable? = null, val details: String? = null) : Exception(message, cause)
