package ru.herobrine1st.vk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import ru.herobrine1st.vk.model.VkResponse
import ru.herobrine1st.vk.model.endpoint.VkEndpoint

internal const val API_VERSION = "5.199"

// heavily inspired by trixnity
// AuthData is passed with every request because it could be more efficient in terms of multiple
// accounts (e.g. socket pool is shared)
public class VkApiClient(
    apiServer: Url,
    @PublishedApi
    internal val json: Json = Json { ignoreUnknownKeys = true },
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) {
    public val baseClient: HttpClient = httpClientFactory {
        install(ContentNegotiation) {
            json(json)
        }
        install(Resources)
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        defaultRequest {
            url.takeFrom(apiServer)
            url.protocol = URLProtocol.HTTPS
            url.parameters.append("v", API_VERSION)
        }

        // redirects are used in downloads of "doc" attachments
        followRedirects = true
    }


    public suspend inline fun <reified Endpont : VkEndpoint<Unit, Response>, reified Response> request(
        authData: VkActor,
        endpoint: Endpont,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<Response> = request(authData, endpoint, Unit, requestBuilder)

    public suspend inline fun <reified Endpoint : VkEndpoint<Request, Response>, reified Request, reified Response> request(
        authData: VkActor,
        endpoint: Endpoint,
        requestBody: Request,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<Response> = runCatching {
        val response = baseClient.request(endpoint) {
            if (requestBody != Unit) {
                method = HttpMethod.Post
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
                val jsonObject = json.encodeToJsonElement(requestBody).jsonObject
                val form = FormDataContent(parameters {
                    jsonObject.forEach { (key, value) ->
                        when (value) {
                            is JsonPrimitive -> {
                                append(key, value.content)
                            }

                            is JsonArray -> {
                                append(key, value.joinToString(",") { it.jsonPrimitive.content })
                            }

                            is JsonObject -> {
                                append(key, value.toString())
                            }
                        }
                    }
                })
                setBody(form)
            } else {
                method = HttpMethod.Get
            }
            bearerAuth(authData.token)
            requestBuilder()
        }

        // TODO K2 is probably smart enough to infer type
        when (val vkResponse = response.body<VkResponse<Response>>()) {
            is VkResponse.Error -> throw VkApiException(vkResponse.error)
            is VkResponse.Ok -> vkResponse.response
        }
    }
}