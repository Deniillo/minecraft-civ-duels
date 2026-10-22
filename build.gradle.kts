plugins {
    id("java")
}

group = "mine.deniilo"
version = "0.2.0"

val paperApi = "io.papermc.paper:paper-api:26.3.build.211-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly(paperApi)

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // тот же драйвер, что встроен в Paper
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    // моки Bukkit-сущностей для тестов движка боя без сервера
    testImplementation("org.mockito:mockito-core:5.24.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    // Mockito подключает свой агент для моков final-классов; без флага JDK 25 ругается в лог
    jvmArgs("-XX:+EnableDynamicAgentLoading", "--enable-native-access=ALL-UNNAMED")
}

