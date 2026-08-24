package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateModelCommandTest {
    @Test
    fun `model creation forwards options and renders report`() {
        val client = mock<DaemonClient>()
        val report = ModelCreationReport("example.module.main", "r:1(example.module.main)", "example.module",
            "m:1(example.module)", ModelPersistence.FILE_PER_ROOT, "/p/models/example/module/main.model")
        whenever(client.createModel(".main", "example.module", true, false))
            .thenReturn(ModelCreationResponse(report = report))

        val output = tapSystemOut {
            assertEquals(0, CommandLine(CreateModelCommand(DaemonClientCommandEnvironment(client))).execute(
                ".main", "--module", "example.module", "--file-per-root",
            ))
        }

        verify(client).createModel(".main", "example.module", true, false)
        assertEquals("created model example.module.main [r:1(example.module.main)] in module example.module " +
            "[m:1(example.module)]\n  persistence: file-per-root\n  location: /p/models/example/module/main.model\n", output)
    }
}
