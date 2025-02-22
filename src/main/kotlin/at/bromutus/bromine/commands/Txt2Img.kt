package at.bromutus.bromine.commands

import at.bromutus.bromine.appdata.AppConfig
import at.bromutus.bromine.appdata.CommandsConfig
import at.bromutus.bromine.appdata.readUserPreferences
import at.bromutus.bromine.errors.CommandException
import at.bromutus.bromine.errors.logInteractionException
import at.bromutus.bromine.sdclient.ControlnetUnitParams
import at.bromutus.bromine.sdclient.HiresParams
import at.bromutus.bromine.sdclient.SDClient
import at.bromutus.bromine.sdclient.Txt2ImgParams
import at.bromutus.bromine.tgclient.TGClient
import at.bromutus.bromine.utils.*
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.interaction.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.absoluteValue
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class Txt2ImgCommand(
    private val client: SDClient,
    private val config: AppConfig,
    private val tgClient: TGClient?,
    private val queueInfo: ExecutionQueueInfo,
) : ChatInputCommand {
    override val name = "txt2img"
    override val description = "Generate an image from text"

    private val commandsConfig get() = config.commands
    private val checkpoints get() = config.checkpoints.installed
    private val controlnetTypes get() = config.controlnet.installed

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
        const val ENABLE_ADETAILER = "enable-adetailer"
        const val HIRES_FACTOR = "hires-factor"
        const val HIRES_STEPS = "hires-steps"
        const val HIRES_DENOISING = "hires-denoising"
        const val CONTROLNET1_IMAGE = "controlnet1-image"
        const val CONTROLNET1_TYPE = "controlnet1-type"
        const val CONTROLNET1_WEIGHT = "controlnet1-weight"
        const val CONTROLNET2_IMAGE = "controlnet2-image"
        const val CONTROLNET2_TYPE = "controlnet2-type"
        const val CONTROLNET2_WEIGHT = "controlnet2-weight"
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
            boolean(
                name = OptionNames.ENABLE_ADETAILER,
                description = """
                    Enable ADetailer. Adds more details to hands and faces.
                    Default: ${commandsConfig.defaultEnableADetailer}.
                    """.trimIndent().replace("\n", " ")
            )
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
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun handleInteraction(interaction: ChatInputCommandInteraction) {
        val deferredResponse = interaction.deferPublicResponse()

        try {
            val command = interaction.command

            val pControlnet1Attachment = command.attachments[OptionNames.CONTROLNET1_IMAGE]
            val pControlnet1Image = pControlnet1Attachment?.let {
                if (!it.isImage || it.contentType == null) {
                    throw CommandException("ControlNet 1 image must be an image")
                }
                downloadImageAsBase64(it.url, it.contentType!!)
            }
            val pControlnet2Attachment = command.attachments[OptionNames.CONTROLNET2_IMAGE]
            val pControlnet2Image = pControlnet2Attachment?.let {
                if (!it.isImage || it.contentType == null) {
                    throw CommandException("ControlNet 2 image must be an image")
                }
                downloadImageAsBase64(it.url, it.contentType!!)
            }

            generate(
                user = interaction.user,
                pPrompt = command.strings[OptionNames.PROMPT],
                pNegativePrompt = command.strings[OptionNames.NEGATIVE_PROMPT],
                pWidth = command.integers[OptionNames.WIDTH]?.toInt(),
                pHeight = command.integers[OptionNames.HEIGHT]?.toInt(),
                pCount = command.integers[OptionNames.COUNT]?.toInt(),
                pSeed = command.integers[OptionNames.SEED]?.toInt(),
                pCheckpoint = command.strings[OptionNames.CHECKPOINT],
                pSteps = command.integers[OptionNames.STEPS]?.toInt(),
                pCfg = command.numbers[OptionNames.CFG],
                pHiresFactor = command.numbers[OptionNames.HIRES_FACTOR],
                pHiresSteps = command.integers[OptionNames.HIRES_STEPS]?.toInt(),
                pHiresDenoising = command.numbers[OptionNames.HIRES_DENOISING],
                pControlnet1Type = command.strings[OptionNames.CONTROLNET1_TYPE],
                pControlnet1Image = pControlnet1Image,
                pControlnet1Weight = command.numbers[OptionNames.CONTROLNET1_WEIGHT],
                pControlnet2Type = command.strings[OptionNames.CONTROLNET2_TYPE],
                pControlnet2Image = pControlnet2Image,
                pControlnet2Weight = command.numbers[OptionNames.CONTROLNET2_WEIGHT],
                setInitialMessage = { mainParams, otherParams, warnings, controlnetImages ->
                    deferredResponse.respond {
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
                            warnings = warnings
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
            logger.logInteractionException(e)
            deferredResponse.respond {
                generationFailureEmbed(
                    warnings = listOf(e.message ?: "Unknown error")
                )
            }
        }
    }

    suspend fun <M : Any> generate(
        user: User,
        setInitialMessage: suspend (
            mainParams: Map<String, String>,
            otherParams: Map<String, String>,
            warnings: List<String>,
            controlnetImages: List<Pair<ControlnetUnitParams, String>>,
        ) -> M,
        onProgress: suspend M.(
            index: Int,
            mainParams: Map<String, String>,
            otherParams: Map<String, String>,
            warnings: List<String>,
            controlnetImages: List<Pair<ControlnetUnitParams, String>>,
        ) -> Unit,
        onSuccess: suspend M.(
            outputImages: List<String>,
            seed: Int,
            mainParams: Map<String, String>,
            otherParams: Map<String, String>,
            warnings: List<String>,
            controlnetImages: List<Pair<ControlnetUnitParams, String?>>,
        ) -> Unit,
        onError: suspend M.(
            e: Exception,
            mainParams: Map<String, String>,
            otherParams: Map<String, String>,
            warnings: List<String>,
            controlnetImages: List<Pair<ControlnetUnitParams, String>>,
        ) -> Unit,
        pPrompt: String? = null,
        pNegativePrompt: String? = null,
        pWidth: Int? = null,
        pHeight: Int? = null,
        pCount: Int? = null,
        pSeed: Int? = null,
        pCheckpoint: String? = null,
        pSteps: Int? = null,
        pCfg: Double? = null,
        pEnableADetailer: Boolean? = null,
        pHiresFactor: Double? = null,
        pHiresSteps: Int? = null,
        pHiresDenoising: Double? = null,
        pControlnet1Image: String? = null,
        pControlnet1Type: String? = null,
        pControlnet1Weight: Double? = null,
        pControlnet2Image: String? = null,
        pControlnet2Type: String? = null,
        pControlnet2Weight: Double? = null,
    ) {
        val preferences by lazy { readUserPreferences(user.tag) }

        val prompt = includeText(preferences.promptPrefix, pPrompt)
        val negativePrompt = includeText(preferences.negativePromptPrefix, pNegativePrompt)
        val width = pWidth
            ?: preferences.width
        val height = pHeight
            ?: preferences.height
        val count = pCount
            ?: preferences.count
            ?: commandsConfig.defaultCount
        val seed = pSeed?.absoluteValue
            ?: Random.nextInt(0, Int.MAX_VALUE)
        val checkpointId = pCheckpoint
            ?: preferences.checkpoint?.takeIf { id -> checkpoints.any { it.id == id } }
            ?: commandsConfig.defaultCheckpoint
        val samplerName = commandsConfig.defaultSampler
        val steps = pSteps
            ?: preferences.steps
            ?: commandsConfig.defaultSteps
        val cfg = pCfg
            ?: preferences.cfg
            ?: commandsConfig.defaultCfg
        val enableADetailer = pEnableADetailer
            ?: preferences.enableADetailer
            ?: commandsConfig.defaultEnableADetailer
        val hiresFactor = pHiresFactor
            ?: commandsConfig.defaultHiresFactor
        val hiresUpscaler = commandsConfig.hiresUpscaler
        val hiresSteps = pHiresSteps
            ?: commandsConfig.defaultHiresSteps
        val hiresDenoising = pHiresDenoising
            ?: commandsConfig.defaultHiresDenoising
        val controlnet1Type = pControlnet1Type
            ?.let { type -> controlnetTypes.find { it.name == type } }
            ?: controlnetTypes.first()
        val controlnet1Weight = pControlnet1Weight
            ?: config.controlnet.weight.default
        val controlnet2Type = pControlnet2Type
            ?.let { type -> controlnetTypes.find { it.name == type } }
            ?: controlnetTypes.first()
        val controlnet2Weight = pControlnet2Weight
            ?: config.controlnet.weight.default

        val desiredSize = calculateDesiredImageSize(
            specifiedWidth = width,
            specifiedHeight = height,
            defaultWidth = commandsConfig.defaultWidth,
            defaultHeight = commandsConfig.defaultHeight,
        )
        val size = this.commandsConfig.maxPixels?.let { desiredSize.constrainToPixelSize(it) } ?: desiredSize

        val isHiresFixDesired = hiresFactor > 1.0
        val scaledSize = size * hiresFactor
        val doHiresFix = isHiresFixDesired && (this.commandsConfig.maxPixels?.let { scaledSize.inPixels <= it } ?: true)
        val hires = if (doHiresFix) HiresParams(
            factor = hiresFactor,
            steps = hiresSteps,
            upscaler = hiresUpscaler,
            denoising = hiresDenoising,
        ) else null

        val controlnets = buildList {
            if (pControlnet1Image != null) {
                add(
                    ControlnetUnitParams(
                        image = pControlnet1Image,
                        type = controlnet1Type,
                        weight = controlnet1Weight,
                    )
                )
            }
            if (pControlnet2Image != null) {
                add(
                    ControlnetUnitParams(
                        image = pControlnet2Image,
                        type = controlnet2Type,
                        weight = controlnet2Weight,
                    )
                )
            }
        }

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
            enableADetailer = enableADetailer,
            hires = hires,
            checkpointId = checkpointId,
            controlnets = controlnets,
        )

        val mainParams = buildMap {
            if (prompt != null) put("Prompt", "`$prompt`")
            if (negativePrompt != null) put("Negative prompt", "`$negativePrompt`")
            put("Size", if (doHiresFix) "$scaledSize (scaled up from $size)" else "$size")
            put("Seed", "$seed")
            val checkpoint = checkpoints.find { it.id == checkpointId }
            if (checkpoint != null) put("Checkpoint", checkpoint.name)
        }

        val otherParams = buildMap {
            put("Steps", "$steps")
            put("CFG", "$cfg")
            put("ADetailer", if (enableADetailer) "enabled" else "disabled")
            if (doHiresFix) {
                put("Hires factor", "$hiresFactor")
                put("Hires steps", "$hiresSteps")
                put("Hires denoising", "$hiresDenoising")
            }
        }

        val warnings = buildList {
            if (isHiresFixDesired && !doHiresFix) {
                add("Hires-fix was ignored due to size constraints.")
            }
            if (desiredSize.inPixels > size.inPixels) {
                add("$desiredSize was reduced to $size due to size constraints.")
            }
        }

        val controlnetImages = controlnets.map { net -> net to net.image.split(",").last() }

        val initialResponse = setInitialMessage(mainParams, otherParams, warnings, controlnetImages)

        queueInfo.register(
            isTextGeneration = false,
            onIndexChanged = { index ->
                initialResponse.onProgress(
                    index,
                    mainParams,
                    otherParams,
                    warnings,
                    controlnetImages,
                )
            },
        ) {
            try {
                if (queueInfo.isTextGenerationActive) {
                    tgClient?.unloadModel()
                }
                val response = client.txt2img(params)

                val images = if (count > 1) {
                    // The first image is a grid containing all the generated images
                    // We can remove it
                    response.images.drop(1)
                } else {
                    response.images
                }
                val outputImages = images.take(count)
                val extraImages = images.drop(count)
                var imageIndex = -1
                val controlnetImages = controlnets.map { net ->
                    if (doHiresFix && net.type.supportsHiresFix) {
                        ++imageIndex
                    }
                    net to extraImages.getOrNull(++imageIndex)
                }

                initialResponse.onSuccess(
                    outputImages,
                    params.seed,
                    mainParams,
                    otherParams,
                    warnings,
                    controlnetImages
                )

            } catch (e: Exception) {
                logger.logInteractionException(e)
                initialResponse.onError(
                    e,
                    mainParams,
                    otherParams,
                    warnings,
                    controlnetImages
                )
            }
        }
    }
}

private val CommandsConfig.defaultCheckpoint get() = txt2img.defaultCheckpoint ?: global.defaultCheckpoint
private val CommandsConfig.alwaysIncludedPrompt get() = txt2img.alwaysIncludedPrompt ?: global.alwaysIncludedPrompt
private val CommandsConfig.alwaysIncludedNegativePrompt
    get() = txt2img.alwaysIncludedNegativePrompt
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
private val CommandsConfig.defaultEnableADetailer
    get() = txt2img.defaultEnableADetailer ?: global.defaultEnableADetailer
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

