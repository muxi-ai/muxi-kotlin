plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("com.vanniktech.maven.publish") version "0.28.0"
}

group = "org.muxi"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-sse:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
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
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
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
