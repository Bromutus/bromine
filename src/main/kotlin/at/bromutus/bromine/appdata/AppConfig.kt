package at.bromutus.bromine.appdata

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.serialization.Serializable
import java.io.FileInputStream
import kotlin.io.path.Path

fun loadAppConfig(): AppConfig {
    val configFileName = "application.yaml"
    val stream = System.getenv("BROMINE_APPCONFIG")?.let(::FileInputStream)
        ?: try {
            Path(APP_DATA_DIR, configFileName).toFile().inputStream()
        } catch (e: Exception) {
            null
        }
        ?: try {
            FileInputStream(configFileName)
        } catch (e: Exception) {
            null
        }
        ?: object {}::class.java.classLoader.getResourceAsStream(configFileName)
        ?: throw IllegalStateException("Could not find $configFileName. Please create it in the working directory or set the BROMINE_APPCONFIG environment variable.")
    return stream.use { Yaml.default.decodeFromStream(it) }
}

@Serializable
data class AppConfig(
    val discord: DiscordConfig,
    val sd: SDConfig = SDConfig(),
    val tg: TGConfig = TGConfig(),
    val commands: CommandsConfig = CommandsConfig(),
    val checkpoints: CheckpointConfig = CheckpointConfig(),
    val controlnet: ControlnetConfig = ControlnetConfig(),
    val lora: LoraConfig = LoraConfig(),
)

@Serializable
data class DiscordConfig(
    val token: String,
)

@Serializable
data class SDConfig(
    val apiUrl: String? = null,
)

@Serializable
data class TGConfig(
    val apiUrl: String? = null,
)

@Serializable
data class CommandsConfig(
    val global: GlobalCommandConfig = GlobalCommandConfig(),
    val txt2img: Txt2ImgCommandConfig = Txt2ImgCommandConfig(),
    val img2img: Img2ImgCommandConfig = Img2ImgCommandConfig(),
)

@Serializable
data class GlobalCommandConfig(
    val defaultCheckpoint: String? = null,
    val alwaysIncludedPrompt: String? = null,
    val alwaysIncludedNegativePrompt: String? = null,
    val width: ValueOptions<Int> = ValueOptions(1, 8192, 1024),
    val height: ValueOptions<Int> = ValueOptions(1, 8192, 1024),
    val maxPixels: Int? = null,
    val count: ValueOptions<Int> = ValueOptions(1, 9, 1),
    val defaultSampler: String = "Euler a",
    val steps: ValueOptions<Int> = ValueOptions(1, 40, 25),
    val cfg: ValueOptions<Double> = ValueOptions(1.0, 30.0, 6.0),
    val defaultEnableADetailer: Boolean = false,
)

@Serializable
data class Txt2ImgCommandConfig(
    val defaultCheckpoint: String? = null,
    val alwaysIncludedPrompt: String? = null,
    val alwaysIncludedNegativePrompt: String? = null,
    val width: NullableValueOptions<Int>? = null,
    val height: NullableValueOptions<Int>? = null,
    val maxPixels: Int? = null,
    val count: NullableValueOptions<Int>? = null,
    val defaultSampler: String? = null,
    val steps: NullableValueOptions<Int>? = null,
    val cfg: NullableValueOptions<Double>? = null,
    val defaultEnableADetailer: Boolean? = null,
    val hiresFactor: ValueOptions<Double> = ValueOptions(1.0, 20.0, 1.0),
    val hiresSteps: ValueOptions<Int> = ValueOptions(0, 40, 0),
    val hiresUpscaler: String = "Latent",
    val hiresDenoising: ValueOptions<Double> = ValueOptions(0.0, 1.0, 0.65),
)

@Serializable
data class Img2ImgCommandConfig(
    val defaultCheckpoint: String? = null,
    val alwaysIncludedPrompt: String? = null,
    val alwaysIncludedNegativePrompt: String? = null,
    val width: NullableValueOptions<Int>? = null,
    val height: NullableValueOptions<Int>? = null,
    val maxPixels: Int? = null,
    val count: NullableValueOptions<Int>? = null,
    val denoisingStrength: ValueOptions<Double> = ValueOptions(0.0, 1.0, 0.6),
    val defaultResizeMode: Int = 1,
    val defaultSampler: String? = null,
    val steps: NullableValueOptions<Int>? = null,
    val cfg: NullableValueOptions<Double>? = null,
    val defaultEnableADetailer: Boolean? = null,
    val displaySourceImageByDefault: Boolean = true,
)

@Serializable
data class CheckpointConfig(
    val installed: List<Checkpoint> = emptyList(),
)

@Serializable
data class Checkpoint(
    val id: String,
    val name: String,
)

@Serializable
data class ControlnetConfig(
    val weight: ValueOptions<Double> = ValueOptions(0.0, 2.0, 1.0),
    val installed: List<Controlnet> = emptyList(),
)

@Serializable
data class Controlnet(
    val name: String,
    val params: ControlnetParams = ControlnetParams(),
    val supportsHiresFix: Boolean = true,
)

@Serializable
data class ControlnetParams(
    val model: String? = null,
    val module: String? = null,
    val processorRes: Int? = null,
    val thresholdA: Double? = null,
    val thresholdB: Double? = null,
)

@Serializable
data class LoraConfig(
    val tags: List<String> = emptyList(),
    val installed: List<Lora> = emptyList(),
)

@Serializable
data class Lora(
    val id: String,
    val name: String,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val nsfw: Boolean = false,
    val recommendedWeights: RecommendedWeights? = null,
    val keywords: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class RecommendedWeights(
    val low: Double? = null,
    val high: Double? = null,
    val default: Double? = null,
)

@Serializable
data class ValueOptions<out T : Any>(
    val min: T,
    val max: T,
    val default: T,
)

@Serializable
data class NullableValueOptions<out T : Any>(
    val min: T? = null,
    val max: T? = null,
    val default: T? = null,
)
