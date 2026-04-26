rootProject.name = "blps-lab3"

include(
    "platform-core",
    "grpc-contracts",
    "auth-service",
    "claim-service",
    "assessment-service",
    "penalty-service",
    "storage-service",
    "notification-service",
    "audit-service",
    "api-gateway"
)

project(":platform-core").projectDir = file("lib/platform-core")
project(":grpc-contracts").projectDir = file("lib/grpc-contracts")
project(":auth-service").projectDir = file("services/auth-service")
project(":claim-service").projectDir = file("services/claim-service")
project(":assessment-service").projectDir = file("services/assessment-service")
project(":penalty-service").projectDir = file("services/penalty-service")
project(":storage-service").projectDir = file("services/storage-service")
project(":notification-service").projectDir = file("services/notification-service")
project(":audit-service").projectDir = file("services/audit-service")
project(":api-gateway").projectDir = file("services/api-gateway")
