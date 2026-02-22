import java.util.Properties

plugins {
    id("maven-publish")
    kotlin("jvm") version "2.3.0"
}

group = "ru.lavafrai"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.knowm.xchart:xchart:3.8.3")
    testImplementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    testImplementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
    testImplementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
}

kotlin {
    jvmToolchain(17)
}

tasks.register<JavaExec>("runBenchmark") {
    group = "verification"
    description = "Runs the benchmark for FourierTransform implementations."

    mainClass.set("ru.lavafrai.kfft.BenchmarkKt")

    classpath = sourceSets["test"].runtimeClasspath
}

tasks.register<JavaExec>("runEqualizer") {
    group = "verification"
    description = "Runs the FFT Equalizer application."

    mainClass.set("ru.lavafrai.kfft.EqualizerAppKt")

    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs = listOf("-Xmx512m")
}

tasks.test {
    useJUnitPlatform()
}


val localProperties = Properties().also { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) props.load(file.inputStream())
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "kfft"
            version = project.version.toString()

            from(components["kotlin"])

            artifact(tasks.kotlinSourcesJar)

            pom {
                name.set("kfft")
                description.set("Kotlin FFT library")
            }
        }
    }

    repositories {
        val mavenUrl = localProperties.getProperty("maven.url")
        if (!mavenUrl.isNullOrBlank()) {
            maven {
                url = uri(mavenUrl)
                credentials {
                    username = localProperties.getProperty("maven.username", "")
                    password = localProperties.getProperty("maven.password", "")
                }
            }
        }
    }
}