package at.bromutus.bromine

import io.github.cdimascio.dotenv.Dotenv

fun loadAppConfig(env: EnvLoader): AppConfig {
    val discord = DiscordConfig(
        token = env.string("DISCORD_API_TOKEN"),
    )
    val sd = SDConfig(
        apiUrl = env.string("SD_API_URL"),
    )
    val global = GlobalCommandConfig(
        checkpointDefault = env.stringOrNull("CMD_GLOBAL_CHECKPOINT_DEFAULT"),
        promptAlwaysInclude = env.stringOrNull("CMD_GLOBAL_PROMPT_ALWAYS_INCLUDE"),
        negativePromptAlwaysInclude = env.stringOrNull("CMD_GLOBAL_NEGATIVE_PROMPT_ALWAYS_INCLUDE"),
        width = ValueOptions(
            min = env.uintOrNull("CMD_GLOBAL_WIDTH_MIN"),
            max = env.uintOrNull("CMD_GLOBAL_WIDTH_MAX"),
            default = env.uintOrNull("CMD_GLOBAL_WIDTH_DEFAULT"),
        ),
        height = ValueOptions(
            min = env.uintOrNull("CMD_GLOBAL_HEIGHT_MIN"),
            max = env.uintOrNull("CMD_GLOBAL_HEIGHT_MAX"),
            default = env.uintOrNull("CMD_GLOBAL_HEIGHT_DEFAULT"),
        ),
        count = ValueOptions(
            min = env.uintOrNull("CMD_GLOBAL_COUNT_MIN"),
            max = env.uintOrNull("CMD_GLOBAL_COUNT_MAX"),
            default = env.uintOrNull("CMD_GLOBAL_COUNT_DEFAULT"),
        ),
        samplerDefault = env.string("CMD_GLOBAL_SAMPLER_DEFAULT"),
        steps = ValueOptions(
            min = env.uintOrNull("CMD_GLOBAL_STEPS_MIN"),
            max = env.uintOrNull("CMD_GLOBAL_STEPS_MAX"),
            default = env.uintOrNull("CMD_GLOBAL_STEPS_DEFAULT"),
        ),
        cfg = ValueOptions(
            min = env.doubleOrNull("CMD_GLOBAL_CFG_MIN"),
            max = env.doubleOrNull("CMD_GLOBAL_CFG_MAX"),
            default = env.doubleOrNull("CMD_GLOBAL_CFG_DEFAULT"),
        ),
    )
    val txt2img = Txt2ImgCommandConfig(
        defaultCheckpoint = env.string("CMD_TXT2IMG_CHECKPOINT_DEFAULT", default = global.checkpointDefault),
        promptAlwaysInclude = env.stringOrNull("CMD_TXT2IMG_PROMPT_ALWAYS_INCLUDE") ?: global.promptAlwaysInclude,
        negativePromptAlwaysInclude = env.stringOrNull("CMD_TXT2IMG_NEGATIVE_PROMPT_ALWAYS_INCLUDE")
            ?: global.negativePromptAlwaysInclude,
        width = ValueOptions(
            min = env.uint("CMD_TXT2IMG_WIDTH_MIN", default = global.width.min),
            max = env.uint("CMD_TXT2IMG_WIDTH_MAX", default = global.width.max),
            default = env.uint("CMD_TXT2IMG_WIDTH_DEFAULT", default = global.width.default),
        ),
        height = ValueOptions(
            min = env.uint("CMD_TXT2IMG_HEIGHT_MIN", default = global.height.min),
            max = env.uint("CMD_TXT2IMG_HEIGHT_MAX", default = global.height.max),
            default = env.uint("CMD_TXT2IMG_HEIGHT_DEFAULT", default = global.height.default),
        ),
        count = ValueOptions(
            min = env.uint("CMD_TXT2IMG_COUNT_MIN", default = global.count.min),
            max = env.uint("CMD_TXT2IMG_COUNT_MAX", default = global.count.max),
            default = env.uint("CMD_TXT2IMG_COUNT_DEFAULT", default = global.count.default),
        ),
        samplerDefault = env.string("CMD_TXT2IMG_SAMPLER_DEFAULT", default = global.samplerDefault),
        steps = ValueOptions(
            min = env.uint("CMD_TXT2IMG_STEPS_MIN", default = global.steps.min),
            max = env.uint("CMD_TXT2IMG_STEPS_MAX", default = global.steps.max),
            default = env.uint("CMD_TXT2IMG_STEPS_DEFAULT", default = global.steps.default),
        ),
        cfg = ValueOptions(
            min = env.double("CMD_TXT2IMG_CFG_MIN", default = global.cfg.min),
            max = env.double("CMD_TXT2IMG_CFG_MAX", default = global.cfg.max),
            default = env.double("CMD_TXT2IMG_CFG_DEFAULT", default = global.cfg.default),
        ),
        hiresFactor = ValueOptions(
            min = env.double("CMD_TXT2IMG_HIRES_FACTOR_MIN"),
            max = env.double("CMD_TXT2IMG_HIRES_FACTOR_MAX"),
            default = env.double("CMD_TXT2IMG_HIRES_FACTOR_DEFAULT"),
        ),
        hiresSteps = ValueOptions(
            min = env.uint("CMD_TXT2IMG_HIRES_STEPS_MIN"),
            max = env.uint("CMD_TXT2IMG_HIRES_STEPS_MAX"),
            default = env.uint("CMD_TXT2IMG_HIRES_STEPS_DEFAULT"),
        ),
        hiresUpscalerDefault = env.string("CMD_TXT2IMG_HIRES_UPSCALER_DEFAULT"),
        hiresDenoising = ValueOptions(
            min = env.double("CMD_TXT2IMG_HIRES_DENOISING_MIN"),
            max = env.double("CMD_TXT2IMG_HIRES_DENOISING_MAX"),
            default = env.double("CMD_TXT2IMG_HIRES_DENOISING_DEFAULT"),
        ),
    )
    val img2img = Img2ImgCommandConfig(
        checkpointDefault = env.string("CMD_IMG2IMG_CHECKPOINT_DEFAULT", default = global.checkpointDefault),
        promptAlwaysInclude = env.stringOrNull("CMD_IMG2IMG_PROMPT_ALWAYS_INCLUDE") ?: global.promptAlwaysInclude,
        negativePromptAlwaysInclude = env.stringOrNull("CMD_IMG2IMG_NEGATIVE_PROMPT_ALWAYS_INCLUDE")
            ?: global.negativePromptAlwaysInclude,
        width = ValueOptions(
            min = env.uint("CMD_IMG2IMG_WIDTH_MIN", default = global.width.min),
            max = env.uint("CMD_IMG2IMG_WIDTH_MAX", default = global.width.max),
            default = env.uint("CMD_IMG2IMG_WIDTH_DEFAULT", default = global.width.default),
        ),
        height = ValueOptions(
            min = env.uint("CMD_IMG2IMG_HEIGHT_MIN", default = global.height.min),
            max = env.uint("CMD_IMG2IMG_HEIGHT_MAX", default = global.height.max),
            default = env.uint("CMD_IMG2IMG_HEIGHT_DEFAULT", default = global.height.default),
        ),
        count = ValueOptions(
            min = env.uint("CMD_IMG2IMG_COUNT_MIN", default = global.count.min),
            max = env.uint("CMD_IMG2IMG_COUNT_MAX", default = global.count.max),
            default = env.uint("CMD_IMG2IMG_COUNT_DEFAULT", default = global.count.default),
        ),
        resizeModeDefault = env.uint("CMD_IMG2IMG_RESIZE_MODE_DEFAULT"),
        denoisingStrength = ValueOptions(
            min = env.double("CMD_IMG2IMG_DENOISING_STRENGTH_MIN"),
            max = env.double("CMD_IMG2IMG_DENOISING_STRENGTH_MAX"),
            default = env.double("CMD_IMG2IMG_DENOISING_STRENGTH_DEFAULT"),
        ),
        samplerDefault = env.string("CMD_IMG2IMG_SAMPLER_DEFAULT", default = global.samplerDefault),
        steps = ValueOptions(
            min = env.uint("CMD_IMG2IMG_STEPS_MIN", default = global.steps.min),
            max = env.uint("CMD_IMG2IMG_STEPS_MAX", default = global.steps.max),
            default = env.uint("CMD_IMG2IMG_STEPS_DEFAULT", default = global.steps.default),
        ),
        cfg = ValueOptions(
            min = env.double("CMD_IMG2IMG_CFG_MIN", default = global.cfg.min),
            max = env.double("CMD_IMG2IMG_CFG_MAX", default = global.cfg.max),
            default = env.double("CMD_IMG2IMG_CFG_DEFAULT", default = global.cfg.default),
        ),
        displaySourceImageDefault = env.boolean("CMD_IMG2IMG_DISPLAY_SOURCE_IMAGE_DEFAULT"),
    )
    val commands = CommandsConfig(
        global = global,
        txt2img = txt2img,
        img2img = img2img,
    )
    return AppConfig(
        discord = discord,
        sd = sd,
        commands = commands,
    )
}

data class AppConfig(
    val discord: DiscordConfig,
    val sd: SDConfig,
    val commands: CommandsConfig,
)

data class DiscordConfig(
    val token: String,
)

data class SDConfig(
    val apiUrl: String,
)

data class CommandsConfig(
    val global: GlobalCommandConfig,
    val txt2img: Txt2ImgCommandConfig,
    val img2img: Img2ImgCommandConfig,
)

data class GlobalCommandConfig(
    val checkpointDefault: String?,
    val promptAlwaysInclude: String?,
    val negativePromptAlwaysInclude: String?,
    val width: ValueOptions<UInt?>,
    val height: ValueOptions<UInt?>,
    val count: ValueOptions<UInt?>,
    val samplerDefault: String?,
    val steps: ValueOptions<UInt?>,
    val cfg: ValueOptions<Double?>,
)

data class Txt2ImgCommandConfig(
    val defaultCheckpoint: String,
    val promptAlwaysInclude: String?,
    val negativePromptAlwaysInclude: String?,
    val width: ValueOptions<UInt>,
    val height: ValueOptions<UInt>,
    val count: ValueOptions<UInt>,
    val samplerDefault: String,
    val steps: ValueOptions<UInt>,
    val cfg: ValueOptions<Double>,
    val hiresFactor: ValueOptions<Double>,
    val hiresSteps: ValueOptions<UInt>,
    val hiresUpscalerDefault: String,
    val hiresDenoising: ValueOptions<Double>,
)

data class Img2ImgCommandConfig(
    val checkpointDefault: String,
    val promptAlwaysInclude: String?,
    val negativePromptAlwaysInclude: String?,
    val width: ValueOptions<UInt>,
    val height: ValueOptions<UInt>,
    val count: ValueOptions<UInt>,
    val denoisingStrength: ValueOptions<Double>,
    val resizeModeDefault: UInt,
    val samplerDefault: String,
    val steps: ValueOptions<UInt>,
    val cfg: ValueOptions<Double>,
    val displaySourceImageDefault: Boolean,
)

data class ValueOptions<T>(
    val min: T,
    val max: T,
    val default: T,
)


class EnvLoader {
    private val dotenv: Dotenv by lazy { Dotenv.load() }

    fun stringOrNull(key: String): String? {
        val str = System.getenv(key) ?: dotenv.get(key)
        return if (str.isNullOrBlank()) null else str
    }

    fun intOrNull(key: String): Int? = stringOrNull(key)?.toInt()

    fun uintOrNull(key: String): UInt? = stringOrNull(key)?.toUInt()

    fun doubleOrNull(key: String): Double? = stringOrNull(key)?.toDouble()

    fun booleanOrNull(key: String): Boolean? = stringOrNull(key)?.toBoolean()

    fun string(key: String, default: String? = null): String = stringOrNull(key)
        ?: default
        ?: throw IllegalStateException("Environment variable $key not found")


    fun int(key: String, default: Int? = null): Int = stringOrNull(key)?.toInt()
        ?: default
        ?: throw IllegalStateException("Environment variable $key not found")

    fun uint(key: String, default: UInt? = null): UInt = stringOrNull(key)?.toUInt()
        ?: default
        ?: throw IllegalStateException("Environment variable $key not found")

    fun double(key: String, default: Double? = null): Double = stringOrNull(key)?.toDouble()
        ?: default
        ?: throw IllegalStateException("Environment variable $key not found")

    fun boolean(key: String, default: Boolean? = null): Boolean = stringOrNull(key)?.toBoolean()
        ?: default
        ?: throw IllegalStateException("Environment variable $key not found")
}