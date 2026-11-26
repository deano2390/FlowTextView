import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.roborazzi)
    `maven-publish`
}

group = "com.github.deano2390"
version = "3.0.0"

android {
    namespace = "uk.co.deanwild.flowtextview"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

roborazzi {
    // Golden images are committed; `./gradlew verifyRoborazziDebug` compares against them and
    // `./gradlew recordRoborazziDebug` re-records after an intentional visual change.
    outputDir.set(file("src/test/screenshots"))
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.customview)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "FlowTextView"
            afterEvaluate { from(components["release"]) }

            pom {
                name.set("FlowTextView")
                description.set("An Android layout whose text flows around its child views.")
                url.set("https://github.com/deano2390/FlowTextView")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("deano2390")
                        name.set("Dean Wild")
                    }
                }
                scm {
                    url.set("https://github.com/deano2390/FlowTextView")
                    connection.set("scm:git:https://github.com/deano2390/FlowTextView.git")
                }
            }
        }
    }
}
