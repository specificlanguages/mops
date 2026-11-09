package com.specificlanguages.mops.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MpsDistributionLayoutTest {

    @Test
    public void readsVersionFromBuildProperties(@TempDir Path tempDir) throws IOException {
        Path buildProperties = tempDir.resolve("build.properties");
        Files.writeString(buildProperties, "mps.build.number=MPS-213.7172.1079\n", StandardCharsets.ISO_8859_1);
        assertEquals(new MpsVersion(2021, 3), MpsDistributionLayout.readVersion(tempDir));
    }

    @Test
    public void findsJnaLibraryInArchitectureFolderOfGenericDistribution(@TempDir Path mpsHome) throws IOException {
        Files.createDirectories(mpsHome.resolve("lib/jna/aarch64"));
        Files.createDirectories(mpsHome.resolve("lib/jna/amd64"));

        assertEquals(mpsHome.resolve("lib/jna/aarch64"), MpsDistributionLayout.findJnaLibrary(mpsHome, "aarch64"));
        assertEquals(mpsHome.resolve("lib/jna/amd64"), MpsDistributionLayout.findJnaLibrary(mpsHome, "amd64"));
        // Java on Intel macOS reports os.arch as x86_64.
        assertEquals(mpsHome.resolve("lib/jna/amd64"), MpsDistributionLayout.findJnaLibrary(mpsHome, "x86_64"));
    }

    @Test
    public void findsJnaLibraryDirectlyInLibJnaOfPlatformDistribution(@TempDir Path mpsHome) throws IOException {
        Files.createDirectories(mpsHome.resolve("lib/jna"));
        Files.createFile(mpsHome.resolve("lib/jna/libjnidispatch.jnilib"));

        assertEquals(mpsHome.resolve("lib/jna"), MpsDistributionLayout.findJnaLibrary(mpsHome, "aarch64"));
    }

}
