package com.example.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val fields: Map<String, String>? = null,
    val timestamp: String,
    val path: String
)
