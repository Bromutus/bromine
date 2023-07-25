package at.bromutus.bromine.commands

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.AppConfig
import at.bromutus.bromine.CommandsConfig
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.errors.respondWithException
import at.bromutus.bromine.sdclient.SDClient
import at.bromutus.bromine.sdclient.Txt2ImgParams
import at.bromutus.bromine.utils.calculateDesiredImageSize
import at.bromutus.bromine.utils.constrainToPixelSize
import at.bromutus.bromine.utils.includeText
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

class Txt2ImgCommand(
    private val client: SDClient,
    private val config: AppConfig,
) : ChatInputCommand {
    override val name = "txt2img"
    override val description = "Generate an image from text"

    private val commandsConfig get() = config.commands
    private val checkpoints get() = config.checkpoints.installed

    private object OptionNames {
        const val PROMPT = "prompt"
        const val NEGATIVE_PROMPT = "negative-prompt"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val COUNT = "count"
        const val SEED = "seed"
        const val CHECKPOINT = "checkpoint"
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
                    Height of the generated image in pixels (before hires-fix).
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
            number(
                name = OptionNames.HIRES_FACTOR,
                description = """
                    If set, upscale by this factor using hires-fix.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minHiresFactor
                maxValue = commandsConfig.maxHiresFactor
            }
            integer(
                name = OptionNames.HIRES_STEPS,
                description = """
                    Number of diffusion steps for hires-fix (0 = same as steps).
                    Default: ${commandsConfig.defaultHiresSteps}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minHiresSteps.toLong()
                maxValue = commandsConfig.maxHiresSteps.toLong()
            }
            number(
                name = OptionNames.HIRES_DENOISING,
                description = """
                    Denoising strength for hires-fix.
                    Default: ${commandsConfig.defaultHiresDenoising}.
                    """.trimIndent().replace("\n", " ")
            ) {
                required = false
                minValue = commandsConfig.minHiresDenoising
                maxValue = commandsConfig.maxHiresDenoising
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
            val height = command.integers[OptionNames.HEIGHT]?.toUInt()
            val count = command.integers[OptionNames.COUNT]?.toUInt()
                ?: commandsConfig.defaultCount
            val seed = command.integers[OptionNames.SEED]?.toUInt()
                ?: Random.nextUInt()
            val checkpointId = command.strings[OptionNames.CHECKPOINT]
                ?: commandsConfig.defaultCheckpoint
            val samplerName = commandsConfig.defaultSampler
            val steps = command.integers[OptionNames.STEPS]?.toUInt()
                ?: commandsConfig.defaultSteps
            val cfg = command.numbers[OptionNames.CFG]
                ?: commandsConfig.defaultCfg
            val hiresFactor = command.numbers[OptionNames.HIRES_FACTOR]
                ?: commandsConfig.defaultHiresFactor
            val hiresUpscaler = commandsConfig.hiresUpscaler
            val hiresSteps = command.integers[OptionNames.HIRES_STEPS]?.toUInt()
                ?: commandsConfig.defaultHiresSteps
            val hiresDenoising = command.numbers[OptionNames.HIRES_DENOISING]
                ?: commandsConfig.defaultHiresDenoising

            val desiredSize = calculateDesiredImageSize(
                specifiedWidth = width,
                specifiedHeight = height,
                defaultWidth = commandsConfig.defaultWidth,
                defaultHeight = commandsConfig.defaultHeight,
            )
            val size = desiredSize.constrainToPixelSize(this.commandsConfig.maxPixels)

            val isHiresFixDesired = hiresFactor > 1.0
            val scaledSize = size * hiresFactor
            val doHiresFix = isHiresFixDesired && scaledSize.inPixels <= this.commandsConfig.maxPixels

            val params = Txt2ImgParams(
                prompt = includeText(this.commandsConfig.alwaysIncludedPrompt, prompt),
                negativePrompt = includeText(this.commandsConfig.alwaysIncludedNegativePrompt, negativePrompt),
                width = size.width,
                height = size.height,
                count = count,
                seed = seed,
                samplerName = samplerName,
                steps = steps,
                cfg = cfg,
                hiresFactor = if (doHiresFix) hiresFactor else null,
                hiresSteps = hiresSteps,
                hiresUpscaler = hiresUpscaler,
                hiresDenoising = hiresDenoising,
                checkpointId = checkpointId,
            )

            val response = client.txt2img(params)

            val mainParams = mutableMapOf<String, String>()
            if (prompt != null) mainParams["Prompt"] = prompt
            if (negativePrompt != null) mainParams["Negative prompt"] = negativePrompt
            mainParams["Size"] = if (doHiresFix) "$scaledSize (scaled up from $size)" else "$scaledSize"
            mainParams["Seed"] = "$seed"

            val otherParams = mutableMapOf<String, String>()
            val checkpoint = checkpoints.find { it.id == checkpointId }
            if (checkpoint != null) otherParams["Checkpoint"] = checkpoint.name
            otherParams["Steps"] = "$steps"
            otherParams["CFG"] = "$cfg"
            if (doHiresFix) {
                otherParams["Hires factor"] = "$hiresFactor"
                otherParams["Hires steps"] = "$hiresSteps"
                otherParams["Hires denoising"] = "$hiresDenoising"
            }

            val warnings = mutableListOf<String>()
            if (isHiresFixDesired && !doHiresFix) {
                warnings.add("Hires-fix was ignored due to size constraints.")
            }
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

private val CommandsConfig.defaultCheckpoint get() = txt2img.defaultCheckpoint ?: global.defaultCheckpoint
private val CommandsConfig.alwaysIncludedPrompt get() = txt2img.alwaysIncludedPrompt ?: global.alwaysIncludedPrompt
private val CommandsConfig.alwaysIncludedNegativePrompt get() = txt2img.alwaysIncludedNegativePrompt
    ?: global.alwaysIncludedNegativePrompt
private val CommandsConfig.minWidth get() = txt2img.width?.min ?: global.width.min
private val CommandsConfig.maxWidth get() = txt2img.width?.max ?: global.width.max
private val CommandsConfig.defaultWidth get() = txt2img.width?.default ?: global.width.default
private val CommandsConfig.minHeight get() = txt2img.height?.min ?: global.height.min
private val CommandsConfig.maxHeight get() = txt2img.height?.max ?: global.height.max
private val CommandsConfig.defaultHeight get() = txt2img.height?.default ?: global.height.default
private val CommandsConfig.maxPixels get() = txt2img.maxPixels ?: global.maxPixels
private val CommandsConfig.minCount get() = txt2img.count?.min ?: global.count.min
private val CommandsConfig.maxCount get() = txt2img.count?.max ?: global.count.max
private val CommandsConfig.defaultCount get() = txt2img.count?.default ?: global.count.default
private val CommandsConfig.defaultSampler get() = txt2img.defaultSampler ?: global.defaultSampler
private val CommandsConfig.minSteps get() = txt2img.steps?.min ?: global.steps.min
private val CommandsConfig.maxSteps get() = txt2img.steps?.max ?: global.steps.max
private val CommandsConfig.defaultSteps get() = txt2img.steps?.default ?: global.steps.default
private val CommandsConfig.minCfg get() = txt2img.cfg?.min ?: global.cfg.min
private val CommandsConfig.maxCfg get() = txt2img.cfg?.max ?: global.cfg.max
private val CommandsConfig.defaultCfg get() = txt2img.cfg?.default ?: global.cfg.default
private val CommandsConfig.minHiresFactor get() = txt2img.hiresFactor.min
private val CommandsConfig.maxHiresFactor get() = txt2img.hiresFactor.max
private val CommandsConfig.defaultHiresFactor get() = txt2img.hiresFactor.default
private val CommandsConfig.minHiresSteps get() = txt2img.hiresSteps.min
private val CommandsConfig.maxHiresSteps get() = txt2img.hiresSteps.max
private val CommandsConfig.defaultHiresSteps get() = txt2img.hiresSteps.default
private val CommandsConfig.hiresUpscaler get() = txt2img.hiresUpscaler
private val CommandsConfig.minHiresDenoising get() = txt2img.hiresDenoising.min
private val CommandsConfig.maxHiresDenoising get() = txt2img.hiresDenoising.max
private val CommandsConfig.defaultHiresDenoising get() = txt2img.hiresDenoising.default

