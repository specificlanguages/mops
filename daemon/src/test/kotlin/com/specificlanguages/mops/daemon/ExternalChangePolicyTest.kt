package com.specificlanguages.mops.daemon

import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.structure.modules.ModuleReference
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalChangePolicyTest {
    private val a = Path.of("/libs/a.jar")
    private val b = Path.of("/libs/b.jar")
    private val c = Path.of("/libs/c.jar")

    @Test
    fun `the first refresh refreshes every archive`() {
        assertEquals(setOf(a, b), archivesToRefresh(null, mapOf(a to ArchiveStamp(1, 1), b to null)))
    }

    @Test
    fun `later refreshes refresh archives whose stamp changed`() {
        val previous = mapOf(a to ArchiveStamp(1, 1), b to ArchiveStamp(1, 1), c to null)
        val current = mapOf(
            a to ArchiveStamp(1, 1),
            b to ArchiveStamp(2, 1),
            c to ArchiveStamp(1, 1),
            Path.of("/libs/new.jar") to ArchiveStamp(1, 1),
            Path.of("/libs/missing.jar") to null,
        )
        assertEquals(setOf(b, c, Path.of("/libs/new.jar")), archivesToRefresh(previous, current))
    }

    @Test
    fun `stored files are loaded once per folder`() {
        assertEquals(listOf(b, c), foldersToLoad(setOf(a), listOf(a, b, c, b)))
    }

    @Test
    fun `modules whose class path or its contents changed are reloaded`() {
        val unchanged = module("unchanged")
        val newFingerprint = module("newFingerprint")
        val newRoot = module("newRoot")
        val added = module("added")
        val previous = mapOf(
            unchanged to listOf(ClassPathRoot(a, "1")),
            newFingerprint to listOf(ClassPathRoot(a, "1")),
            newRoot to listOf(ClassPathRoot(a, "1")),
            module("removed") to listOf(ClassPathRoot(a, "1")),
        )
        val current = mapOf(
            unchanged to listOf(ClassPathRoot(a, "1")),
            newFingerprint to listOf(ClassPathRoot(a, "2")),
            newRoot to listOf(ClassPathRoot(a, "1"), ClassPathRoot(b, "1")),
            added to listOf(ClassPathRoot(a, "1")),
        )
        assertEquals(setOf(newFingerprint, newRoot, added), modulesToReload(previous, current))
    }

    private fun module(name: String): SModuleReference = ModuleReference(name, ModuleId.regular())
}
