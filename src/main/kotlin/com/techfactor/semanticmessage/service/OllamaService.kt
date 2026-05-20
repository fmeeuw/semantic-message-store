package com.techfactor.semanticmessage

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.Closeable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import io.ktor.client.plugins.HttpTimeout

@Serializable
private data class EmbeddingRequest(val model: String, val input: String)

@Serializable
private data class EmbeddingResponse(val embeddings: List<List<Float>>)

class OllamaService(private val baseUrl: String = "http://localhost:11434",
                    private val model: String = "nomic-embed-text") : Closeable {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun generateEmbedding(text: String): List<Float>? {
        return try {
            val response = client.post("$baseUrl/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(model = model, input = text))
            }
            response.body<EmbeddingResponse>().embeddings.firstOrNull()
        } catch (e: Exception) {
            logger.error("Failed to generate embedding: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun isAvailable(): Boolean {
        return try {
            client.get("$baseUrl/api/tags")
            true
        } catch (e: Exception) {
            logger.debug("Ollama not available: ${e.message}")
            false
        }
    }

    override fun close() = client.close()
}

