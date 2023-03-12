plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.21"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-script-runtime:1.7.21")
    implementation("io.github.kscripting:kscript-annotations:1.5.0")
    implementation("mysql:mysql-connector-java:8.0.32")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.google.guava:guava-rate-limiter:15.0-atlassian-1")
}

sourceSets.getByName("main").java.srcDirs("src")
sourceSets.getByName("test").java.srcDirs("src")
