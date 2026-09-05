# Library versions for Kotlin 2.4.10 / Compose Multiplatform 1.12.0 / AGP 9.0.1

Resolves issue #4. Researched 2026-09-05 against primary sources only (Maven Central and Google Maven
metadata, official release notes and compatibility docs). Every version below carries the URL it was
taken from. This note does not modify `gradle/libs.versions.toml`; the block at the end is ready to paste.

## Summary

- The toolchain triple is internally consistent: KGP 2.4.x is documented for AGP 8.5.2–9.1.0 and Gradle
  7.6.3–9.5.0; AGP 9.0.1 needs Gradle >= 9.1.0 (the wrapper is on 9.1.0); CMP 1.12.0 needs Kotlin language
  2.2+ / Kotlin 2.3+ for native and "the latest Compose Multiplatform is always compatible with the latest
  version of Kotlin".
- Bump the existing catalog: `composeMultiplatform` 1.11.1 -> **1.12.0**, `material3` 1.11.0-alpha07 ->
  **1.12.0-alpha03**, `androidx-lifecycle` 2.11.0-beta01 -> **2.11.0**. These are the exact versions the
  CMP 1.12.0 release notes pair together.
- Room: use **Room 3.0.2** (`androidx.room3`, KSP-only, KMP-first) with **sqlite-bundled 2.7.0** and
  **KSP 2.3.11**. Room 2.8.4 (Nov 2025) is the last 2.x and is the fallback.
- KSP: any KSP >= **2.3.10** is required for Kotlin 2.4.x (2.3.9 breaks on Kotlin 2.4's new default module
  names); 2.3.11 is the latest. KSP1 is removed, so this is KSP2.
- Everything else has a release that works with Kotlin 2.4.10: Koin 4.2.2, JetBrains navigation-compose
  2.10.0-alpha02 (2.9.2 if a stable is preferred), kotlinx-datetime 0.8.0, kotlinx-serialization 1.11.0
  (plugin 2.4.10), kotlinx-coroutines 1.11.0, Kermit 2.1.0, Kotest assertions 6.2.4, Turbine 1.2.1,
  Mokkery 3.4.2.
- Compose Hot Reload does **not** apply: it requires a JVM/desktop target and the project targets Android +
  iOS only. CMP 1.12.0 bundles Hot Reload 1.2.0 should a desktop target ever be added.
- No library in scope lacks a Kotlin-2.4.10-capable release. The only soft spot is Mokkery, whose
  compatibility table lists "2.4.0" for 3.4.x; 3.x is explicitly not pinned to Kotlin patch versions.

## Toolchain baseline (already pinned)

| Item | Version | Why / source |
|---|---|---|
| Kotlin / KGP | 2.4.10 | Bug-fix release of 2.4.0, released 2026-07-14. Latest on Maven Central is 2.4.20-RC3 (pre-release). https://kotlinlang.org/docs/releases.html , https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml |
| KGP <-> Gradle / AGP | Gradle 7.6.3–9.5.0, AGP 8.5.2–9.1.0 | Row "2.4.0-2.4.10" of the KGP compatibility table. AGP 9.0.1 is inside the range; AGP 9.2+ is not officially covered by KGP 2.4.10. https://kotlinlang.org/docs/gradle-configure-project.html |
| AGP | 9.0.1 | Requires Gradle >= 9.1.0, JDK 17, Build Tools 36.0.0. Built-in Kotlin is on by default; AGP 9.0 has a runtime dependency on KGP 2.2.10 and auto-upgrades KSP < 2.2.10-2.0.2. Google Maven lists 9.0.0 … 9.3.2 and 9.4.0 (Sept 2026). https://developer.android.com/build/releases/agp-9-0-0-release-notes , https://dl.google.com/dl/android/maven2/com/android/tools/build/group-index.xml |
| Gradle wrapper | 9.1.0 | Meets AGP 9.0.1 minimum and is within the KGP 2.4.x max (9.5.0). `gradle/wrapper/gradle-wrapper.properties` |
| Compose compiler plugin | 2.4.10 (= Kotlin) | "Compose Multiplatform requires the Compose Compiler Gradle plugin applied with the same version as the Kotlin Multiplatform plugin." https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html |

## Chosen versions

| Library | Artifact(s) | Chosen | Why / compatibility source |
|---|---|---|---|
| Compose Multiplatform | `org.jetbrains.compose` plugin; `org.jetbrains.compose.{runtime,foundation,ui,components}` | **1.12.0** | Released 2026-08-25 (Maven Central `latest`). Based on Jetpack Compose 1.12.0. Requires Kotlin language/API 2.2 and Kotlin 2.3+ for native; 2.4.10 satisfies both. https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0 , https://repo1.maven.org/maven2/org/jetbrains/compose/compose-gradle-plugin/maven-metadata.xml , https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html |
| Material3 (CMP) | `org.jetbrains.compose.material3:material3` | **1.12.0-alpha03** | Exactly what CMP 1.12.0 ships and recommends ("Material3 1.12.0-alpha03, based on Jetpack Material3 1.5.0-alpha22"). It is the newest published material3 for CMP (2026-06-30); there is no stable material3 for CMP 1.12. https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0 , https://repo1.maven.org/maven2/org/jetbrains/compose/material3/material3/maven-metadata.xml |
| Compose Resources | `org.jetbrains.compose.components:components-resources` | **1.12.0** (same ref as CMP) | Part of the CMP release train; requires CMP >= 1.6.10 / Kotlin >= 2.0. 1.12.0 adds BCP 47 script and numeric-region qualifiers. https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html , https://repo1.maven.org/maven2/org/jetbrains/compose/components/components-resources/maven-metadata.xml |
| Lifecycle (JetBrains) | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | **2.11.0** | CMP 1.12.0 pairs with "Lifecycle 2.11.0 (Jetpack Lifecycle 2.11.0)". Stable since 2026-07-14; replaces the current 2.11.0-beta01 pin. https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0 , https://repo1.maven.org/maven2/org/jetbrains/androidx/lifecycle/lifecycle-viewmodel-compose/maven-metadata.xml |
| Navigation 2 (JetBrains) | `org.jetbrains.androidx.navigation:navigation-compose` | **2.10.0-alpha02** (stable alternative: 2.9.2) | CMP 1.12.0 pairs with "Navigation 2.10.0-alpha02 (Jetpack Navigation 2.10.0-alpha05)"; the JetBrains navigation doc recommends the same coordinate/version. 2.9.2 is the last stable (compiled against Compose 1.9.3 / Lifecycle 2.9.x, resolves upward fine). https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0 , https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html , https://repo1.maven.org/maven2/org/jetbrains/androidx/navigation/navigation-compose/maven-metadata.xml |
| KSP | `com.google.devtools.ksp` plugin | **2.3.11** | Latest (2026-08-03). Since 2.3.0 "KSP version is no longer tied to the Kotlin compiler version"; KSP 1.x removed. 2.3.10 added "Sanitize ':' in internal-name module suffix so KSP works with Kotlin 2.4.0 default module names" (issue #2964, closed 2026-07-07). kotlinlang's KSP quick-start shows KSP 2.3.10 alongside Kotlin 2.4.10. https://github.com/google/ksp/releases/tag/2.3.0 , https://github.com/google/ksp/releases/tag/2.3.10 , https://github.com/google/ksp/issues/2964 , https://kotlinlang.org/docs/ksp-quickstart.html , https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml |
| Room | `androidx.room3:room3-runtime`, `room3-compiler`, plugin `androidx.room3` | **3.0.2** | Stable 2026-08-26. "Room 3.0 (package `androidx.room3`) is a major version update … that focuses on Kotlin Multiplatform"; KSP-only, Kotlin-only codegen, coroutines required, KMP targets incl. Android/iOS/JVM. The official Room KMP guide now uses `androidx.room3` 3.0.2 + `sqlite` 2.7.0. Fallback: Room 2.8.4 (Nov 2025, last 2.x, Kotlin 2.0+, KSP2). https://developer.android.com/jetpack/androidx/releases/room3 , https://developer.android.com/kotlin/multiplatform/room , https://dl.google.com/dl/android/maven2/androidx/room3/group-index.xml , https://developer.android.com/jetpack/androidx/releases/room |
| SQLite (bundled driver) | `androidx.sqlite:sqlite-bundled` | **2.7.0** | Stable 2026-07-01; the version Room 3.0.2's POM depends on (`sqlite-jvm 2.7.0`) and the Room KMP guide pins. https://developer.android.com/jetpack/androidx/releases/sqlite , https://dl.google.com/dl/android/maven2/androidx/sqlite/group-index.xml |
| Koin | `io.insert-koin:koin-core`, `koin-compose`, `koin-compose-viewmodel`, `koin-compose-viewmodel-navigation`, `koin-test`, `koin-android` | **4.2.2** | Latest stable 2026-06-15. Koin 4.2.0 was built on Kotlin 2.3.20, JetBrains Compose 1.10.2, Lifecycle 2.10.0, Navigation 2.9.7. 4.2.2 POMs depend on compose 1.10.2 / lifecycle 2.9.6 / navigation 2.9.2 — all lower than our pins, so Gradle resolves upward. Koin setup docs list exactly these CMP artifacts. https://insert-koin.io/docs/setup/koin , https://github.com/InsertKoinIO/koin/releases/tag/4.2.0 , https://repo1.maven.org/maven2/io/insert-koin/koin-core/maven-metadata.xml |
| kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** | Latest (2026-05-07). README: requires Kotlin stdlib >= 2.3.21. Use the plain artifact, not `0.8.0-0.6.x-compat` (only for third-party libs still on `kotlinx.datetime.Instant`). https://github.com/Kotlin/kotlinx-datetime/blob/master/README.md , https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-datetime/maven-metadata.xml |
| kotlinx-serialization | plugin `org.jetbrains.kotlin.plugin.serialization`; `kotlinx-serialization-json` | plugin **2.4.10**, runtime **1.11.0** | kotlinlang's get-started shows `plugin.serialization version "2.4.10"` with `kotlinx-serialization-json:1.11.0`. 1.11.0 is built on Kotlin 2.3.20; 1.12.0-RC (2026-09-04) is built on Kotlin 2.4.10 but is a release candidate. https://kotlinlang.org/docs/serialization-get-started.html , https://github.com/Kotlin/kotlinx.serialization/releases , https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml |
| kotlinx-coroutines | `kotlinx-coroutines-core`, `kotlinx-coroutines-test` | **1.11.0** | Latest stable (2026-05-08), built on Kotlin 2.2.20. https://github.com/Kotlin/kotlinx.coroutines/releases , https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml |
| Kermit | `co.touchlab:kermit` | **2.1.0** | Latest (2026-03-02); POM depends on kotlin-stdlib 2.2.0. https://github.com/touchlab/Kermit/releases , https://repo1.maven.org/maven2/co/touchlab/kermit/maven-metadata.xml |
| Kotest assertions | `io.kotest:kotest-assertions-core` | **6.2.4** | Latest (2026-08-11); POM depends on kotlin-stdlib 2.2.21. Usable "with another test framework like JUnit" — i.e. with `kotlin-test`, no Kotest framework plugin needed. https://github.com/kotest/kotest/releases , https://kotest.io/docs/assertions/assertions.html , https://repo1.maven.org/maven2/io/kotest/kotest-assertions-core/maven-metadata.xml |
| Turbine | `app.cash.turbine:turbine` | **1.2.1** | Latest (2025-06-11); POM depends on kotlin-stdlib 2.1.21, coroutines 1.10.2. https://github.com/cashapp/turbine/blob/trunk/CHANGELOG.md , https://repo1.maven.org/maven2/app/cash/turbine/turbine/maven-metadata.xml |
| Mokkery | plugin `dev.mokkery` | **3.4.2** | Compatibility table: "3.4.0 - 3.4.2 -> Kotlin 2.4.0"; 3.4.0 "Bump minimum Kotlin version to 2.4.0". "Mokkery 2.* and 3.* versions aren't tied to specific Kotlin 2.* versions". https://mokkery.dev/docs/Setup , https://github.com/lupuuss/Mokkery/releases , https://repo1.maven.org/maven2/dev/mokkery/mokkery-gradle/maven-metadata.xml |
| Compose Hot Reload | plugin `org.jetbrains.compose.hot-reload` | **not applicable** (would be 1.2.0) | "Desktop/JVM target is REQUIRED … does NOT currently support Android or iOS targets." CMP 1.12.0 bundles Hot Reload 1.2.0 (compose-gradle-plugin POM depends on `hot-reload-gradle-plugin 1.2.0`). https://kotlinlang.org/docs/multiplatform/compose-hot-reload.html , https://github.com/JetBrains/compose-hot-reload/releases |
| kotlin-test | `org.jetbrains.kotlin:kotlin-test` | 2.4.10 (= Kotlin) | Ships with the Kotlin release. |

## Known incompatibilities and gotchas

1. **AGP 9 built-in Kotlin vs Room/KSP.** `:androidApp` applies `com.android.application` without
   `org.jetbrains.kotlin.android`, so AGP's built-in Kotlin is active there. AGP 9.0 "has a runtime
   dependency on KGP 2.2.10"; with KGP 2.4.10 on the build classpath the higher version wins. `kotlin-kapt`
   "is incompatible with built-in Kotlin" — irrelevant because Room 3 is KSP-only. Put Room in `:shared`
   (the KMP module), never in `:androidApp`. Source: https://developer.android.com/build/releases/agp-9-0-0-release-notes ,
   https://developer.android.com/build/migrate-to-built-in-kotlin
2. **KSP with `com.android.kotlin.multiplatform.library`.** `:shared` uses the AKMP library plugin. KSP
   issue #2476 ("Support com.android.kotlin.multiplatform.library") is still open, but KSP 2.3.6 shipped
   AKMP fixes and the last comment (2026-04-16) reports it "resolved with KSP 2.3.6". Use KSP >= 2.3.6
   (we pick 2.3.11) and per-target configurations `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`;
   the catch-all `ksp(...)` is deprecated in KSP2. Verify with a real build in the implementation ticket.
   Sources: https://github.com/google/ksp/issues/2476 , https://github.com/google/ksp/releases/tag/2.3.6 ,
   https://kotlinlang.org/docs/ksp-multiplatform.html
3. **KSP < 2.3.10 breaks on Kotlin 2.4.x.** Kotlin 2.4.0 changed default module names to
   `{group}:{project}`; KSP 2.3.9 failed with "Can't escape identifier … contains illegal characters: :".
   Fixed in KSP 2.3.10 (PR #2981). Do not pin KSP below 2.3.10. Source: https://github.com/google/ksp/issues/2964
4. **KSP1 is gone.** "KSP 1.x has been removed and is no longer supported" (KSP README); there is no
   `ksp.useKSP2` toggle to worry about. Room 3 is KSP-only ("configuration of annotation processor via
   KAPT or JavaAP is no longer possible in Room 3.0"). Sources: https://github.com/google/ksp ,
   https://developer.android.com/jetpack/androidx/releases/room3
5. **Room 3 is a new group and package.** `androidx.room:room-runtime` -> `androidx.room3:room3-runtime`,
   `androidx.room.RoomDatabase` -> `androidx.room3.RoomDatabase`, Gradle plugin id `androidx.room3`.
   DAO functions must be `suspend` or return `Flow`. Room 3 minSdk is 23 (project is 30).
   Source: https://developer.android.com/jetpack/androidx/releases/room3
6. **Mokkery plugin pin.** Mokkery is a compiler plugin; its docs table lists Kotlin "2.4.0" for
   3.4.0–3.4.2 and does not enumerate 2.4.10, but states 3.x is not tied to specific Kotlin 2.x versions.
   No 2.4.10 issue is reported. Expect to need a Mokkery bump when moving to Kotlin 2.4.20 (planned
   Sept 2026). Source: https://mokkery.dev/docs/Setup
7. **Navigation multiplatform is alpha on CMP 1.12.** The version CMP 1.12.0 ships and JetBrains documents
   is 2.10.0-alpha02 (its POM targets compose 1.12.0-alpha02 / lifecycle 2.11.0-beta0x). The last stable,
   2.9.2, is built against CMP 1.9.3 / lifecycle 2.9.x and resolves upward. Koin's
   `koin-compose-viewmodel-navigation` 4.2.2 depends on 2.9.2; Gradle picks the highest requested version,
   so declaring 2.10.0-alpha02 in `commonMain` is enough. Type-safe routes need the serialization plugin.
   Navigation 3 (`org.jetbrains.androidx.navigation3:navigation3-ui` 1.2.0-alpha02 with CMP 1.12.0) is
   also available but out of scope for this catalog. Sources: https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0 ,
   https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html
8. **Lifecycle docs lag.** The kotlinlang ViewModel page still shows 2.10.0; the CMP 1.12.0 release notes
   (2.11.0) are the authoritative pairing. Source: https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html
9. **material3 stays alpha.** CMP has never published a stable `org.jetbrains.compose.material3` for the
   1.11/1.12 lines; 1.12.0-alpha03 is what 1.12.0 is built and tested against.
10. **kotlinx-datetime 0.8.0.** `Instant`/`Clock` live in `kotlin.time` (removed from kotlinx-datetime in
    0.7.0). Requires stdlib >= 2.3.21. Do not use the `-0.6.x-compat` variant unless a third-party
    dependency forces it. Source: https://github.com/Kotlin/kotlinx-datetime/blob/master/README.md
11. **kotlinx-serialization 1.12.0-RC.** Built on Kotlin 2.4.10 but still an RC (2026-09-04); stay on
    1.11.0 and revisit when 1.12.0 goes stable. Source: https://github.com/Kotlin/kotlinx.serialization/releases
12. **AGP ceiling.** KGP 2.4.10 is officially validated with AGP up to 9.1.0. Bumping AGP beyond 9.1.0
    should wait for Kotlin 2.4.20. Source: https://kotlinlang.org/docs/gradle-configure-project.html
13. **Compose Hot Reload.** Needs a JVM target and JetBrains Runtime (Java <= 21). Not applicable to this
    Android + iOS project; do not add the plugin. Source: https://kotlinlang.org/docs/multiplatform/compose-hot-reload.html

## Ready-to-paste `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.0.1"
android-compileSdk = "36"
android-minSdk = "30"
android-targetSdk = "36"
androidx-activity = "1.13.0"
androidx-appcompat = "1.8.0"
androidx-core = "1.19.0"
androidx-espresso = "3.7.0"
androidx-lifecycle = "2.11.0"
androidx-navigation = "2.10.0-alpha02"
androidx-testExt = "1.3.0"
composeMultiplatform = "1.12.0"
junit = "4.13.2"
kermit = "2.1.0"
koin = "4.2.2"
kotest = "6.2.4"
kotlin = "2.4.10"
kotlinx-coroutines = "1.11.0"
kotlinx-datetime = "0.8.0"
kotlinx-serialization = "1.11.0"
ksp = "2.3.11"
material3 = "1.12.0-alpha03"
mokkery = "3.4.2"
room = "3.0.2"
sqlite = "2.7.0"
turbine = "1.2.1"

[libraries]
# Kotlin / test
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlin-testJunit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
junit = { module = "junit:junit", version.ref = "junit" }

# AndroidX (Android-only)
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-testExt-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-testExt" }
androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "androidx-espresso" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidx-appcompat" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }

# Compose Multiplatform
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composeMultiplatform" }
compose-uiTooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "composeMultiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "composeMultiplatform" }

# JetBrains lifecycle + navigation (multiplatform)
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }

# Room 3 (KMP) + bundled SQLite
androidx-room-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room" }
androidx-room-sqliteWrapper = { module = "androidx.room3:room3-sqlite-wrapper", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }

# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
koin-compose-viewmodel-navigation = { module = "io.insert-koin:koin-compose-viewmodel-navigation", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }

# kotlinx
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Logging
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }

# Testing
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidMultiplatformLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room3", version.ref = "room" }
mokkery = { id = "dev.mokkery", version.ref = "mokkery" }
# Compose Hot Reload intentionally omitted: requires a JVM/desktop target (see "Known incompatibilities" 13).
```

### Wiring notes for the implementation ticket

- `:shared/build.gradle.kts`: apply `ksp`, `room`, `kotlinSerialization`, `mokkery`; add
  `room { schemaDirectory("$projectDir/schemas") }`; register the compiler per target
  (`add("kspAndroid", libs.androidx.room.compiler)`, `add("kspIosArm64", …)`,
  `add("kspIosSimulatorArm64", …)`) — the catch-all `ksp(...)` is deprecated in KSP2.
  Source: https://developer.android.com/kotlin/multiplatform/room
- `commonMain`: room runtime + sqlite-bundled, koin-core/compose/compose-viewmodel(-navigation),
  navigation-compose, coroutines-core, datetime, serialization-json, kermit.
- `commonTest`: kotlin-test, kotest-assertions-core, turbine, coroutines-test, koin-test (Mokkery is a
  plugin plus its auto-added runtime).
- Room 3's `room3-sqlite-wrapper` is optional and Android-only (`androidMain`).
