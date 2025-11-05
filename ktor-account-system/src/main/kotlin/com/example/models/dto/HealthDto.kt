package com.example.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val services: Map<String, String>
)
