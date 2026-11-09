package com.specificlanguages.mops.cli

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

private const val MAX_DEPTH_BELOW = 3
private const val MAX_PROJECTS_BELOW = 3
private const val MAX_ENTRIES_READ = 10_000

/** Whether this directory is an MPS project, that is, holds a `.mps` directory. */
internal fun Path.isMpsProject(): Boolean = resolve(".mps").isDirectory()

/**
 * Returns up to 3 MPS projects at most 3 levels below [root], nearest first.
 *
 * Skips hidden directories and symbolic links, and stops after reading 10,000 directory entries in total.
 */
internal fun findMpsProjectsBelow(root: Path): List<Path> {
    val found = mutableListOf<Path>()
    val budget = EntryBudget(MAX_ENTRIES_READ)
    var level = listOf(root)
    repeat(MAX_DEPTH_BELOW) {
        level = level.flatMap { subdirectories(it, budget) }
        for (directory in level) {
            if (found.size == MAX_PROJECTS_BELOW) return found
            if (directory.isMpsProject()) found.add(directory)
        }
    }
    return found
}

private class EntryBudget(var remaining: Int)

private fun subdirectories(directory: Path, budget: EntryBudget): List<Path> {
    val entries = runCatching {
        Files.newDirectoryStream(directory).use { stream ->
            stream.asSequence().take(budget.remaining).toList()
        }
    }.getOrDefault(emptyList())
    budget.remaining -= entries.size
    return entries
        .filter { !it.name.startsWith(".") && it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
        .sorted()
}
