package com.specificlanguages.mops.cli.get

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModuleLocationsJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "module",
    header = ["Report where MPS locates one module's files."],
    description = ["Report where MPS locates one module's descriptor, jars, class output, and model sources."],
    footer = [
        "",
        "The module is found anywhere in the repository. Text output is one `key<TAB>value` row per location MPS " +
            "reports, keyed by the JSON field name: `kind`, `name`, `reference`, `packaged`, then `descriptor` (the " +
            ".mpl/.msd/.devkit of a source module, or `name.jar!/META-INF/module.xml` of a deployed one), `jar` and " +
            "`sourcesJar` (the deployed module's archive and the sources archive its deployment descriptor names), " +
            "`sourceDescriptor` (the source descriptor inside that archive), `sourceDirectory` (a source module's " +
            "descriptor directory), `classesGen` (a source module's compiled-class output, when its Java facet " +
            "declares one), `classPath` (one row per entry of the Java facet's runtime classpath), and `modelRoots` " +
            "(one row per file-based model root).",
        "",
        "A location MPS does not record for the module is left out rather than guessed from a naming convention. " +
            "--json prints the daemon response, with the same fields under `module`. An unknown module fails with " +
            "MODULE_NOT_FOUND; a name shared by several modules fails and lists their serialized references, so a " +
            "reference disambiguates it.",
    ],
)
class GetModuleCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(names = ["--json"], description = ["Print the module's locations as JSON."])
    var json: Boolean = false

    @Parameters(
        index = "0",
        arity = "1",
        paramLabel = "MODULE",
        description = ["Module name or serialized module reference; library modules included."],
    )
    lateinit var module: String

    override fun run() {
        val response = environment.daemon().getModule(module)

        if (json) {
            println(renderJson(response))
            return
        }
        rows(response.module).forEach { (key, value) -> println("$key\t$value") }
    }

    // One row per present field, keyed by the JSON field name so text and JSON output read the same; a list field
    // repeats its key once per entry, and a field MPS has no value for is left out rather than printed empty.
    private fun rows(locations: ModuleLocationsJson): List<Pair<String, String>> = buildList {
        add("kind" to locations.kind)
        add("name" to locations.name)
        add("reference" to locations.reference)
        locations.packaged?.let { add("packaged" to it.toString()) }
        locations.descriptor?.let { add("descriptor" to it) }
        locations.jar?.let { add("jar" to it) }
        locations.sourceDescriptor?.let { add("sourceDescriptor" to it) }
        locations.sourcesJar?.let { add("sourcesJar" to it) }
        locations.sourceDirectory?.let { add("sourceDirectory" to it) }
        locations.classesGen?.let { add("classesGen" to it) }
        locations.classPath.forEach { add("classPath" to it) }
        locations.modelRoots.forEach { add("modelRoots" to it) }
    }
}
