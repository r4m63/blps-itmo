plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    war
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
    implementation("org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter:7.21.0")
    implementation("org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest:7.21.0")
    implementation("org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp:7.21.0")
    implementation("io.minio:minio:8.5.12")
    implementation("jakarta.resource:jakarta.resource-api:2.1.0")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
    runtimeOnly("org.postgresql:postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("claim-service.jar")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootWar>("bootWar") {
    archiveFileName.set("claim-service.war")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<War>("war") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
