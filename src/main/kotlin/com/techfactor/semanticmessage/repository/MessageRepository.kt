package com.techfactor.semanticmessage.repository

import com.techfactor.semanticmessage.model.Message
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.ResultRow
import java.sql.ResultSet
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.time.toKotlinInstant
import kotlin.time.toJavaInstant
import org.postgresql.util.PGobject

object MessageTable : Table("message") {
    val id = varchar("id", 36)
    val timestamp = timestamp("timestamp")
    val content = text("content")
    override val primaryKey = PrimaryKey(id)
}

class MessageRepository {

    suspend fun <T> dbQuery(block: suspend JdbcTransaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    fun init() {
        transaction {
            // Enable pgvector extension and create table
            // TODO use database migration tool for this, like flyway.
            // TODO ideally use postgresql uuid type instead of varchar(36)
            exec("""
                CREATE EXTENSION IF NOT EXISTS vector;
                
                CREATE TABLE IF NOT EXISTS message (
                    id VARCHAR(36) PRIMARY KEY,
                    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                    content TEXT NOT NULL,
                    embedding vector(768)
                );
                
                CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON message(timestamp);
                CREATE INDEX IF NOT EXISTS idx_messages_embedding ON message 
                    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
            """.trimIndent())
        }
    }

    suspend fun insert(message: Message, embedding: List<Float>? = null): Unit = dbQuery {
        val embeddingStr = embedding?.let { "[${it.joinToString(",")}]" }
        val conn = TransactionManager.current().connection.connection as java.sql.Connection
        conn.prepareStatement("""
        INSERT INTO message (id, timestamp, content, embedding)
        VALUES (?, ?, ?, ?::vector)
        ON CONFLICT (id) DO NOTHING
    """.trimIndent()).use { stmt ->
            stmt.setString(1, message.id.toString())
            stmt.setObject(2, message.timestamp.toJavaInstant().atOffset(java.time.ZoneOffset.UTC))
            stmt.setString(3, message.content)
            stmt.setString(4, embeddingStr)
            stmt.executeUpdate()
        }
    }

    suspend fun findById(id: UUID): Message? = dbQuery {
        MessageTable.selectAll().where { MessageTable.id eq id.toString() }.singleOrNull().let { it?.toMessage() }
    }


    suspend fun semanticSearch(queryEmbedding: List<Float>, limit: Int = 10): List<Pair<Message, Double>> = dbQuery {
        require(queryEmbedding.isNotEmpty()) { "Query embedding must not be empty" }
        require(limit > 0) { "Limit must be positive" }

        val embeddingStr = "[${queryEmbedding.joinToString(",")}]"
        val results = mutableListOf<Pair<Message, Double>>()

        val conn = TransactionManager.current().connection.connection as java.sql.Connection

        conn.prepareStatement(
            """
            SELECT id, timestamp, content,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM message
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
                val pgVector = PGobject().apply {
                type = "vector"
                value = embeddingStr
            }
            stmt.setObject(1, pgVector)
            stmt.setObject(2, pgVector)
            stmt.setInt(3, limit)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                results.add(rs.toMessage() to rs.getDouble("similarity"))
            }
        }

        results
    }

    private fun ResultRow.toMessage() = Message(
        id = UUID.fromString(  this[MessageTable.id]),
        timestamp = this[MessageTable.timestamp],
        content = this[MessageTable.content]
    )

    private fun ResultSet.toMessage() = Message(
        id = getString("id").let { UUID.fromString(it) },
        timestamp = getTimestamp("timestamp").toInstant().toKotlinInstant(),
        content = getString("content"),
    )
}
