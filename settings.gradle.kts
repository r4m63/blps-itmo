rootProject.name = "blps-lab3"

include(
    "platform-core",
    "auth-service",
    "claim-service",
    "assessment-service",
    "penalty-service",
    "notification-service",
    "audit-service"
)

project(":platform-core").projectDir = file("lib/platform-core")
project(":auth-service").projectDir = file("services/auth-service")
project(":claim-service").projectDir = file("services/claim-service")
project(":assessment-service").projectDir = file("services/assessment-service")
project(":penalty-service").projectDir = file("services/penalty-service")
project(":notification-service").projectDir = file("services/notification-service")
project(":audit-service").projectDir = file("services/audit-service")
