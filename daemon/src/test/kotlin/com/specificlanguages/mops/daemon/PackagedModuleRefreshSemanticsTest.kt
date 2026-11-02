package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class PackagedModuleRefreshSemanticsTest {

    @Test
    fun `refresh picks up a replaced packaged module jar from a project library`() {
        SharedMpsEnvironment.withProjectCopy(prepare = { projectPath ->
            projectPath.resolve(".mps/libraries.xml").writeText(LIBRARIES_XML)
            writeLibraryJar(projectPath.resolve(LIBRARY_JAR), "VersionOne", entryTime = Instant.parse("2026-09-18T12:00:00Z"))
        }) { access, projectPath ->
            assertEquals("VersionOne", rootName(access))

            writeLibraryJar(projectPath.resolve(LIBRARY_JAR), "VersionTwoChanged", entryTime = Instant.parse("2026-10-30T12:00:00Z"))

            access.extra { refreshExternalChanges() }

            assertEquals("VersionTwoChanged", rootName(access), "refresh reloads the replaced packaged model")
        }
    }

    private fun rootName(access: MpsAccess): String =
        propertyValue(access.read { getNode(NodeTarget.InModel(MODEL_NAME, ROOT_NODE_ID)) }, "name")

    // MPS reloads a packaged model only when its archive entry timestamp changes, so each version gets its own.
    private fun writeLibraryJar(jar: Path, rootName: String, entryTime: Instant) {
        jar.parent.createDirectories()
        val temporary = Files.createTempFile(jar.parent, "library", ".tmp")
        ZipOutputStream(temporary.outputStream()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name).apply { time = entryTime.toEpochMilli() })
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("META-INF/module.xml", MODULE_XML)
            entry("module/repro.lib.msd", SOLUTION_DESCRIPTOR)
            entry("module/models/repro.lib.mps", model(rootName))
        }
        // Replaced like a Gradle Sync replaces a distribution: a new file at the same path.
        Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun model(rootName: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <model ref="r:5d1c6b7e-2222-4a2b-9c3d-000000000002($MODEL_NAME)">
          <persistence version="9" />
          <languages>
            <use id="f3f42ddf-d692-4c29-90fb-7360196f01ab" name="com.specificlanguages.json" version="0" />
          </languages>
          <imports />
          <registry>
            <language id="f3f42ddf-d692-4c29-90fb-7360196f01ab" name="com.specificlanguages.json">
              <concept id="2110045694544566904" name="com.specificlanguages.json.structure.JsonFile" flags="ng" index="IoV${'$'}p" />
            </language>
            <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
              <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ngI" index="TrEIO">
                <property id="1169194664001" name="name" index="TrG5h" />
              </concept>
            </language>
          </registry>
          <node concept="IoV${'$'}p" id="$ROOT_NODE_ID">
            <property role="TrG5h" value="$rootName" />
          </node>
        </model>
    """.trimIndent()

    private companion object {
        const val LIBRARY_JAR = "lib/repro.lib.jar"
        const val MODEL_NAME = "repro.lib"
        const val ROOT_NODE_ID = "2"

        val LIBRARIES_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ProjectLibraryManager">
                <option name="libraries">
                  <map>
                    <entry key="repro">
                      <value>
                        <Library>
                          <option name="name" value="repro" />
                          <option name="path" value="${'$'}PROJECT_DIR${'$'}/lib" />
                        </Library>
                      </value>
                    </entry>
                  </map>
                </option>
              </component>
            </project>
        """.trimIndent()

        val MODULE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <module namespace="repro.lib" type="solution" uuid="5d1c6b7e-1111-4a2b-9c3d-000000000001">
              <dependencies />
              <uses>
                <language id="l:f3f42ddf-d692-4c29-90fb-7360196f01ab:com.specificlanguages.json" />
              </uses>
              <sources descriptor="repro.lib.msd" jar="." />
            </module>
        """.trimIndent()

        val SOLUTION_DESCRIPTOR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <solution name="repro.lib" uuid="5d1c6b7e-1111-4a2b-9c3d-000000000001" moduleVersion="0">
              <models>
                <modelRoot contentPath="${'$'}{module}" type="default">
                  <sourceRoot location="models" />
                </modelRoot>
              </models>
              <languageVersions>
                <language slang="l:f3f42ddf-d692-4c29-90fb-7360196f01ab:com.specificlanguages.json" version="0" />
                <language slang="l:ceab5195-25ea-4f22-9b92-103b95ca8c0c:jetbrains.mps.lang.core" version="2" />
              </languageVersions>
            </solution>
        """.trimIndent()
    }
}
