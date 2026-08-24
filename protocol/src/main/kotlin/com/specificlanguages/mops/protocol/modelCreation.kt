package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable enum class ModelPersistence { SINGLE_FILE, FILE_PER_ROOT }

@Serializable data class ModelCreationPlan(
    val modelName: String,
    val moduleName: String,
    val moduleReference: String,
    val persistence: ModelPersistence,
    val location: String,
)

@Serializable data class ModelCreationReport(
    val modelName: String,
    val modelReference: String,
    val moduleName: String,
    val moduleReference: String,
    val persistence: ModelPersistence,
    val location: String,
)
