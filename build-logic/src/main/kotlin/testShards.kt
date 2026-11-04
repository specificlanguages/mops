import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * Runs one shard of this task's tests when the `testShard` Gradle property is `<index>/<count>`, for example
 * `-PtestShard=2/8`. Without the property, or with `1/1`, the task runs all its tests.
 *
 * [durations] lists measured test durations, one `<seconds> <test>` line each, where `<test>` is a fully qualified test
 * class name or `<class>.<method>`. Blank lines and lines starting with `#` are ignored.
 * `.github/scripts/test-durations.sh` writes the file from JUnit XML reports. The listed tests go to the shards longest
 * first, each to the shard with the smallest total so far. Tests that the file does not list run in the shard with the
 * smallest total, so every test runs in exactly one shard.
 */
fun Test.shardTests(durations: File) {
    val property = project.providers.gradleProperty("testShard").orNull ?: return
    val match = Regex("""(\d+)/(\d+)""").matchEntire(property)
    val index = match?.groupValues?.get(1)?.toInt()
    val count = match?.groupValues?.get(2)?.toInt()
    if (index == null || count == null || index !in 1..count) {
        throw GradleException("testShard must be <index>/<count> with 1 <= index <= count, not '$property'")
    }
    val shards = assignShards(readDurations(durations), count)
    val unlisted = shards.indices.minBy { shards[it].totalSeconds() }
    if (index - 1 == unlisted) {
        shards.filterIndexed { shard, _ -> shard != unlisted }.flatten().forEach { filter.excludeTestsMatching(it.test) }
    } else if (shards[index - 1].isEmpty()) {
        throw GradleException("Shard $property has no tests in ${durations.name}; use fewer shards")
    } else {
        shards[index - 1].forEach { filter.includeTestsMatching(it.test) }
    }
}

private data class TestDuration(val test: String, val seconds: Double)

private fun List<TestDuration>.totalSeconds() = sumOf { it.seconds }

private fun readDurations(file: File): List<TestDuration> =
    file.readLines()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val seconds = line.substringBefore(' ').toDoubleOrNull()
                ?: throw GradleException("${file.name}: expected '<seconds> <test>', not '$line'")
            TestDuration(line.substringAfter(' ').trim(), seconds)
        }

private fun assignShards(tests: List<TestDuration>, count: Int): List<List<TestDuration>> {
    val shards = List(count) { mutableListOf<TestDuration>() }
    tests.sortedWith(compareByDescending<TestDuration> { it.seconds }.thenBy { it.test }).forEach { test ->
        shards.minBy { it.totalSeconds() }.add(test)
    }
    return shards
}
