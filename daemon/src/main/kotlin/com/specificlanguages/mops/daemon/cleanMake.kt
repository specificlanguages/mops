package com.specificlanguages.mops.daemon

import jetbrains.mps.project.facets.GenerationTargetFacet
import org.jetbrains.mps.openapi.module.SModule

/**
 * The folders the make writes generated files of [module] into: the output and output cache roots of each of its
 * models, for every generation target facet of the module. Must run inside a read action.
 */
internal fun generatedOutputRoots(module: SModule): Set<String> {
    val facets = module.facets.filterIsInstance<GenerationTargetFacet>()
    return module.models.flatMapTo(HashSet()) { model ->
        facets.flatMap { listOfNotNull(it.getOutputRoot(model), it.getOutputCacheRoot(model)) }.map { it.path }
    }
}

/**
 * The modules of [candidates] whose output roots in [roots] neither equal, contain, nor lie inside an output root of
 * another module in [roots]. A clean make treats every file under a made model's output root as stale and deletes
 * what the make does not write again, so it is safe only for a module whose output folders hold nothing but its own
 * generated files; mops always generates every model of a module it makes.
 */
internal fun <M> modulesWithOwnOutputRoots(candidates: Collection<M>, roots: Map<M, Set<String>>): Set<M> =
    candidates.filterTo(LinkedHashSet()) { candidate ->
        val own = roots[candidate].orEmpty()
        roots.none { (other, otherRoots) ->
            other != candidate && own.any { root -> otherRoots.any { overlaps(root, it) } }
        }
    }

private fun overlaps(a: String, b: String): Boolean {
    val dirA = a.trimEnd('/') + "/"
    val dirB = b.trimEnd('/') + "/"
    return dirA.startsWith(dirB) || dirB.startsWith(dirA)
}
