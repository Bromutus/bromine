package at.bromutus.bromine.sdclient

import at.bromutus.bromine.appdata.Controlnet
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
import kotlinx.serialization.json.*

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
                override fun log(message: String) = at.bromutus.bromine.sdclient.logger.debug { message }
            }
            level = LogLevel.HEADERS
        }
    }
    return SDClient(baseUrl, httpClient)
}

class SDClient(val baseUrl: String, private val httpClient: HttpClient) {
    suspend fun txt2img(params: Txt2ImgParams): ImgResponse {
        val request = Txt2ImgRequest.create(params)
        logger.trace { "Request: $request" }

        val response = try {
            httpClient.post("$baseUrl/sdapi/v1/txt2img") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<ImgResponse>()
        } catch (e: Exception) {
            throw SDClientException(cause = e)
        }
        logger.trace { "Response: $response" }

        if (response.error != null) {
            throw SDClientException(message = response.error, details = response.detail, errors = response.errors)
        }
        return response
    }

    suspend fun img2img(params: Img2ImgParams): ImgResponse {
        val request = Img2ImgRequest.create(params)
        logger.trace { "Request: $request" }

        val response = try {
            httpClient.post("$baseUrl/sdapi/v1/img2img") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<ImgResponse>()
        } catch (e: Exception) {
            throw SDClientException(cause = e)
        }
        logger.trace { "Response: $response" }

        if (response.error != null) {
            throw SDClientException(message = response.error, details = response.detail, errors = response.errors)
        }
        return response
    }
}

data class Txt2ImgParams(
    val prompt: String?,
    val negativePrompt: String?,
    val width: Int,
    val height: Int,
    val count: Int,
    val seed: Int,
    val samplerName: String,
    val steps: Int,
    val cfg: Double,
    val enableADetailer: Boolean,
    val hires: HiresParams?,
    val checkpointId: String?,
    val controlnets: List<ControlnetUnitParams> = emptyList(),
)

data class HiresParams(
    val factor: Double,
    val steps: Int,
    val upscaler: String,
    val denoising: Double,
)

data class ControlnetUnitParams(
    val image: String,
    val type: Controlnet,
    val weight: Double,
)

data class Img2ImgParams(
    val image: String,
    val prompt: String?,
    val negativePrompt: String?,
    val width: Int,
    val height: Int,
    val count: Int,
    val denoisingStrength: Double,
    val resizeMode: ResizeMode,
    val seed: Int,
    val samplerName: String,
    val steps: Int,
    val cfg: Double,
    val enableADetailer: Boolean,
    val checkpointId: String?,
    val controlnets: List<ControlnetUnitParams> = emptyList(),
)

enum class ResizeMode(val intValue: Int) {
    Stretch(0),
    Crop(1),
    Fill(2),
    Latent(3),
    ;

    companion object {
        fun fromInt(intValue: Int): ResizeMode? = entries.firstOrNull { it.intValue == intValue }
    }
}

@Serializable
data class Txt2ImgRequest(
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("negative_prompt") val negativePrompt: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("n_iter") val nIter: Int? = null,
    @SerialName("seed") val seed: Int? = null,
    @SerialName("sampler_name") val samplerName: String? = null,
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
    @SerialName("hr_additional_modules") val hrAdditionalModules: List<String>? = null,
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
    @SerialName("script_args") val scriptArgs: List<JsonObject>? = null,
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
            enableHr = params.hires != null,
            hrScale = params.hires?.factor,
            hrSecondPassSteps = params.hires?.steps,
            hrUpscaler = params.hires?.upscaler,
            hrAdditionalModules = listOf("Use same choices"),
            denoisingStrength = params.hires?.denoising,
            overrideSettings = buildJsonObject {
                if (params.checkpointId != null) {
                    put("sd_model_checkpoint", params.checkpointId)
                }
            },
            alwaysOnScripts = buildJsonObject {
                if (params.controlnets.isNotEmpty()) {
                    putJsonObject("controlnet") {
                        putJsonArray("args") {
                            for (controlnet in params.controlnets) {
                                val type = controlnet.type.params
                                addJsonObject {
                                    put("input_image", controlnet.image)
                                    if (type.module != null) put("module", type.module)
                                    if (type.model != null) put("model", type.model)
                                    put("weight", controlnet.weight)
                                    if (type.processorRes != null) put("processor_res", type.processorRes.toInt())
                                    if (type.thresholdA != null) put("threshold_a", type.thresholdA)
                                    if (type.thresholdB != null) put("threshold_b", type.thresholdB)
                                }
                            }
                        }
                    }
                }
                if (params.enableADetailer) {
                    putJsonObject("ADetailer") {
                        putJsonArray("args") {
                            addJsonObject {
                                put("ad_model", "hand_yolov8n.pt")
                                put("ad_prompt", "hand")
                            }
                            addJsonObject {
                                put("ad_model", "face_yolov8n.pt")
                            }
                        }
                    }
                }
            },
            saveImages = true,
        )
    }
}

@Serializable
data class Img2ImgRequest(
    @SerialName("init_images") val initImages: List<String>? = null,
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("negative_prompt") val negativePrompt: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("n_iter") val nIter: Int? = null,
    @SerialName("denoising_strength") val denoisingStrength: Double? = null,
    @SerialName("resize_mode") val resizeMode: Int? = null,
    @SerialName("seed") val seed: Int? = null,
    @SerialName("sampler_name") val samplerName: String? = null,
    @SerialName("steps") val steps: Int? = null,
    @SerialName("cfg_scale") val cfgScale: Double? = null,
    @SerialName("image_cfg_scale") val imageCfgScale: Int? = null,
    @SerialName("mask") val mask: String? = null,
    @SerialName("mask_blur") val maskBlur: Int? = null,
    @SerialName("inpainting_fill") val inpaintingFill: Int? = null,
    @SerialName("inpaint_full_res") val inpaintFullRes: Boolean? = true,
    @SerialName("inpaint_full_res_padding") val inpaintFullResPadding: Int? = null,
    @SerialName("inpainting_mask_invert") val inpaintingMaskInvert: Int? = null,
    @SerialName("initial_noise_multiplier") val initialNoiseMultiplier: Int? = null,
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
    @SerialName("script_args") val scriptArgs: List<JsonObject>? = null,
    @SerialName("sampler_index") val samplerIndex: String? = null,
    @SerialName("include_init_images") val includeInitImages: Boolean? = false,
    @SerialName("script_name") val scriptName: String? = null,
    @SerialName("send_images") val sendImages: Boolean? = null,
    @SerialName("save_images") val saveImages: Boolean? = null,
    @SerialName("alwayson_scripts") val alwaysOnScripts: JsonObject? = null
) {
    companion object {
        fun create(params: Img2ImgParams) = Img2ImgRequest(
            initImages = listOf(params.image),
            prompt = params.prompt,
            negativePrompt = params.negativePrompt,
            width = params.width,
            height = params.height,
            nIter = params.count,
            denoisingStrength = params.denoisingStrength,
            resizeMode = params.resizeMode.intValue,
            seed = params.seed,
            samplerName = params.samplerName,
            steps = params.steps,
            cfgScale = params.cfg,
            overrideSettings = buildJsonObject {
                if (params.checkpointId != null) {
                    put("sd_model_checkpoint", params.checkpointId)
                }
            },
            alwaysOnScripts = buildJsonObject {
                if (params.controlnets.isNotEmpty()) {
                    putJsonObject("controlnet") {
                        putJsonArray("args") {
                            for (controlnet in params.controlnets) {
                                val type = controlnet.type.params
                                addJsonObject {
                                    put("input_image", controlnet.image)
                                    if (type.module != null) put("module", type.module)
                                    if (type.model != null) put("model", type.model)
                                    put("weight", controlnet.weight)
                                    if (type.processorRes != null) put("processor_res", type.processorRes.toInt())
                                    if (type.thresholdA != null) put("threshold_a", type.thresholdA)
                                    if (type.thresholdB != null) put("threshold_b", type.thresholdB)
                                }
                            }
                        }
                    }
                    if (params.enableADetailer) {
                        putJsonObject("ADetailer") {
                            putJsonArray("args") {
                                addJsonObject {
                                    put("ad_model", "hand_yolov8n.pt")
                                    put("ad_prompt", "hand")
                                }
                                addJsonObject {
                                    put("ad_model", "face_yolov8n.pt")
                                }
                            }
                        }
                    }
                }
            },
            saveImages = true,
        )
    }
}


@Serializable
data class ImgResponse(
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