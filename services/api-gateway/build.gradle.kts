plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-core"))
    implementation(project(":grpc-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("api-gateway.jar")
}
