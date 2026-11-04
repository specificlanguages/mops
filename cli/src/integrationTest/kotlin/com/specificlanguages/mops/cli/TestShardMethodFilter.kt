package com.specificlanguages.mops.cli

import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.PostDiscoveryFilter
import java.util.zip.CRC32

/**
 * Keeps only the methods of this test shard in the classes that `shardTests` in
 * `build-logic/src/main/kotlin/testShards.kt` splits by method. It reads the shard and the split classes from the
 * `mops.testShard` and `mops.testShard.splitClasses` system properties and keeps every test when they are not set.
 */
class TestShardMethodFilter : PostDiscoveryFilter {
    private val shard = System.getProperty("mops.testShard")?.split('/')?.map(String::toInt)
    private val splitClasses = System.getProperty("mops.testShard.splitClasses").orEmpty().split(',').toSet()

    override fun apply(descriptor: TestDescriptor): FilterResult {
        val source = descriptor.source.orElse(null) as? MethodSource
        // Nested classes (`Outer$Inner`) are split with their outer class.
        val className = source?.className?.substringBefore('$')
        if (shard == null || source == null || className !in splitClasses) {
            return FilterResult.included(null)
        }
        val (index, count) = shard
        return FilterResult.includedIf(testShardOf("$className.${source.methodName}", count) == index - 1)
    }

    // The same hash as testShardOf in build-logic/src/main/kotlin/testShards.kt.
    private fun testShardOf(name: String, count: Int): Int =
        (CRC32().apply { update("139:$name".toByteArray()) }.value % count).toInt()
}
