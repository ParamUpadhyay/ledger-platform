plugins {
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    group = "dev.ledger"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    // Security patches newer than Spring Boot 4.1.1 manages, flagged by the Trivy scan.
    // Remove each override once a Boot release ships that version or later.
    extra["tomcat.version"] = "11.0.26"
    extra["jackson-bom.version"] = "3.1.7"
}
