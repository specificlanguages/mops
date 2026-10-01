package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.protocol.NodeTarget
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentReadsSemanticsTest {
    @Test
    fun `shared access supports overlapping lookups searches and diagnostics`() {
        SharedMpsEnvironment.withProjectCopy { access, _ ->
            access.extra { refreshExternalChanges() }
            val target = NodeTarget.InModel("com.specificlanguages.json.structure", "2110045694544566904")
            val operations: List<MpsRead.() -> Any> = listOf(
                { list(listOf("com.specificlanguages.json", ".structure"), depth = 3) },
                { getNode(target, ancestry = true) },
                { findByName("Json", resolveScope(null), limit = 0) },
                { findNodeById("2110045694544566904", resolveScope(null), limit = 0) },
                { findUsages(target, resolveScope(null), limit = 0) },
                { findInstances("jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration",
                    exact = false, scope = resolveScope(null), limit = 0) },
                { diagnoseModules() },
                { diagnoseModule("com.specificlanguages.json") },
            )
            val pool = Executors.newFixedThreadPool(operations.size * 2)
            try {
                // Two callers per operation also exercise first-use model loading and shared runtime caches.
                val barrier = CyclicBarrier(operations.size * 2)
                val concurrent = (operations + operations).map { operation ->
                    pool.submit<Any> {
                        access.read {
                            barrier.await(30, TimeUnit.SECONDS)
                            operation()
                        }
                    }
                }.map { it.get(60, TimeUnit.SECONDS) }
                val serial = operations.map { operation -> access.read(operation) }
                assertEquals(serial + serial, concurrent)
            } finally {
                pool.shutdown()
                check(pool.awaitTermination(60, TimeUnit.SECONDS))
            }
        }
    }
}
