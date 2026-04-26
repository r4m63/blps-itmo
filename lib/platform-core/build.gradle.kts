plugins {
    `java-library`
}

dependencies {
    api(project(":grpc-contracts"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.kafka:spring-kafka")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("io.grpc:grpc-netty-shaded:1.64.0")
    api("io.grpc:grpc-stub:1.64.0")
    api("io.grpc:grpc-protobuf:1.64.0")
}
