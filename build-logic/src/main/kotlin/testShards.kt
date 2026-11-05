import org.gradle.api.GradleException
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.testing.Test
import java.util.zip.CRC32

/**
 * Runs one shard of this task's tests when the `testShard` Gradle property is `<index>/<count>`, for example
 * `-PtestShard=2/7`. Without the property the task runs all its tests.
 *
 * A test class runs in shard `testShardOf(<fully qualified class name>, count) + 1`, so every class runs in exactly one
 * shard. The task still compiles and sees every class file, but skips the class files of other shards.
 *
 * The classes in [splitClasses] run in every shard, and a JUnit Platform `PostDiscoveryFilter` in the test sources
 * keeps only the methods whose `<class>.<method>` name falls into the shard. Only split classes whose methods share no
 * state, since the methods then run in different JVMs. The task passes the shard and the split classes to the filter in
 * the `mops.testShard` and `mops.testShard.splitClasses` system properties.
 */
fun Test.shardTests(splitClasses: List<String> = emptyList()) {
    val property = project.providers.gradleProperty("testShard").orNull ?: return
    val match = Regex("""(\d+)/(\d+)""").matchEntire(property)
    val index = match?.groupValues?.get(1)?.toInt()
    val count = match?.groupValues?.get(2)?.toInt()
    if (index == null || count == null || index !in 1..count) {
        throw GradleException("testShard must be <index>/<count> with 1 <= index <= count, not '$property'")
    }
    exclude(Spec { element ->
        // Nested classes (`Outer$Inner.class`) run with their outer class.
        val className = element.path.removeSuffix(".class").substringBefore('$').replace('/', '.')
        !element.isDirectory && className !in splitClasses && testShardOf(className, count) != index - 1
    })
    systemProperty("mops.testShard", property)
    systemProperty("mops.testShard.splitClasses", splitClasses.joinToString(","))
}

/**
 * The zero-based shard of the test named [name] among [count] shards. The salt in the hashed text was picked so that
 * the shards of the current tests take similar time; change it when one shard takes much longer than the others.
 */
fun testShardOf(name: String, count: Int): Int =
    (CRC32().apply { update("139:$name".toByteArray()) }.value % count).toInt()
