/**
 * Logic. The text of messages built from a module load diagnosis: root-cause lines with their fixes, the list of
 * unusable languages, and the closing line that names the one command to run.
 *
 * Hides: how a message presents module load problems and their fixes.
 */
package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadFix
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson

/**
 * Lists the root causes of a module load [problem] — the modules that must be fixed — one per line:
 * `  - <module>: <reason> — <detail>; fix: <command>`. The detail and fix appear only when known. Shared by every
 * caller that turns a diagnosis into a message.
 */
internal fun moduleLoadRootCauseLines(problem: ModuleLoadProblemJson): String =
    problem.rootCauses.joinToString("\n") { leaf ->
        "  - ${leaf.module}: ${leaf.reason}${leaf.detail?.let { " — $it" } ?: ""}${fixSuffix(leaf.fix)}"
    }

private fun fixSuffix(fix: ModuleLoadFix?): String = fix?.let { "; ${it.label}" } ?: ""

/**
 * [body] followed by the remedies, one per line: first `run '<command>', then retry` when [fix] resolves every problem
 * together, then [others]. A null [fix] adds no line, because then no single command makes the operation succeed.
 */
internal fun withRemedies(body: String, fix: ModuleLoadFix?, vararg others: String): String =
    (listOf(body) + listOfNotNull(fix?.let { "run '${it.command}', then retry" }) + others).joinToString("\n")

/**
 * [languages] as a bulleted `  - name: reason` list, one per line, each followed by the root causes of its load
 * problem — the shared body of every unusable-language message. A language without a load problem shows its own fix.
 */
fun unusableLanguageList(languages: List<UnusableLanguage>): String = buildString {
    var remaining = 5
    for (language in languages) {
        appendLine(unusableLanguageLines(language))
        for (model in language.models.take(remaining)) {
            appendLine("    Model: ${model.model}")
            appendLine("      Source: ${model.source}")
            appendLine("      Reason: ${model.reason}")
            if (model.currentHashChecked) appendLine("      Current hash: ${model.currentHash ?: "unavailable"}")
            if (model.recordedHashChecked) appendLine("      Recorded hash: ${model.recordedHash ?: "unavailable"}")
            appendLine("      Generation record: ${model.generationRecord ?: "location unavailable"}")
            remaining--
        }
    }
    val omitted = languages.sumOf { it.models.size } - (5 - remaining)
    if (omitted > 0) appendLine("  $omitted additional models require generation (details omitted).")
}.trimEnd()

// The language's own line, then the root causes of its load problem, which carry the fixes; a language without a load
// problem carries its fix on its own line.
private fun unusableLanguageLines(language: UnusableLanguage): String {
    val header = "  - ${language.name}: ${language.reason.explanation}"
    val problem = language.loadProblem ?: return header + fixSuffix(language.fix)
    return header + "\n" + moduleLoadRootCauseLines(problem)
}

/**
 * Refusal message for an operation blocked because [subject] (a model, a concept name, …) would resolve through
 * [languages] whose runtimes are unavailable or may not match their sources. Lists the observed conditions with their
 * fixes, and closes with one command when it fixes every language.
 */
fun unusableLanguagesMessage(subject: String, languages: List<UnusableLanguage>): String = withRemedies(
    "$subject cannot be used while these project language runtimes are unavailable or may not match their sources, since name-based resolution through " +
        "them may return a concept whose identity contradicts the sources:\n" +
        unusableLanguageList(languages),
    ModuleLoadFix.combine(languages.map { it.fix }),
)
