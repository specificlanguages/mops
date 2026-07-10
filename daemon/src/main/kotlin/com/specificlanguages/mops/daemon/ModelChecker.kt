package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.FindingSeverity
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.ModelCheckResponse
import jetbrains.mps.checkers.ModelCheckerBuilder
import jetbrains.mps.errors.CheckerRegistry
import jetbrains.mps.errors.MessageStatus
import jetbrains.mps.errors.item.IssueKindReportItem
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.Project
import jetbrains.mps.util.CollectConsumer
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Runs MPS's own full **Model Check** — every checker in the platform's [CheckerRegistry], i.e. typesystem plus
 * checking/constraint rules — over one model and maps the reported items to the stable finding shape.
 *
 * Read-only: the checker only reads model data, and nothing here modifies or saves the model. It must run inside a read
 * action; [JetBrainsMpsRead] provides one. Findings come back sorted most severe first and bounded by the request limit,
 * so the errors survive truncation.
 *
 * See `docs/mps/model-check.md` for the verified API contract behind this.
 */
class ModelChecker(private val persistence: PersistenceFacade = PersistenceFacade.getInstance()) {

    fun check(project: Project, model: SModel, limit: Int): ModelCheckResponse {
        val findings = collectFindings(project, model)
            .sortedBy { it.severity.ordinal }
        val selected = if (limit > 0) findings.take(limit) else findings
        return ModelCheckResponse(
            limit = limit,
            truncated = selected.size < findings.size,
            findings = selected,
        )
    }

    private fun collectFindings(project: Project, model: SModel): List<ModelCheckFindingJson> {
        val registry = project.getComponent(CheckerRegistry::class.java)
            ?: error("CheckerRegistry component is not available")

        // Restrict the check to the target model and leave stub models out, mirroring MPS's own headless checking.
        val extractor = object : ModelCheckerBuilder.ModelsExtractorImpl() {
            override fun includeModel(candidate: SModel): Boolean = candidate == model
        }
        extractor.includeStubs(false)

        val checker = ModelCheckerBuilder(extractor).createChecker(registry.checkers)
        val collector = CollectConsumer<IssueKindReportItem>()
        checker.check(
            ModelCheckerBuilder.ItemsToCheck.forSingleModel(model),
            project.repository,
            collector,
            EmptyProgressMonitor(),
        )

        return collector.result.map { toFinding(project, it) }
    }

    private fun toFinding(project: Project, item: IssueKindReportItem): ModelCheckFindingJson {
        val node = (IssueKindReportItem.PATH_OBJECT.get(item) as? IssueKindReportItem.PathObject.NodePathObject)
            ?.resolve(project.repository)
        val issueKind = item.issueKind
        return ModelCheckFindingJson(
            severity = severityOf(item.severity),
            message = item.message,
            node = node?.let { nodeSummary(it, persistence) },
            category = issueKind?.checker?.name?.takeIf(String::isNotBlank),
            rule = issueKind?.specialization?.takeIf(String::isNotBlank),
        )
    }

    private fun severityOf(status: MessageStatus): FindingSeverity =
        when (status) {
            MessageStatus.ERROR -> FindingSeverity.ERROR
            MessageStatus.WARNING -> FindingSeverity.WARNING
            MessageStatus.OK -> FindingSeverity.INFO
        }
}
