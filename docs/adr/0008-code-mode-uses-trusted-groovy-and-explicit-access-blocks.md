# Code Mode uses trusted Groovy and explicit access blocks

> **Status: accepted.** Supersedes ADR-0006.

**Code Mode** is a general environment for composing operations against an open **MPS Project**, complementary to direct mops commands rather than an edit-only front end. Programs are trusted code, execute with the daemon's JVM authority, and use the Groovy runtime bundled with the selected MPS distribution; mops ships no second Groovy runtime because its flat daemon classpath cannot safely host competing Groovy versions.

Supported **Code Services** run inside explicit read or edit **Access Blocks**, while operations such as make and editor rendering run outside model access. Blocks commit independently, so a later failure does not undo earlier completed blocks. An edit block tracks repository changes to include models changed through raw MPS objects in its commit or rollback; direct persistence and non-model side effects performed through the escape hatch remain the program's responsibility.

The supported API uses handles and dedicated, reflected service interfaces backed by the same implementations as direct mops commands. Trusted programs may also obtain the underlying MPS project, nodes, models, and related objects as a version-coupled escape hatch that carries no mops compatibility or guard guarantees. Built-in and plugin services share one provider SPI and one **Service Catalog**; signatures are derived through Kotlin or Java reflection, while annotations supply stable names and semantic documentation that reflection cannot infer. The same catalog drives daemon-backed text and JSON help and in-program discovery. The first plugin mechanism uses `ServiceLoader` on the daemon classpath and includes provider jars in the daemon compatibility fingerprint; project-local MPS module loading is a later extension.

## Consequences

Code Mode is not a sandbox. Groovy dependency injection such as `@Grab` is disabled to keep the daemon classpath deterministic, and the default 15-minute hard execution timeout terminates the project daemon rather than pretending thread interruption can stop arbitrary JVM code. The timeout is configurable and may be disabled explicitly; the next mops invocation starts a fresh daemon after a timeout. The Groovy language level and raw MPS escape hatch follow the selected MPS distribution, while the reflected Code Service facade is mops's supported compatibility surface.
