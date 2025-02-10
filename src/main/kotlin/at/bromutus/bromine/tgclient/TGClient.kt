package at.bromutus.bromine.tgclient

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

private val logger = KotlinLogging.logger {}

fun createTGClient(baseUrl: String): TGClient {
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
                override fun log(message: String) = at.bromutus.bromine.tgclient.logger.debug { message }
            }
            level = LogLevel.HEADERS
        }
    }
    return TGClient(baseUrl, httpClient)
}

class TGClient(val baseUrl: String, private val httpClient: HttpClient) {
    suspend fun loadModel(modelName: String) {
        val request = ModelLoadRequest(modelName)
        logger.trace { "Request: $request" }

        val response = try {
            httpClient.post("$baseUrl/v1/internal/model/load") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: Exception) {
            throw TGClientException(cause = e)
        }
        logger.trace { "Response: $response" }
    }

    suspend fun unloadModel() {
        val response = try {
            httpClient.post("$baseUrl/v1/internal/model/unload")
        } catch (e: Exception) {
            throw TGClientException(cause = e)
        }
        logger.trace { "Response: $response" }
    }

    suspend fun generateChat(params: ChatCompletionParams): ChatCompletionResponse {
        val request = ChatCompletionRequest.create(params)
        logger.info { "\nSYSTEM\n${params.messages.firstOrNull { it.role == "system" }?.content}" }
        logger.info { "\nUSER\n${params.messages.lastOrNull { it.role == "user" }?.content}" }
        logger.trace { "Request: $request" }

        val response = try {
            httpClient.post("$baseUrl/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<ChatCompletionResponse>()
        } catch (e: Exception) {
            throw TGClientException(cause = e)
        }
        logger.trace { "Response: $response" }
        logger.info { "\nASSISTANT\n${response.choices?.firstOrNull()?.message?.content}" }

        return response
    }
}

data class ChatCompletionParams(
    val mode: String = "instruct",
    val instructionTemplate: String = "Alpaca",
    val autoMaxNewTokens: Boolean = true,
    val maxTokens: Int? = null,
    val messages: List<ChatCompletionMessage>,
    val stream: Boolean = false,
    val stop: List<String> = emptyList(),
    val grammar: String? = null,
    val shouldContinue: Boolean = false,
)

@Serializable
data class ModelLoadRequest(
    @SerialName("model_name") val modelName: String,
)

@Serializable
data class ChatCompletionRequest(
    @SerialName("mode") val mode: String? = null,
    @SerialName("instruction_template") val instructionTemplate: String? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("auto_max_new_tokens") val autoMaxNewTokens: Boolean? = null,
    @SerialName("messages") val messages: List<ChatCompletionMessage>? = null,
    @SerialName("stream") val stream: Boolean? = null,
    @SerialName("stop") val stop: List<String>? = null,
    @SerialName("continue_") val shouldContinue: Boolean? = null,
    @SerialName("grammar_string") val grammar: String? = null,
) {
    companion object {
        fun create(params: ChatCompletionParams): ChatCompletionRequest {
            return ChatCompletionRequest(
                mode = params.mode,
                instructionTemplate = params.instructionTemplate,
                maxTokens = params.maxTokens,
                autoMaxNewTokens = params.autoMaxNewTokens,
                messages = params.messages,
                stream = params.stream,
                stop = params.stop,
                shouldContinue = params.shouldContinue,
                grammar = params.grammar,
            )
        }
    }
}

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("object") val objectValue: String? = null,
    @SerialName("created") val created: Int? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("choices") val choices: List<ChatCompletionChoice>? = null,
    @SerialName("usage") val usage: ChatCompletionUsage? = null,
)

@Serializable
data class ChatCompletionChoice(
    @SerialName("index") val index: Int? = null,
    @SerialName("message") val message: ChatCompletionMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    @SerialName("logprobs") val logprobs: Int? = null,
)

@Serializable
data class ChatCompletionUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

class TGClientException(
    message: String? = null,
    cause: Throwable? = null,
) :
    Exception(message, cause)