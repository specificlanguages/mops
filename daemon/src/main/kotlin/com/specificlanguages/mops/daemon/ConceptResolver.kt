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
 * languages (see [ModuleLoadDiagnostics]). A caller may name a concept three ways: by its qualified name, by the
 * qualified name with the `.structure.` infix dropped, or by its bare short name. The first two are resolved through
 * MPS's by-name index; the bare short name is resolved by counting matches across all loaded languages, since the same
 * short name may belong to more than one language.
 *
 * A lookup can therefore fail five ways, each with its own remedy: a bare short name is offered while a project language
 * is unbuilt (so counting cannot be trusted); a bare short name is ambiguous across languages; a bare short name matches
 * nothing; a qualified name's language is unknown or not loaded; or the language is loaded but has no such concept. This
 * resolver tells them apart. It also forgives a dropped `.structure.` infix: given `foo.bar.Baz` it retries
 * `foo.bar.structure.Baz`, so a caller who omits the infix still gets results when the language is loaded.
 *
 * Must run inside an MPS read action.
 */
class ConceptResolver(private val project: Project) {

    private val conceptRegistry: ConceptRegistry = ConceptRegistry.getInstance()
    private val languageRegistry: LanguageRegistry = project.getComponent(LanguageRegistry::class.java)

    /** The resolved concept, or an [MpsRequestException] carrying a diagnosis of why the name did not resolve. */
    fun resolve(name: String): SAbstractConcept {
        resolveQualified(name)?.let { return it }

        // A bare short name (no dots) never keys MPS's qualified-name index, so resolve it by counting matches across
        // loaded languages instead. A dotted name that reaches here is a qualified attempt whose language is the more
        // useful thing to diagnose.
        if (isBareShortName(name)) return resolveShortName(name)

        val parsed = ConceptName.parse(name)
            ?: throw notFound(malformedMessage(name))
        throw notFound(diagnose(parsed))
    }

    /** Resolves a qualified name in either spelling through MPS's by-name index, or null when neither spelling hits. */
    private fun resolveQualified(name: String): SAbstractConcept? {
        conceptRegistry.getConceptByName(name).takeIf { it.isValid }?.let { return it }

        // Forgive a dropped `.structure.`: treat the whole prefix as the language and insert the infix before the
        // last segment. This tries the interpretation before parse() picks one for the diagnosis, so a language whose
        // own name ends in `.structure` (e.g. jetbrains.mps.lang.structure) still resolves from the short form.
        droppedInfixCandidate(name)?.takeIf { it != name }?.let { candidate ->
            conceptRegistry.getConceptByName(candidate).takeIf { it.isValid }?.let { return it }
        }
        return null
    }

    /**
     * Resolves a bare short name by counting concepts of that name across all loaded languages: a unique match wins, a
     * tie fails with the qualified candidates as retry tokens, and no match fails with near-miss suggestions.
     *
     * The count is trustworthy only for two or more matches: that is ambiguous no matter what, since an unbuilt language
     * could only add further candidates, never collapse these to one. For zero or one match the verdict is provisional —
     * an unbuilt project language contributes no concepts to name lookup, so it could hold the deciding concept unseen
     * and turn "unique" or "not found" wrong. So when the count is under two and any project language is unbuilt, the
     * resolution refuses, naming the languages to build (or asking for a qualified name) instead of guessing.
     */
    private fun resolveShortName(shortName: String): SAbstractConcept {
        val matches = conceptsNamed(shortName)
        if (matches.size >= 2) {
            throw ambiguous(ambiguousShortNameMessage(shortName, matches.map { it.qualifiedName }.sorted()))
        }

        val unbuilt = ModuleLoadDiagnostics(project).unbuiltProjectLanguages()
        if (unbuilt.isNotEmpty()) throw languageNotLoaded(unbuiltShortNameMessage(shortName, unbuilt))

        return matches.singleOrNull()
            ?: throw notFound(shortNameNotFoundMessage(shortName, similarConceptNamesAcrossLanguages(shortName)))
    }

    /** Concepts of every loaded language whose short name is exactly [shortName], one per qualified name. */
    private fun conceptsNamed(shortName: String): List<SAbstractConcept> =
        allConcepts().filter { it.name == shortName }.distinctBy { it.qualifiedName }

    /** Qualified names of loaded concepts whose short name resembles [shortName], closest first. */
    private fun similarConceptNamesAcrossLanguages(shortName: String): List<String> =
        rankBySimilarity(shortName, allConcepts()) { it.qualifiedName }

    /** Every concept of every loaded language. */
    private fun allConcepts(): List<SAbstractConcept> =
        languageRegistry.allLanguages.flatMap { it.concepts }

    /**
     * The [project]ed names of [concepts] whose short name resembles [query], closest first and one per projected
     * name, capped at [MAX_SUGGESTIONS].
     */
    private fun rankBySimilarity(
        query: String,
        concepts: Iterable<SAbstractConcept>,
        project: (SAbstractConcept) -> String,
    ): List<String> =
        concepts
            .mapNotNull { concept ->
                concept.name.takeIf(String::isNotBlank)
                    ?.let { name -> similarity(query, name)?.let { project(concept) to it } }
            }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(MAX_SUGGESTIONS)
            .map { it.first }

    private fun isBareShortName(name: String): Boolean = name.isNotBlank() && !name.contains('.')

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
        return rankBySimilarity(parsed.shortName, language.concepts) { it.name }
    }

    private fun notFound(message: String): MpsRequestException =
        MpsRequestException(code = MpsErrorCode.CONCEPT_NOT_FOUND, message = message)

    private fun ambiguous(message: String): MpsRequestException =
        MpsRequestException(code = MpsErrorCode.AMBIGUOUS_TARGET, message = message)

    private fun languageNotLoaded(message: String): MpsRequestException =
        MpsRequestException(code = MpsErrorCode.LANGUAGE_NOT_LOADED, message = message)

    companion object {
        private const val MAX_SUGGESTIONS = 5

        fun unbuiltShortNameMessage(shortName: String, unbuiltLanguages: List<String>): String =
            "the short concept name \"$shortName\" cannot be resolved while these project languages are not built, " +
                "since any of them may also define it:\n" +
                unbuiltLanguages.joinToString("\n") { "  - $it" } +
                "\nbuild them (for example 'mops make ${unbuiltLanguages.first()}') or use a qualified concept name."

        fun ambiguousShortNameMessage(shortName: String, qualifiedCandidates: List<String>): String =
            "concept name \"$shortName\" is ambiguous; it names ${qualifiedCandidates.size} concepts across loaded " +
                "languages. Retry with one qualified name:\n" +
                qualifiedCandidates.joinToString("\n") { "  $it" }

        fun shortNameNotFoundMessage(shortName: String, suggestions: List<String>): String =
            if (suggestions.isEmpty()) {
                "concept \"$shortName\" was not found in any loaded language, and no concept has a similar name"
            } else {
                "concept \"$shortName\" was not found in any loaded language; " +
                    "did you mean: ${suggestions.joinToString(", ")}?"
            }

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
                append(moduleLoadRootCauseLines(problem))
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
