# Building

Requirements:

- JDK 17
- Gradle 8.8 (or restore the standard Gradle wrapper JAR from upstream)
- Internet access for ForgeGradle / Forge / Parchment dependencies on the first build

Build from the project root:

```text
gradle clean build
```

The re-obfuscated mod JAR is written under `build/libs/`.

The original upstream repository includes a Gradle wrapper JAR. This archive intentionally contains source/text files only; binary wrapper files were not re-exported by the source retrieval environment.
