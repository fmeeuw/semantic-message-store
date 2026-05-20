package com.techfactor.semanticmessage.service

import com.techfactor.semanticmessage.model.Message
import com.techfactor.semanticmessage.model.SearchRequest
import com.techfactor.semanticmessage.model.SearchResult
import com.techfactor.semanticmessage.repository.MessageRepository
import com.techfactor.semanticmessage.OllamaService
import org.slf4j.LoggerFactory
import java.util.UUID

class MessageService(
    private val repository: MessageRepository,
    private val ollamaService: OllamaService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun storeMessage(message: Message) {
        val embedding = ollamaService.generateEmbedding(message.content)
        if (embedding == null) {
            logger.warn("Storing message ${message.id} without embedding (Ollama unavailable)")
        }
        repository.insert(message, embedding)
        logger.info("Stored message with id=${message.id} and timestamp=${message.timestamp}")
    }

    suspend fun findById(id: UUID): Message? =
        repository.findById(id)

    suspend fun semanticSearch(request: SearchRequest): Result<List<SearchResult>> {
        val queryEmbedding = ollamaService.generateEmbedding(request.query)
            ?: return Result.failure<List<SearchResult>>(IllegalStateException("Ollama unavailable")).also {
                logger.warn("Semantic search failed: Ollama unavailable")
            }

        return try {
            val results = repository.semanticSearch(queryEmbedding, request.limit)
            logger.info("Search returned ${results.size} results")
            Result.success(results.map { (message, similarity) -> SearchResult(message, similarity) })
        } catch (e: Exception) {
            logger.error("Repository semanticSearch failed: ${e::class.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

}
