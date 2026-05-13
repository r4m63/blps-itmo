rootProject.name = "blps-lab3"

include(
    "gateway-service",
    "auth-service",
    "claim-service",
    "penalty-service"
)

project(":gateway-service").projectDir = file("services/gateway-service")
project(":auth-service").projectDir = file("services/auth-service")
project(":claim-service").projectDir = file("services/claim-service")
project(":penalty-service").projectDir = file("services/penalty-service")
