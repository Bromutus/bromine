package at.bromutus.bromine.utils

import at.bromutus.bromine.AppColors
import at.bromutus.bromine.sdclient.ControlnetUnitParams
import dev.kord.common.Color
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


fun MessageModifyBuilder.generationInProgressEmbed(
    mainParams: Map<String, String> = emptyMap(),
    otherParams: Map<String, String> = emptyMap(),
    warnings: List<String> = emptyList(),
    thumbnail: String? = null,
    thumbnailExtension: String? = null,
) = generationParamsEmbed(
    title = "Generating...",
    mainParams = mainParams,
    otherParams = otherParams,
    warnings = warnings,
    color = AppColors.processing,
    thumbnail = thumbnail,
    thumbnailExtension = thumbnailExtension,
)

fun MessageModifyBuilder.generationSuccessEmbed(
    mainParams: Map<String, String> = emptyMap(),
    otherParams: Map<String, String> = emptyMap(),
    warnings: List<String> = emptyList(),
    thumbnail: String? = null,
    thumbnailExtension: String? = null,
) = generationParamsEmbed(
    title = "Generation completed.",
    mainParams = mainParams,
    otherParams = otherParams,
    warnings = warnings,
    color = AppColors.success,
    thumbnail = thumbnail,
    thumbnailExtension = thumbnailExtension,
)

@OptIn(ExperimentalEncodingApi::class)
private fun MessageModifyBuilder.generationParamsEmbed(
    title: String? = null,
    mainParams: Map<String, String> = emptyMap(),
    otherParams: Map<String, String> = emptyMap(),
    warnings: List<String> = emptyList(),
    color: Color? = null,
    thumbnail: String? = null,
    thumbnailExtension: String? = null,
) {
    val thumbnailFileName = "input.${thumbnailExtension ?: "png"}"
    if (thumbnail != null) {
        addFile(thumbnailFileName, ChannelProvider {
            ByteReadChannel(Base64.decode(thumbnail))
        })
    }
    embed {
        this.title = title
        description = mainParams.map { "**${it.key}**: ${it.value}" }.joinToString("\n")
        if (warnings.isNotEmpty()) {
            description += "\n\n_Warnings:_\n" + warnings.joinToString("\n") { "- $it" }
        }
        footer {
            text = otherParams.map { "${it.key}: ${it.value}" }.joinToString(", ")
        }
        this.color = color
        if (thumbnail != null) {
            thumbnail {
                url = "attachment://$thumbnailFileName"
            }
        }
    }
}

fun MessageModifyBuilder.controlnetInProgressEmbeds(
    controlnetImages: List<Pair<ControlnetUnitParams, String?>> = emptyList(),
) {
    controlnetEmbeds(
        controlnetImages = controlnetImages,
        color = AppColors.processing,
    )
}

fun MessageModifyBuilder.controlnetSuccessEmbeds(
    controlnetImages: List<Pair<ControlnetUnitParams, String?>> = emptyList(),
) {
    controlnetEmbeds(
        controlnetImages = controlnetImages,
        color = AppColors.success,
    )
}

@OptIn(ExperimentalEncodingApi::class)
private fun MessageModifyBuilder.controlnetEmbeds(
    controlnetImages: List<Pair<ControlnetUnitParams, String?>> = emptyList(),
    color: Color? = null,
) {
    controlnetImages.forEachIndexed { index, (net, img) ->
        val fileName = "controlnet${index + 1}.png"
        if (img != null) {
            addFile(fileName, ChannelProvider {
                ByteReadChannel(Base64.decode(img))
            })
        }
        embed {
            title = "ControlNet Unit ${index + 1}"
            description = listOf(
                "**Type**: ${net.type.name}",
                "**Weight**: ${net.weight}",
            ).joinToString("\n")
            this.color = color
            if (img != null) {
                thumbnail {
                    url = "attachment://$fileName"
                }
            }
        }
    }
}
