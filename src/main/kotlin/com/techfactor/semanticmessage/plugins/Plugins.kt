package com.techfactor.semanticmessage.plugins

import com.techfactor.semanticmessage.model.UUIDSerializer
import com.techfactor.semanticmessage.repository.MessageRepository
import com.techfactor.semanticmessage.OllamaService
import com.techfactor.semanticmessage.service.MessageService
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.serialization.modules.contextual

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                contextual(UUIDSerializer)
            }
        })
    }
}

fun Application.configureHTTP() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error"))
            )
        }
    }
}

fun Application.configureDatabase(): MessageRepository {

    val url = environment.config.property("db.url").getString()
    val driver = environment.config.property("db.driver").getString()
    val user = environment.config.property("db.user").getString()
    val pass = environment.config.property("db.password").getString()

    val config = HikariConfig().apply {
        jdbcUrl = url
        driverClassName = driver
        username = user
        password = pass
        maximumPoolSize = 10
    }
    Database.connect(HikariDataSource(config))
    val repository = MessageRepository()
    repository.init()
    return repository
}

fun Application.configureOllama(): OllamaService {
    val baseUrl = environment.config.property("ollama.baseUrl").getString()
    val service = OllamaService(baseUrl)
    monitor.subscribe(ApplicationStopped) { service.close() }
    return service
}
