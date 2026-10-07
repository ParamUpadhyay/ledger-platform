plugins {
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    group = "dev.ledger"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
