plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    plugins.withId("com.diffplug.spotless") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint().editorConfigOverride(
                    mapOf(
                        "android" to "true",
                        // Compose call chains are intentionally wider than typical Kotlin.
                        "max_line_length" to "200",
                        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    ),
                )
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint()
            }
        }
    }
}

tasks.register("spotlessCheckAll") {
    group = "verification"
    description = "Runs Spotless check on all subprojects."
    dependsOn(
        ":noctdock-core:spotlessCheck",
        ":noctdock-sender:spotlessCheck",
        ":noctdock-receiver:spotlessCheck",
    )
}

tasks.register("spotlessApplyAll") {
    group = "formatting"
    description = "Applies Spotless formatting on all subprojects."
    dependsOn(
        ":noctdock-core:spotlessApply",
        ":noctdock-sender:spotlessApply",
        ":noctdock-receiver:spotlessApply",
    )
}
