package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ModuleLoadDiagnosticJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.language.SAbstractConcept

/**
 * Resolves a concept name for `find instances` and, when it does not resolve, replaces the generic "not found" with a
 * targeted explanation.
 *
 * A concept's qualified name is `<language>.structure.<ConceptName>`, and name lookup only sees concepts of loaded
 * languages (see [ModuleLoadDiagnostics]). So a lookup can fail for three distinct reasons, each with its own remedy:
 * the name is not well formed, the owning language is unknown or not loaded, or the language is loaded but has no such
 * concept. This resolver tells them apart. It also forgives a dropped `.structure.` infix: given `foo.bar.Baz` it
 * retries `foo.bar.structure.Baz`, so a caller who omits the infix still gets results when the language is loaded.
 *
 * Must run inside an MPS read action.
 */
class ConceptResolver(private val project: Project) {

    private val conceptRegistry: ConceptRegistry = ConceptRegistry.getInstance()
    private val languageRegistry: LanguageRegistry = project.getComponent(LanguageRegistry::class.java)

    /** The resolved concept, or an [MpsRequestException] carrying a diagnosis of why the name did not resolve. */
    fun resolve(name: String): SAbstractConcept {
        conceptRegistry.getConceptByName(name).takeIf { it.isValid }?.let { return it }

        // Forgive a dropped `.structure.`: treat the whole prefix as the language and insert the infix before the
        // last segment. This tries the interpretation before parse() picks one for the diagnosis, so a language whose
        // own name ends in `.structure` (e.g. jetbrains.mps.lang.structure) still resolves from the short form.
        droppedInfixCandidate(name)?.takeIf { it != name }?.let { candidate ->
            conceptRegistry.getConceptByName(candidate).takeIf { it.isValid }?.let { return it }
        }

        val parsed = ConceptName.parse(name)
            ?: throw notFound(malformedMessage(name))
        throw notFound(diagnose(parsed))
    }

    private fun droppedInfixCandidate(name: String): String? {
        val shortName = name.substringAfterLast('.', "")
        val language = name.substringBeforeLast('.', "")
        if (shortName.isEmpty() || language.isEmpty()) return null
        return "$language.structure.$shortName"
    }

    private fun diagnose(parsed: ConceptName): String {
        val diagnostic: ModuleLoadDiagnosticJson =
            ModuleLoadDiagnostics(project).diagnoseModule(parsed.language).module
        return when {
            !diagnostic.present -> unknownLanguageMessage(parsed)
            !diagnostic.loaded -> unloadedLanguageMessage(parsed, diagnostic.problem)
            else -> loadedButMissingMessage(parsed, similarConceptNames(parsed))
        }
    }

    /** Short names of the loaded language's concepts that resemble the queried one, closest first. */
    private fun similarConceptNames(parsed: ConceptName): List<String> {
        val language = languageRegistry.allLanguages.firstOrNull { it.qualifiedName == parsed.language }
            ?: return emptyList()
        return language.concepts
            .mapNotNull { it.name.takeIf(String::isNotBlank) }
            .distinct()
            .mapNotNull { candidate -> similarity(parsed.shortName, candidate)?.let { candidate to it } }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }

    private fun notFound(message: String): MpsRequestException =
        MpsRequestException(code = MpsErrorCode.CONCEPT_NOT_FOUND, message = message)

    companion object {
        private const val MAX_SUGGESTIONS = 5

        fun malformedMessage(name: String): String =
            "\"$name\" is not a well-formed concept name; expected <language>.structure.<ConceptName> " +
                "(for example com.example.structure.MyConcept)"

        fun unknownLanguageMessage(parsed: ConceptName): String =
            "concept \"${parsed.qualifiedName}\" was not found: its language \"${parsed.language}\" is not a module " +
                "known to this project — check the language part of the name"

        fun unloadedLanguageMessage(parsed: ConceptName, problem: ModuleLoadProblemJson?): String = buildString {
            append(
                "concept \"${parsed.qualifiedName}\" was not found because its language \"${parsed.language}\" is " +
                    "not loaded",
            )
            if (problem != null) {
                append(":\n")
                append(rootCauseLines(problem))
            }
            append("\nrun 'mops diagnose module ${parsed.language}' for the full dependency tree")
        }

        fun loadedButMissingMessage(parsed: ConceptName, suggestions: List<String>): String =
            if (suggestions.isEmpty()) {
                "concept \"${parsed.shortName}\" was not found in language \"${parsed.language}\", which is loaded, " +
                    "and it has no concept with a similar name"
            } else {
                "concept \"${parsed.shortName}\" was not found in language \"${parsed.language}\", which is loaded; " +
                    "did you mean: ${suggestions.joinToString(", ")}?"
            }

        /** The root modules to fix: the leaves of the problem tree, one per line, de-duplicated. */
        private fun rootCauseLines(problem: ModuleLoadProblemJson): String =
            leaves(problem).distinctBy { it.module to it.reason }.joinToString("\n") { leaf ->
                "  - ${leaf.module}: ${leaf.reason}${leaf.detail?.let { " — $it" } ?: ""}"
            }

        private fun leaves(problem: ModuleLoadProblemJson): List<ModuleLoadProblemJson> =
            if (problem.causes.isEmpty()) listOf(problem) else problem.causes.flatMap(::leaves)

        /** A closeness rank (lower is closer) for a candidate concept name, or null when it is not similar enough. */
        private fun similarity(query: String, candidate: String): Int? {
            val q = query.lowercase()
            val c = candidate.lowercase()
            val distance = levenshtein(q, c)
            val threshold = maxOf(2, query.length / 3)
            val contains = c.contains(q) || q.contains(c)
            return if (distance <= threshold || contains) distance else null
        }

        private fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}

/**
 * A concept name split into its owning language and short concept name. [parse] accepts both the canonical
 * `<language>.structure.<ConceptName>` form and the `<language>.<ConceptName>` form with the `.structure.` infix
 * dropped; [qualifiedName] always rebuilds the canonical form.
 *
 * The concept name is the last dotted segment; the language is the rest, with a trailing `.structure` (the canonical
 * infix) stripped. So `a.b.structure.Foo` and `a.b.Foo` both parse to language `a.b`.
 */
data class ConceptName(val language: String, val shortName: String) {
    val qualifiedName: String get() = "$language.structure.$shortName"

    companion object {
        private const val INFIX = ".structure"

        fun parse(input: String): ConceptName? {
            val shortName = input.substringAfterLast('.', "")
            val prefix = input.substringBeforeLast('.', "")
            if (shortName.isEmpty() || prefix.isEmpty()) return null
            val language = prefix.removeSuffix(INFIX)
            if (language.isEmpty()) return null
            return ConceptName(language, shortName)
        }
    }
}
