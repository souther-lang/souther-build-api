# souther-build-api

The service interface a build plugin drives a [Souther](https://github.com/souther-lang/souther)
compile through.

```java
public interface SoutherBuildDriver {
    BuildResult compile(BuildRequest request);
}
```

A build plugin links this and nothing else of Souther's. The implementation —
`souther-build-driver` — ships with the compiler at the compiler's version, and the plugin resolves
it for the Souther version the project names and loads it in an isolated class loader. So the
compiler's own types stay on the compiler's side of that loader, and a change to them is not a
change a plugin release has to follow.

## Why it has its own version

Four things release for four reasons:

| Artifact | Releases when |
|---|---|
| `souther-compiler`, `souther-runtime`, `souther-build-driver` | the language moved |
| `souther-build-api` | the protocol moved |
| `souther-maven-plugin` | Maven integration moved |
| `souther-gradle-plugin` | Gradle integration moved |

This artifact's major version is the protocol version. A driver states which protocol it was built
against, and a plugin reads that before loading a class from it, so a disagreement is a build error
naming both numbers rather than an `AbstractMethodError`.

Compiled against Java 17, below the compiler's 25: a plugin has to start on whatever JDK the build
is already running to be able to report that Souther needs a newer one.

## Design

[souther-lang/souther#137](https://github.com/souther-lang/souther/issues/137).

## License

Copyright © kawasima 2026

Released under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
