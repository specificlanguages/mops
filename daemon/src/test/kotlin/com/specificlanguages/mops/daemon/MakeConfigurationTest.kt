package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import jetbrains.mps.baseLanguage.tuples.runtime.Tuples
import jetbrains.mps.generator.DefaultModifiableGenerationSettings
import jetbrains.mps.generator.GenerationOptions
import jetbrains.mps.make.MakeSession
import jetbrains.mps.make.facet.ITarget
import jetbrains.mps.make.script.IPropertiesPool
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generation options one make passes to the Generate facet for a requested thread count, derived from the daemon's
 * generation settings, and the script controller that hands them to the facet's `configure` target.
 */
class MakeConfigurationTest {
    @Test
    fun `no thread count leaves the generation options to the facet`() {
        assertNull(makeGenerationOptions(DefaultModifiableGenerationSettings(), threads = null))
    }

    @Test
    fun `a thread count above 1 generates in parallel with that many threads`() {
        val options = makeGenerationOptions(DefaultModifiableGenerationSettings(), threads = 4)!!.create()

        assertTrue(options.isGenerateInParallel)
        assertEquals(4, options.numberOfThreads)
    }

    @Test
    fun `a thread count of 1 generates sequentially even when the settings ask for parallel generation`() {
        val settings = DefaultModifiableGenerationSettings().apply {
            isParallelGenerator = true
            numberOfParallelThreads = 8
        }

        val options = makeGenerationOptions(settings, threads = 1)!!.create()

        assertFalse(options.isGenerateInParallel)
    }

    @Test
    fun `other generation settings carry over`() {
        val settings = DefaultModifiableGenerationSettings().apply { isShowWarnings = false }

        val options = makeGenerationOptions(settings, threads = 4)!!.create()

        assertFalse(options.isShowWarnings)
    }

    @Test
    fun `a thread count below 1 is an invalid request`() {
        val failure = assertFailsWith<MpsRequestException> {
            makeGenerationOptions(DefaultModifiableGenerationSettings(), threads = 0)
        }

        assertEquals(MpsErrorCode.INVALID_REQUEST, failure.code)
    }

    @Test
    fun `parallel generation without strict mode is an invalid request instead of silently sequential`() {
        val settings = DefaultModifiableGenerationSettings().apply { isStrictMode = false }

        val failure = assertFailsWith<MpsRequestException> { makeGenerationOptions(settings, threads = 4) }

        assertEquals(MpsErrorCode.INVALID_REQUEST, failure.code)
    }

    @Test
    fun `the script controller hands the options to the Generate facet's configure target`() {
        val options = makeGenerationOptions(DefaultModifiableGenerationSettings(), threads = 3)
        val configure = mock<Tuples._2<Boolean, GenerationOptions.OptionsBuilder>>()
        val pool = mock<IPropertiesPool>()
        whenever(pool.properties(ITarget.Name("jetbrains.mps.make.facets.Generate.configure"), Any::class.java))
            .thenReturn(configure)

        makeScriptController(mock<MakeSession>(), options).setup(pool, emptyList(), emptyList())

        val passed = argumentCaptor<GenerationOptions.OptionsBuilder>()
        verify(configure)._1(passed.capture())
        val created = passed.firstValue.create()
        assertTrue(created.isGenerateInParallel)
        assertEquals(3, created.numberOfThreads)
    }

    @Test
    fun `without options the script controller leaves the property pool alone`() {
        val pool = mock<IPropertiesPool>()

        makeScriptController(mock<MakeSession>(), options = null).setup(pool, emptyList(), emptyList())

        verifyNoInteractions(pool)
    }
}
