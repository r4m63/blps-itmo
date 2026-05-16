plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.maven.apache.org/maven2") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("io.minio:minio:8.5.12")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("jakarta.resource:jakarta.resource-api:2.1.0")
}

sourceSets {
    main {
        java {
            exclude("blps/itmo/claim/jca/**")
            exclude("blps/itmo/claim/kafka/JiraEventConsumer.java")
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("claim-service.jar")
}
