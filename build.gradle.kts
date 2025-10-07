plugins {
    id("io.ktor.plugin") version "3.3.0"
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"

    application
}

group = "mi.yxz.mizu"
version = "1.0-SNAPSHOT"
val ktor_version = "3.3.0"
repositories {
    mavenCentral()
}
dependencies {
//    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
    implementation("ch.qos.logback:logback-classic:1.5.6")
//    implementation("io.ktor:ktor-serialization-jackson:$ktor_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")

}
application {
    mainClass.set("mi.yxz.mizu.MainKt")
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
ktor {
    fatJar {
        archiveFileName.set("diy.jar")
    }
}
