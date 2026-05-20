// src/test/kotlin/com/techfactor/semanticmessage/repository/MessageRepositoryTest.kt
package com.techfactor.semanticmessage.repository

import com.techfactor.semanticmessage.MessageRepository
import com.techfactor.semanticmessage.model.Message
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MessageRepositoryTest {

    private val postgres = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")

    private lateinit var repository: MessageRepository

    @BeforeAll
    fun setup() {
        postgres.start()
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        repository = MessageRepository()
        repository.init()
        // The index makes it harded to test, dropping it to have deterministic results.
        // TODO rewrite test so we can test with index.
        transaction {
            exec("DROP INDEX IF EXISTS idx_messages_embedding")
        }
    }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    //TODO could improve this by rolling back transactions for each test.
    @BeforeEach
    fun clearTable() {
        runBlocking {
            repository.dbQuery {
                exec("TRUNCATE TABLE message")
            }
        }
    }


    @Test
    fun `insert and findById returns message`() = runBlocking {
        val message = Message(
            id = UUID.randomUUID(),
            timestamp = Clock.System.now(),
            content = "test content"
        )
        repository.insert(message)
        val found = repository.findById(message.id)
        assertNotNull(found)
        assertEquals(message.id, found.id)
        assertEquals(message.content, found.content)
    }

    @Test
    fun `findById returns null for unknown id`() = runBlocking {
        val result = repository.findById(UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `insert with embedding and semanticSearch returns results`() = runBlocking {
        val embedding = List(768) { it.toFloat() / 768f }
        val message = Message(
            id = UUID.randomUUID(),
            timestamp = Clock.System.now(),
            content = "semantic search test"
        )
        repository.insert(message, embedding)
        val results = repository.semanticSearch(embedding, limit = 5)
        assert(results.isNotEmpty())
        assertEquals(message.id, results.first().first.id)
    }

    @Test
    fun `insert duplicate id does nothing`() = runBlocking {
        val id = UUID.randomUUID()
        val message = Message(id = id, timestamp = Clock.System.now(), content = "original")
        repository.insert(message)
        repository.insert(message.copy(content = "duplicate"))
        val found = repository.findById(id)
        assertEquals("original", found?.content)
    }

    @Test
    fun `insert with embedding and semanticSearch returns results ordered by similarity`() = runBlocking {
        // Two embeddings: one close to query, one far away
        val queryEmbedding = List(768) { 1.0f }
        val closeEmbedding = List(768) { 0.99f }   // very similar to query
        val farEmbedding   = List(768) { if (it < 384) 0.0f else 1.0f }    // perpendicular, similarity = 0

        val closeMessage = Message(
            id = UUID.randomUUID(),
            timestamp = Clock.System.now(),
            content = "close to query"
        )
        val farMessage = Message(
            id = UUID.randomUUID(),
            timestamp = Clock.System.now(),
            content = "far from query"
        )

        repository.insert(closeMessage, closeEmbedding)
        repository.insert(farMessage, farEmbedding)

        val results = repository.semanticSearch(queryEmbedding, limit = 5)
        assertEquals(2, results.size)
        // closest message should rank first
        assertEquals(closeMessage.id, results.first().first.id)
        assertEquals(farMessage.id, results[1].first.id)
        // similarity of close message should be higher than far message
        val closeSimilarity = results.first { it.first.id == closeMessage.id }.second
        val farSimilarity = results.first { it.first.id == farMessage.id }.second
        Assertions.assertTrue(closeSimilarity > farSimilarity)
    }

}
