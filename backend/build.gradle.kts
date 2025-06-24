plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.github.perelshtein"
version = "1.0.4"
val ktor_version = "3.0.3"

repositories {
    mavenCentral()
//    maven (url = "https://jitpack.io")
}

application {
    mainClass.set("com.github.perelshtein.MainKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-xml:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-compression:${ktor_version}")
    implementation("io.ktor:ktor-server-http-redirect:${ktor_version}")
    implementation("io.ktor:ktor-client-core:${ktor_version}")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-rate-limit:$ktor_version")
    implementation("org.ktorm:ktorm-core:4.1.1")
    implementation("org.ktorm:ktorm-support-mysql:4.1.1")
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation("mysql:mysql-connector-java:8.0.32")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("org.telegram:telegrambots:6.9.7.1")

    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("org.bitcoinj:core:0.15")
//    implementation("com.guardsquare:proguard-gradle:7.7.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "obmen",
            "Implementation-Version" to version
        )
    }
}

// Add to build.gradle.kts
//tasks.register<Copy>("copyDependencies") {
//    from(configurations.runtimeClasspath)
//    into("build/libs/dependencies")
//}