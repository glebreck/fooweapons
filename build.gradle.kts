allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

tasks.register<Zip>("packageResourcePack") {
    archiveBaseName.set("fooWeapons-resourcepack")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    from("resourcepack") {
        include("pack.mcmeta")
        include("assets/**")
    }
}

tasks.register("dist") {
    dependsOn(":plugin:jar", "packageResourcePack")
    doLast {
        println("Plugin JAR: " + project(":plugin").layout.buildDirectory.file("libs/nf_fooweapons-${project.version}.jar").get())
        println("Resource pack: " + layout.buildDirectory.file("dist/fooWeapons-resourcepack-${project.version}.zip").get())
    }
}
