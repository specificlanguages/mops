package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable enum class ModuleKind { LANGUAGE, SOLUTION, DEVKIT, GENERATOR }
@Serializable enum class SolutionUsagePreset { NOT_GENERATED, TEXT, JAVA, JAVA_TESTS, JAVA_MPS_PLUGIN }
@Serializable enum class GeneratorPersistence { EMBEDDED, STANDALONE }

@Serializable data class MementoJson(
    val type: String? = null,
    val values: Map<String, String> = emptyMap(),
    val text: String? = null,
    val children: List<MementoJson> = emptyList(),
)
@Serializable data class FacetMementoJson(
    val type: String,
    val values: Map<String, String> = emptyMap(),
    val text: String? = null,
    val children: List<MementoJson> = emptyList(),
)
@Serializable data class ModuleCreationEntry(
    val moduleReference: String, val moduleName: String, val kind: ModuleKind, val descriptorPath: String,
    val sourceLanguageReference: String? = null, val alias: String? = null,
    val persistence: GeneratorPersistence? = null, val facets: List<FacetMementoJson> = emptyList(),
)
@Serializable data class ModuleCreationReport(
    val primary: ModuleCreationEntry, val companions: List<ModuleCreationEntry> = emptyList(),
)
@Serializable data class ModuleCreationPlanEntry(
    val moduleName: String, val kind: ModuleKind, val descriptorPath: String,
    val sourceLanguageReference: String? = null, val alias: String? = null,
    val persistence: GeneratorPersistence? = null, val generatorDirectory: String? = null,
    val facets: List<FacetMementoJson> = emptyList(),
)
@Serializable data class ModuleCreationPlan(
    val primary: ModuleCreationPlanEntry, val companions: List<ModuleCreationPlanEntry> = emptyList(),
)
