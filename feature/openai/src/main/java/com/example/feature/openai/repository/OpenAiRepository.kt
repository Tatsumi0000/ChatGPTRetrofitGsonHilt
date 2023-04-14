package com.example.feature.openai.repository

import com.example.feature.openai.model.GPT35Turbo
import com.example.feature.openai.model.GPT35TurboResponse
import com.example.feature.openai.state.SSEEvent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface OpenAiRepository {
    suspend fun postCompletions(gpt35Turbo: GPT35Turbo)
    fun cancelCompletions()
    val state: StateFlow<SSEEvent>
}

@Singleton
class OpenAiRepositoryImpl @Inject constructor(
    private val requestBuilder: Request.Builder,
    private val client: OkHttpClient,
) : OpenAiRepository {

    private val _state = MutableStateFlow<SSEEvent>(SSEEvent.Empty)
    override val state = _state.asStateFlow()

    private val gson: Gson = Gson()
    private var eventSource: EventSource? = null
    private val eventSourceListener = object : EventSourceListener() {

        override fun onOpen(eventSource: EventSource, response: Response) {
            super.onOpen(eventSource, response)
            _state.value = SSEEvent.Open
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            super.onEvent(eventSource, id, type, data)

            if (data != "[DONE]") {
                val response = gson.fromJson(data, GPT35TurboResponse::class.java)
                val message = response.choices[0].delta.content
                println(message)
                _state.value = SSEEvent.Event(response)
            }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            super.onFailure(eventSource, t, response)

            if (t != null) {
                _state.value = SSEEvent.Failure(t, response)
            }
        }

        override fun onClosed(eventSource: EventSource) {
            super.onClosed(eventSource)

            _state.value = SSEEvent.Closed
            println("Closed")
        }
    }

    override suspend fun postCompletions(gpt35Turbo: GPT35Turbo) {
        val requestBody = gson.toJson(gpt35Turbo)

        val request = requestBuilder
            .post(requestBody.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
            .build()

        withContext(Dispatchers.IO) {
            eventSource = EventSources.createFactory(client)
                .newEventSource(request, eventSourceListener)
        }
    }

    override fun cancelCompletions() {
        eventSource?.cancel()
    }
}