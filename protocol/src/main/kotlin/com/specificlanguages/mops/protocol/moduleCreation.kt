package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class ModuleKind { LANGUAGE, SOLUTION, DEVKIT, GENERATOR }
@Serializable enum class SolutionUsagePreset {
    @SerialName("not-generated") NOT_GENERATED,
    @SerialName("text") TEXT,
    @SerialName("java") JAVA,
    @SerialName("java-tests") JAVA_TESTS,
    @SerialName("java-mps-plugin") JAVA_MPS_PLUGIN;

    /** The kebab-case spelling used on the command line, in text output, and in JSON, e.g. `java-tests`. */
    val wireName: String get() = name.lowercase().replace('_', '-')

    companion object {
        /** Maps a command-line spelling (`not-generated`, `text`, `java`, ...) to its preset, or null if unknown. */
        fun fromWireName(value: String): SolutionUsagePreset? = entries.firstOrNull { it.wireName == value }
    }
}
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
    val persistence: GeneratorPersistence? = null, val usagePreset: SolutionUsagePreset? = null,
    val facets: List<FacetMementoJson> = emptyList(),
)
@Serializable data class ModuleCreationReport(
    val primary: ModuleCreationEntry, val companions: List<ModuleCreationEntry> = emptyList(),
)
@Serializable data class ModuleCreationPlanEntry(
    val moduleName: String, val kind: ModuleKind, val descriptorPath: String,
    val sourceLanguageReference: String? = null, val alias: String? = null,
    val persistence: GeneratorPersistence? = null, val generatorDirectory: String? = null,
    val usagePreset: SolutionUsagePreset? = null, val facets: List<FacetMementoJson> = emptyList(),
)
@Serializable data class ModuleCreationPlan(
    val primary: ModuleCreationPlanEntry, val companions: List<ModuleCreationPlanEntry> = emptyList(),
)
