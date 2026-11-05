plugins {
    `java-library`
}

val paperApi = "io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT"

dependencies {
    implementation(project(":boxpet-api"))
    compileOnly(paperApi)
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.12")
    // Provided by their plugins at runtime, never shaded
    compileOnly(files("../libs/ModelEngine-R4.1.1.jar"))
    compileOnly(files("../libs/MythicLib-1.7.jar"))
    compileOnly(files("../libs/boxcore-api-1.4.0.jar"))
    compileOnly(files("../libs/boxclans-api-1.4.4.jar"))

    testImplementation(paperApi)
    // Paper provides the SQLite driver at runtime (never shaded); tests need their own copy
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("BoxPet-${project.version}.jar")
    val apiJar = project(":boxpet-api").tasks.named<Jar>("jar")
    dependsOn(apiJar)
    from(apiJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}
