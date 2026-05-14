rootProject.name = "blps-lab3"

include(
    "auth-service",
    "claim-service",
    "penalty-service"
)
project(":auth-service").projectDir = file("services/auth-service")
project(":claim-service").projectDir = file("services/claim-service")
project(":penalty-service").projectDir = file("services/penalty-service")
