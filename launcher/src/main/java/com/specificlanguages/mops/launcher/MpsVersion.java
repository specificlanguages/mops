package com.specificlanguages.mops.launcher;

public record MpsVersion(int major, int minor) implements Comparable<MpsVersion> {

    @Override
    public int compareTo(MpsVersion other) {
        int majorResult = Integer.compare(major, other.major);
        if (majorResult != 0) {
            return majorResult;
        }
        return Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }

    public boolean isAtLeast(int major, int minor) {
        return compareTo(new MpsVersion(major, minor)) >= 0;
    }

    public int requiredJavaMajor() {
        if (major >= 2026) return 25;
        if (major >= 2025) return 21;
        if (major >= 2022) return 17;

        return 11;
    }
}
