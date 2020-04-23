import java.nio.charset.StandardCharsets

plugins {
    kotlin("jvm") version "1.3.21"
    id("com.palantir.docker") version "0.25.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.2.3")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.3")

    implementation("com.github.ajalt:clikt:2.6.0")
    implementation("org.apache.commons:commons-compress:1.19")

    implementation("org.apache.ant:ant:1.10.7")
}

version = project.file("version")
        .readText(StandardCharsets.UTF_8)
        .trim()
        .substring("version=".length)

tasks.withType<Jar> {
    archiveFileName.set("${project.name}.${archiveExtension.get()}")
    manifest {
        attributes["Main-Class"] = "org.corfudb.cloud.infrastructure.integration.MainKt"
    }
}

tasks.dockerPrepare {
    dependsOn(tasks.jar)

    doLast {
        project.copy {
            from(configurations.runtimeClasspath.get())
            into("$buildDir/docker/lib")
        }

        project.copy {
            from(tasks.jar.get() as CopySpec)
            into("$buildDir/docker/")
        }

        project.copy {
            from("$projectDir/bin")
            into("$buildDir/docker/bin")
        }

        project.copy {
            from("${projectDir.parentFile}/dashboard/")
            into("$buildDir/docker/")
        }
    }
}

tasks.dockerPush {
    dependsOn(tasks.check)
}

tasks.build {
    dependsOn(tasks.dockerPush)
}

docker {
    name = "corfudb/${project.name}:latest"
    setDockerfile(file("Dockerfile"))
}

tasks.create("version").doLast {
    println(version)
}
