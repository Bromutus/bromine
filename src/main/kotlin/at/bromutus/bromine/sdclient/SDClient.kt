package at.bromutus.bromine.sdclient

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val logger = KotlinLogging.logger {}

fun createSDClient(baseUrl: String): SDClient {
    val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 0
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = at.bromutus.bromine.sdclient.logger.debug(message)
            }
            level = LogLevel.HEADERS
        }
    }
    return SDClient(baseUrl, httpClient)
}

class SDClient(val baseUrl: String, private val httpClient: HttpClient) {
    suspend fun txt2img(params: Txt2ImgParams): Txt2ImgResponse {
        val request = Txt2ImgRequest.create(params)
        logger.trace("Request: $request")

        val response = try {
            httpClient.post("$baseUrl/sdapi/v1/txt2img") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<Txt2ImgResponse>()
        } catch (e: Exception) {
            throw SDClientException(cause = e)
        }
        logger.trace("Response: $response")

        if (response.error != null) {
            throw SDClientException(message = response.error, details = response.detail, errors = response.errors)
        }
        return response
    }
}

data class Txt2ImgParams(
    val prompt: String,
    val negativePrompt: String,
    val width: Int,
    val height: Int,
    val count: Int,
    val seed: Int,
    val samplerName: String,
    val steps: Int,
    val cfg: Double,
    val hiresFactor: Double?,
    val hiresSteps: Int?,
    val hiresUpscaler: String,
    val hiresDenoising: Double,
    val checkpointName: String,
)

@Serializable
data class Txt2ImgRequest(
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("negative_prompt") val negativePrompt: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("sampler_name") val samplerName: String? = null,
    @SerialName("n_iter") val nIter: Int? = null,
    @SerialName("seed") val seed: Int? = null,
    @SerialName("steps") val steps: Int? = null,
    @SerialName("cfg_scale") val cfgScale: Double? = null,
    @SerialName("enable_hr") val enableHr: Boolean? = null,
    @SerialName("hr_scale") val hrScale: Double? = null,
    @SerialName("hr_upscaler") val hrUpscaler: String? = null,
    @SerialName("hr_second_pass_steps") val hrSecondPassSteps: Int? = null,
    @SerialName("hr_resize_x") val hrResizeX: Int? = null,
    @SerialName("hr_resize_y") val hrResizeY: Int? = null,
    @SerialName("hr_sampler_name") val hrSamplerName: String? = null,
    @SerialName("hr_prompt") val hrPrompt: String? = null,
    @SerialName("hr_negative_prompt") val hrNegativePrompt: String? = null,
    @SerialName("denoising_strength") val denoisingStrength: Double? = null,
    @SerialName("firstphase_width") val firstphaseWidth: Int? = null,
    @SerialName("firstphase_height") val firstphaseHeight: Int? = null,
    @SerialName("styles") val styles: List<String>? = null,
    @SerialName("subseed") val subseed: Int? = null,
    @SerialName("subseed_strength") val subseedStrength: Int? = null,
    @SerialName("seed_resize_from_h") val seedResizeFromH: Int? = null,
    @SerialName("seed_resize_from_w") val seedResizeFromW: Int? = null,
    @SerialName("batch_size") val batchSize: Int? = null,
    @SerialName("restore_faces") val restoreFaces: Boolean? = null,
    @SerialName("tiling") val tiling: Boolean? = null,
    @SerialName("do_not_save_samples") val doNotSaveSamples: Boolean? = null,
    @SerialName("do_not_save_grid") val doNotSaveGrid: Boolean? = null,
    @SerialName("eta") val eta: Int? = null,
    @SerialName("s_min_uncond") val sMinUncond: Int? = null,
    @SerialName("s_churn") val sChurn: Int? = null,
    @SerialName("s_tmax") val sTmax: Int? = null,
    @SerialName("s_tmin") val sTmin: Int? = null,
    @SerialName("s_noise") val sNoise: Int? = null,
    @SerialName("override_settings") val overrideSettings: JsonObject? = null,
    @SerialName("override_settings_restore_afterwards") val overrideSettingsRestoreAfterwards: Boolean? = null,
    @SerialName("script_args") val scriptArgs: List<String>? = null,
    @SerialName("sampler_index") val samplerIndex: String? = null,
    @SerialName("script_name") val scriptName: String? = null,
    @SerialName("send_images") val sendImages: Boolean? = null,
    @SerialName("save_images") val saveImages: Boolean? = null,
    @SerialName("alwayson_scripts") val alwaysOnScripts: JsonObject? = null,
) {
    companion object {
        fun create(params: Txt2ImgParams) = Txt2ImgRequest(
            prompt = params.prompt,
            negativePrompt = params.negativePrompt,
            width = params.width,
            height = params.height,
            nIter = params.count,
            seed = params.seed,
            samplerName = params.samplerName,
            steps = params.steps,
            cfgScale = params.cfg,
            enableHr = params.hiresFactor != null && params.hiresFactor > 1.0,
            hrScale = params.hiresFactor,
            hrSecondPassSteps = params.hiresSteps,
            hrUpscaler = params.hiresUpscaler,
            denoisingStrength = params.hiresDenoising,
            overrideSettings = JsonObject(
                mapOf(
                    "sd_model_checkpoint" to JsonPrimitive(params.checkpointName),
                ),
            ),
            saveImages = true,
        )
    }
}


@Serializable
data class Txt2ImgResponse(
    @SerialName("images") val images: List<String> = listOf(),
    @SerialName("parameters") val parameters: JsonObject? = null,
    @SerialName("info") val info: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("detail") val detail: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("errors") val errors: String? = null,
)

class SDClientException(
    message: String? = null,
    cause: Throwable? = null,
    val details: String? = null,
    val errors: String? = null,
) :
    Exception(message, cause)