package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.PerformanceTracingLevel
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generation options one make passes to the Generate facet, derived from the daemon's generation settings; the
 * script controller that hands them to the facet's `configure` target; and the generation settings a make sets for
 * its duration.
 */
class MakeConfigurationTest {
    private fun options(settings: DefaultModifiableGenerationSettings, request: MakeGenerationOptions) =
        makeGenerationOptions(settings, request)!!.create()

    @Test
    fun `no session options leave the generation options to the facet`() {
        val request = MakeGenerationOptions(saveTransientModels = true, performanceTracing = PerformanceTracingLevel.STEPS)

        assertNull(makeGenerationOptions(DefaultModifiableGenerationSettings(), MakeGenerationOptions()))
        assertNull(makeGenerationOptions(DefaultModifiableGenerationSettings(), request))
    }

    @Test
    fun `a thread count above 1 generates in parallel with that many threads`() {
        val options = options(DefaultModifiableGenerationSettings(), MakeGenerationOptions(threads = 4))

        assertTrue(options.isGenerateInParallel)
        assertEquals(4, options.numberOfThreads)
    }

    @Test
    fun `a thread count of 1 generates sequentially even when the settings ask for parallel generation`() {
        val settings = DefaultModifiableGenerationSettings().apply {
            isParallelGenerator = true
            numberOfParallelThreads = 8
        }

        assertFalse(options(settings, MakeGenerationOptions(threads = 1)).isGenerateInParallel)
    }

    @Test
    fun `other generation settings carry over`() {
        val settings = DefaultModifiableGenerationSettings().apply { isShowWarnings = false }

        assertFalse(options(settings, MakeGenerationOptions(threads = 4)).isShowWarnings)
    }

    @Test
    fun `a thread count below 1 is an invalid request`() {
        val failure = assertFailsWith<MpsRequestException> {
            makeGenerationOptions(DefaultModifiableGenerationSettings(), MakeGenerationOptions(threads = 0))
        }

        assertEquals(MpsErrorCode.INVALID_REQUEST, failure.code)
    }

    @Test
    fun `parallel generation when the settings turn strict mode off is an invalid request that names --strict`() {
        val settings = DefaultModifiableGenerationSettings().apply { isStrictMode = false }

        val failure = assertFailsWith<MpsRequestException> {
            makeGenerationOptions(settings, MakeGenerationOptions(threads = 4))
        }

        assertEquals(MpsErrorCode.INVALID_REQUEST, failure.code)
        assertContains(failure.message, "--strict")
    }

    @Test
    fun `parallel generation with strict mode turned off by the request is an invalid request`() {
        val failure = assertFailsWith<MpsRequestException> {
            makeGenerationOptions(
                DefaultModifiableGenerationSettings(),
                MakeGenerationOptions(threads = 4, strict = false),
            )
        }

        assertEquals(MpsErrorCode.INVALID_REQUEST, failure.code)
    }

    @Test
    fun `strict mode from the request overrides the settings and allows parallel generation`() {
        val settings = DefaultModifiableGenerationSettings().apply { isStrictMode = false }

        val options = options(settings, MakeGenerationOptions(threads = 4, strict = true))

        assertTrue(options.isStrictMode)
        assertTrue(options.isGenerateInParallel)
    }

    @Test
    fun `the request can turn strict mode off`() {
        assertFalse(options(DefaultModifiableGenerationSettings(), MakeGenerationOptions(strict = false)).isStrictMode)
    }

    @Test
    fun `the info message level shows info and warnings`() {
        val options = options(
            DefaultModifiableGenerationSettings(),
            MakeGenerationOptions(messages = MakeMessageLevel.INFO),
        )

        assertTrue(options.isShowInfo)
        assertTrue(options.isShowWarnings)
    }

    @Test
    fun `the warning message level shows warnings but not info, even when the settings show info`() {
        val settings = DefaultModifiableGenerationSettings().apply { isShowInfo = true }

        val options = options(settings, MakeGenerationOptions(messages = MakeMessageLevel.WARNING))

        assertFalse(options.isShowInfo)
        assertTrue(options.isShowWarnings)
    }

    @Test
    fun `the error message level hides warnings, including bad-child and dynamic-reference warnings`() {
        val settings = DefaultModifiableGenerationSettings().apply { warnDynamicToStaticReference(true) }

        val options = options(settings, MakeGenerationOptions(messages = MakeMessageLevel.ERROR))

        assertFalse(options.isShowInfo)
        assertFalse(options.isShowWarnings)
        assertFalse(options.isShowBadChildWarning)
        assertFalse(options.warnDynamicToStaticFailed())
    }

    @Test
    fun `the script controller hands the options to the Generate facet's configure target`() {
        val options = makeGenerationOptions(
            DefaultModifiableGenerationSettings(),
            MakeGenerationOptions(threads = 3, messages = MakeMessageLevel.INFO),
        )
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
        assertTrue(created.isShowInfo)
    }

    @Test
    fun `without options the script controller leaves the property pool alone`() {
        val pool = mock<IPropertiesPool>()

        makeScriptController(mock<MakeSession>(), options = null).setup(pool, emptyList(), emptyList())

        verifyNoInteractions(pool)
    }

    @Test
    fun `the make runs with the requested transient-model and tracing settings and restores them afterwards`() {
        val settings = DefaultModifiableGenerationSettings()
        val request = MakeGenerationOptions(
            saveTransientModels = true,
            performanceTracing = PerformanceTracingLevel.TYPES,
        )

        val during = withMakeGenerationSettings(settings, request) {
            settings.isSaveTransientModels to settings.performanceTracingLevel
        }

        assertEquals(true to GenerationOptions.TRACE_TYPES, during)
        assertFalse(settings.isSaveTransientModels)
        assertEquals(GenerationOptions.TRACE_OFF, settings.performanceTracingLevel)
    }

    @Test
    fun `the settings are restored when the make fails`() {
        val settings = DefaultModifiableGenerationSettings().apply {
            performanceTracingLevel = GenerationOptions.TRACE_STEPS
        }
        val request = MakeGenerationOptions(saveTransientModels = true, performanceTracing = PerformanceTracingLevel.OFF)

        assertFailsWith<IllegalStateException> {
            withMakeGenerationSettings(settings, request) { error("make failed") }
        }

        assertFalse(settings.isSaveTransientModels)
        assertEquals(GenerationOptions.TRACE_STEPS, settings.performanceTracingLevel)
    }

    @Test
    fun `settings the request leaves out are not touched`() {
        val settings = DefaultModifiableGenerationSettings()

        withMakeGenerationSettings(settings, MakeGenerationOptions(saveTransientModels = true)) {
            settings.performanceTracingLevel = GenerationOptions.TRACE_LANGS
        }

        assertFalse(settings.isSaveTransientModels)
        assertEquals(GenerationOptions.TRACE_LANGS, settings.performanceTracingLevel)
    }
}
