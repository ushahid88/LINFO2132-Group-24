plugins {
    application
    java
}

repositories {
    mavenCentral()
}

sourceSets.main.get().java.srcDir("src")
sourceSets.test.get().java.srcDir("test")

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
}

application {
    mainClass.set("compiler.Compiler")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
