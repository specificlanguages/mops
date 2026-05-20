package com.specificlanguages.mops.launcher;

import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MpsBuildProperties(@Nullable String buildNumber) {

    public static @Nullable MpsBuildProperties fromMpsHome(Path mpsHome) {
        try {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(mpsHome.resolve("build.properties"))) {
                props.load(is);
            }

            return fromProperties(props);
        } catch (Exception e) {
            return null;
        }
    }

    public static MpsBuildProperties fromProperties(Properties props) {
        return new MpsBuildProperties(props.getProperty("mps.build.number"));
    }

    @Nullable
    public MpsVersion version() {
        if (buildNumber == null) return null;

        final Matcher matcher = Pattern.compile("^(?:[^\\-]+-)?(\\d+)\\..*$").matcher(buildNumber);
        if (!matcher.matches()) return null;

        String baseline = matcher.group(1);
        if (baseline.length() != 3) return null;

        return new MpsVersion(2000 + Integer.parseInt(baseline.substring(0, 2)),
                Integer.parseInt(baseline.substring(2, 3)));
    }
}