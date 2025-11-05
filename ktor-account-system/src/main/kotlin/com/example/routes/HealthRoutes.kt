package com.example.routes

import com.example.models.dto.HealthResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "healthy",
                timestamp = Clock.System.now().toString(),
                services = mapOf(
                    "database" to "up",
                    "redis" to "up"
                )
            )
        )
    }
}
