plugins {
    java
}

group = "kr.nightbeam"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly(files("libs/DonutTeams-2.0.2.jar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveFileName.set("TeamAdvancements.jar")
}
tasks.processResources {
    filteringCharset = "UTF-8"
}
