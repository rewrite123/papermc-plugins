plugins {
    java
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

group = "io.github.ihormel"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.23.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.wrapper {
    gradleVersion = "9.1.0"
    distributionSha256Sum = "a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806"
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version.toString())
        id.set(providers.gradleProperty("hangarProject"))
        channel.set(providers.gradleProperty("hangarChannel").orElse("Release"))
        apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
        changelog.set(providers.gradleProperty("hangarChangelog").orElse("See the project README for features and usage."))
        platforms {
            paper {
                jar.set(tasks.jar.flatMap { it.archiveFile })
                platformVersions.set(providers.gradleProperty("paperVersions").map {
                    it.split(",").map(String::trim)
                })
            }
        }
    }
}
