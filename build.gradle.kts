plugins {
    java
}

group = "dev.kfanter.mcdcbridge"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Provided at runtime by Paper's library loader (see `libraries` in plugin.yml), so no shading needed.
    compileOnly("net.dv8tion:JDA:5.2.2")
    // Optional: Bedrock players via Geyser. Only touched when the Floodgate plugin is installed.
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
