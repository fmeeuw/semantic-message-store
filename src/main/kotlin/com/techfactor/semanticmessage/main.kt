package com.techfactor.semanticmessage

import com.techfactor.semanticmessage.plugins.configureDatabase
import com.techfactor.semanticmessage.plugins.configureHTTP
import com.techfactor.semanticmessage.plugins.configureOllama
import com.techfactor.semanticmessage.plugins.configureSerialization
import com.techfactor.semanticmessage.service.MessageService
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        environment = applicationEnvironment {
            config = HoconApplicationConfig(ConfigFactory.load())
        },
        configure = {
            connector {
                port = 8090
            }
        },
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureHTTP()
    val repository = configureDatabase()
    val ollamaService = configureOllama()
    val messageService = MessageService(repository, ollamaService)
    configureRouting(messageService)
}
