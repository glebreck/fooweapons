plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
