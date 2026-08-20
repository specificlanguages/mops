package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class CodeCatalogTest {
    @Test
    fun `creation help shows positional values and named options`() {
        val help = CodeCatalog.text("mops.edit.createLanguage")

        assertContains(
            help,
            "mops.edit.createLanguage(String moduleName, descriptor: String = null, withGenerator: Boolean = false)",
        )
        assertFalse("?>" in help)
    }

    @Test
    fun `module lookup is discoverable under read and edit access`() {
        assertContains(CodeCatalog.text("mops.read.getModule"), "mops.read.getModule(NavigationTarget target)")
        assertContains(CodeCatalog.text("mops.edit.getModule"), "mops.edit.getModule(NavigationTarget target)")
    }

    @Test
    fun `catalog hides implementation methods`() {
        val help = CodeCatalog.text("mops.edit")

        assertFalse("persist" in help)
        assertFalse("$" in help)
    }
}
