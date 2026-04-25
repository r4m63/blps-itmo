plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.kafka:spring-kafka")
    api("com.fasterxml.jackson.core:jackson-databind")
}
