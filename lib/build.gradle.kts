import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
}

group = "net.hollowcube"
version = "1.0.1"
base.archivesName = "compat"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.minestom)
    implementation(libs.slf4j)
    implementation(libs.fastutil)
    implementation(libs.zstd)
}

testing {
    suites {
        // Configure the built-in test suite
        val test = named<JvmTestSuite>("test") {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("6.0.1")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun mavenProperty(name: String): String =
    providers.gradleProperty(name).getOrElse(localProperties.getProperty(name) ?: "")

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "compat"
            from(components["java"])
            pom {
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "everbuild"
            url = uri("https://mvn.everbuild.org/public")
            credentials {
                username = mavenProperty("maven.username")
                password = mavenProperty("maven.password")
            }
        }
    }
}
