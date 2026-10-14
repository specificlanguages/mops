package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Where MPS says one **MPS Module**'s files are. Every path is reported as MPS states it; a field is null (omitted on
 * the wire) when MPS has no such location for the module, never derived from a naming convention.
 *
 * [kind] is `language`, `solution`, `generator`, `devkit`, or `other`. [packaged] is whether the module was loaded from
 * an archive. [descriptor] is the file the module was instantiated from: the `.mpl`/`.msd`/`.devkit` of a source module,
 * or `name.jar!/META-INF/module.xml` of a deployed one. For a deployed module [jar] is that archive, [sourceDescriptor]
 * the source descriptor inside the sources archive (`name-src.jar!/module/name.mpl`) and [sourcesJar] that archive; for
 * a source module [sourceDirectory] is the descriptor's directory and [classesGen] the design-time compiled-class
 * output when the module has a Java facet that declares one. [classPath] is the Java facet's full runtime classpath
 * (libraries, plus `classes_gen` or the deployed jars); [modelRoots] are the content directories of the file-based
 * model roots.
 */
@Serializable
data class ModuleLocationsJson(
    val kind: String,
    val name: String,
    val reference: String,
    val packaged: Boolean,
    val descriptor: String? = null,
    val jar: String? = null,
    val sourceDescriptor: String? = null,
    val sourcesJar: String? = null,
    val sourceDirectory: String? = null,
    val classesGen: String? = null,
    val classPath: List<String> = emptyList(),
    val modelRoots: List<String> = emptyList(),
)
