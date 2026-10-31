plugins {
    java
}

group = "dev.kfanter.mcdcbridge"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Provided at runtime by Paper's library loader (see `libraries` in plugin.yml), so no shading needed.
    compileOnly("net.dv8tion:JDA:5.2.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
