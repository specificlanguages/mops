package com.specificlanguages.mops.cli

import java.nio.file.Path
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand

/**
 * CLI entry point for resaving a persisted model through the project daemon.
 *
 * The command accepts a model file path, infers the owning MPS project by walking upward to `.mps`, then sends the
 * normalized model path to the daemon. The actual MPS write action is performed in the daemon process.
 */
@Command(name = "resave", description = ["Resave one model through the mops daemon."])
class ModelResaveCommand : Runnable {
    @ParentCommand
    lateinit var model: ModelOperations

    @Parameters(index = "0", paramLabel = "MODEL_TARGET", description = ["Persisted model path to resave."])
    lateinit var modelTarget: String

    override fun run() {
        val root = model.root
        val resolvedTarget = Path.of(modelTarget).toRealPath()

        val response = root.ensureDaemon(resolvedTarget).resave(resolvedTarget)
        println("Model resaved successfully: ${response.modelTarget}")
    }
}
