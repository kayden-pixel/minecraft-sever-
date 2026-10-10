plugins {
    java
}

group = "yt.kingez341"
version = "0.1.3"

val paperVersion = providers.gradleProperty("paperVersion").orElse("1.21.11-R0.1-SNAPSHOT")
val toolchainVersion = providers.gradleProperty("toolchainVersion").map(String::toInt).orElse(21)
val targetRelease = providers.gradleProperty("targetRelease").map(String::toInt).orElse(21)

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperVersion.get()}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(toolchainVersion.get()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetRelease.get())
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("KingFeatures")
    manifest {
        attributes["Implementation-Title"] = "KingFeatures"
        attributes["Implementation-Version"] = project.version
    }
}
