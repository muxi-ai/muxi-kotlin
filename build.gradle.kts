plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "org.muxi"
version = "1.20260713.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:okhttp-sse:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    
    coordinates(group.toString(), "muxi-kotlin", version.toString())
    
    pom {
        name.set("MUXI Kotlin SDK")
        description.set("Kotlin SDK for MUXI AI platform")
        url.set("https://github.com/muxi-ai/muxi-kotlin")
        
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        
        developers {
            developer {
                id.set("muxi")
                name.set("MUXI AI")
                email.set("support@muxi.ai")
            }
        }
        
        scm {
            connection.set("scm:git:git://github.com/muxi-ai/muxi-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/muxi-ai/muxi-kotlin.git")
            url.set("https://github.com/muxi-ai/muxi-kotlin")
        }
    }
}
