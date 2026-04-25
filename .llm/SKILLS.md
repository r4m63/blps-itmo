# BLPS Project Skills

This file defines project-specific playbooks for agents working in this repository. Use the smallest relevant playbook instead of re-discovering the system from scratch.

## Skill: Architecture Sweep

Use when the user asks a broad question, asks for a review of the whole project, or the task spans multiple layers.

Read in this order:

1. [build.gradle.kts](../build.gradle.kts)
2. [src/main/resources/application.yml](../src/main/resources/application.yml)
3. [docker-compose.yml](../docker-compose.yml)
4. [sql/init_1.sql](../sql/init_1.sql)
5. [sql/init_2.sql](../sql/init_2.sql)
6. [bpmn/blps1.bpmn](../bpmn/blps1.bpmn)
7. [rest-client/scenarios](../rest-client/scenarios)
8. [src/main/java/blps/itmo/controller](../src/main/java/blps/itmo/controller)
9. [src/main/java/blps/itmo/service/ClaimService.java](../src/main/java/blps/itmo/service/ClaimService.java)
10. [src/main/java/blps/itmo/service/MinioService.java](../src/main/java/blps/itmo/service/MinioService.java)

Output focus:

- identify which layer owns the behavior
- identify whether the change touches business DB, auth DB, or MinIO
- identify whether the contract is documented by REST client or Postman examples
- identify whether BPMN and implementation are aligned or diverge

## Skill: BPMN To Code Mapping

Use when the user asks what the business process is, how BPMN is implemented, or whether code matches the process model.

Primary files:

- [bpmn/blps1.bpmn](../bpmn/blps1.bpmn)
- [src/main/java/blps/itmo/service/ClaimService.java](../src/main/java/blps/itmo/service/ClaimService.java)
- [src/main/java/blps/itmo/controller/ClaimController.java](../src/main/java/blps/itmo/controller/ClaimController.java)
- [rest-client/scenarios](../rest-client/scenarios)

Method:

- map each BPMN gateway/task to one or more endpoints
- map each endpoint to allowed source status and target status
- compare BPMN branches against scenario coverage
- explicitly list BPMN features that are conceptual only and not implemented in code

Known current gaps to remember:

- BPMN is not executed by an engine
- timeout path for tenant response is not implemented
- notification step is not implemented
- `INTAKE_REVIEW` exists in enum but is not actively entered
- `TenantResponseRequest.agree` currently has no workflow effect

## Skill: Claim Lifecycle Change

Use when the task affects statuses, transitions, validation, ownership, or claim read models.

Primary files:

- [src/main/java/blps/itmo/service/ClaimService.java](../src/main/java/blps/itmo/service/ClaimService.java)
- [src/main/java/blps/itmo/controller/ClaimController.java](../src/main/java/blps/itmo/controller/ClaimController.java)
- [src/main/java/blps/itmo/entity/business/ClaimStatus.java](../src/main/java/blps/itmo/entity/business/ClaimStatus.java)
- [sql/init_1.sql](../sql/init_1.sql)
- [src/main/java/blps/itmo/dto](../src/main/java/blps/itmo/dto)
- [rest-client/scenarios](../rest-client/scenarios)

Checklist:

- confirm the allowed previous states and target state
- compare the change against BPMN branch intent and existing scenario files
- update status history writes if the transition changes
- preserve landlord/tenant/admin ownership checks
- keep DB enum and Java enum synchronized
- update at least one end-to-end scenario if API behavior changed
- run `./gradlew compileJava`

## Skill: RBAC Or Auth Change

Use when the task adds a role, changes permissions, introduces a protected endpoint, or fixes authorization behavior.

Primary files:

- [src/main/java/blps/itmo/security/Privileges.java](../src/main/java/blps/itmo/security/Privileges.java)
- [src/main/java/blps/itmo/config/SecurityConfig.java](../src/main/java/blps/itmo/config/SecurityConfig.java)
- [src/main/java/blps/itmo/security/AppUserDetailsService.java](../src/main/java/blps/itmo/security/AppUserDetailsService.java)
- [src/main/java/blps/itmo/controller](../src/main/java/blps/itmo/controller)
- [sql/init_2.sql](../sql/init_2.sql)
- [sql/test_users_1.sql](../sql/test_users_1.sql)
- [rest-client/claims-rbac.http](../rest-client/claims-rbac.http)

Checklist:

- keep privilege constants and SQL seed data aligned
- update `@PreAuthorize` annotations where needed
- keep service-level ownership checks; controller guards are not enough
- if new users/roles are needed for manual checks, extend seed data consciously
- validate with the RBAC smoke scenario or a targeted manual request

## Skill: Attachment Or MinIO Flow

Use when the task touches file uploads, attachment confirmation, presigned URLs, or claim-linked files.

Primary files:

- [src/main/java/blps/itmo/controller/StorageController.java](../src/main/java/blps/itmo/controller/StorageController.java)
- [src/main/java/blps/itmo/service/MinioService.java](../src/main/java/blps/itmo/service/MinioService.java)
- [src/main/java/blps/itmo/entity/business/ClaimAttachment.java](../src/main/java/blps/itmo/entity/business/ClaimAttachment.java)
- [sql/init_1.sql](../sql/init_1.sql)
- [docs/how_minio.txt](../docs/how_minio.txt)
- [rest-client/scenarios/11-intake-additional-final-penalty.http](../rest-client/scenarios/11-intake-additional-final-penalty.http)

Checklist:

- preserve `init -> direct upload -> confirm -> attach`
- remember MinIO is outside XA/JTA
- keep `objectKey` normalization behavior intact unless explicitly changing URL handling
- distinguish `attachmentKeys` from `attachmentIds`
- think about orphaned objects/rows if the task changes confirm or cleanup logic
- remember that `attachments/additional` returns presigned URLs despite the method name mentioning keys

## Skill: Cross-DB Transaction Change

Use when one use case writes to both auth DB and business DB, or when a bug smells like partial commit behavior.

Primary files:

- [src/main/java/blps/itmo/config/AuthPersistenceConfig.java](../src/main/java/blps/itmo/config/AuthPersistenceConfig.java)
- [src/main/java/blps/itmo/config/BusinessPersistenceConfig.java](../src/main/java/blps/itmo/config/BusinessPersistenceConfig.java)
- [src/main/java/blps/itmo/config/TransactionTemplateConfig.java](../src/main/java/blps/itmo/config/TransactionTemplateConfig.java)
- [src/main/java/blps/itmo/service/AuthUserService.java](../src/main/java/blps/itmo/service/AuthUserService.java)
- [src/main/java/blps/itmo/service/UserService.java](../src/main/java/blps/itmo/service/UserService.java)
- [src/main/java/blps/itmo/service/ClaimService.java](../src/main/java/blps/itmo/service/ClaimService.java)

Checklist:

- verify which DB each repository belongs to
- keep multi-DB writes inside existing JTA `TransactionTemplate`s
- do not introduce entity relations across the two persistence units
- explicitly classify the path:
  create-claim style cross-DB write, support-decision penalty update, or user deactivation
- remember MinIO is outside XA even when methods are called inside JTA templates
- verify behavior against schema constraints in both SQL init files
- run `./gradlew compileJava`

## Skill: Scenario-Driven Verification

Use when the user refers to tests, scenarios, expected flows, or asks what behavior is currently supported.

Primary files:

- [rest-client/scenarios/00-rbac-smoke.http](../rest-client/scenarios/00-rbac-smoke.http)
- [rest-client/scenarios/10-intake-additional-assessment-no.http](../rest-client/scenarios/10-intake-additional-assessment-no.http)
- [rest-client/scenarios/11-intake-additional-final-penalty.http](../rest-client/scenarios/11-intake-additional-final-penalty.http)
- [rest-client/scenarios/12-intake-additional-final-no-penalty.http](../rest-client/scenarios/12-intake-additional-final-no-penalty.http)
- [rest-client/scenarios/20-intake-ok-assessment-no.http](../rest-client/scenarios/20-intake-ok-assessment-no.http)
- [rest-client/scenarios/21-intake-ok-final-penalty.http](../rest-client/scenarios/21-intake-ok-final-penalty.http)
- [rest-client/scenarios/22-intake-ok-final-no-penalty.http](../rest-client/scenarios/22-intake-ok-final-no-penalty.http)

What to extract:

- covered happy-path branches
- actor/role sequence for each branch
- storage pre-step sequence before claim operations
- which endpoints are exercised together as one business flow
- which edge cases are not covered by scenarios and therefore must be checked in code

## Skill: API Contract Update

Use when the task adds fields to requests/responses, changes validation, or adds endpoints.

Primary files:

- [src/main/java/blps/itmo/dto](../src/main/java/blps/itmo/dto)
- [src/main/java/blps/itmo/controller](../src/main/java/blps/itmo/controller)
- [src/main/java/blps/itmo/service](../src/main/java/blps/itmo/service)
- [src/main/java/blps/itmo/exception/GlobalExceptionHandler.java](../src/main/java/blps/itmo/exception/GlobalExceptionHandler.java)
- [rest-client/scenarios](../rest-client/scenarios)
- [postman/collections](../postman/collections)

Checklist:

- prefer actor identity from `@AuthenticationPrincipal`, not from request payload
- add validation annotations on DTOs where the API contract requires them
- keep error semantics consistent with existing `400/403/404/409` handling
- update at least one reproducible manual request example
- run `./gradlew compileJava`

## Skill: Manual Verification

Use when there are no automated tests for the changed behavior.

Fast path:

1. start infra with `docker compose up -d`
2. run the app with `./gradlew bootRun`
3. choose the nearest scenario from [rest-client/scenarios/README.md](../rest-client/scenarios/README.md)
4. align `@baseUrl` in the `.http` file with `SERVER_PORT` from `.env`
5. use seeded users from [sql/test_users_1.sql](../sql/test_users_1.sql)

Known reusable accounts:

- `landlord1@example.com / password`
- `landlord2@example.com / password`
- `tenant1@example.com / password`
- `tenant2@example.com / password`
- `admin1@example.com / password`
- `admin2@example.com / password`

## Skill: Review Mode

Use when the user asks for a review rather than a feature.

Review priorities for this repo:

- invalid state transitions in `ClaimService`
- mismatch between BPMN and the actual coded workflow
- RBAC mismatches between Java constants, SQL seed data, and controller annotations
- partial-commit risk across auth DB, business DB, and MinIO
- schema/code drift between entities and SQL init files
- API examples that no longer match actual DTOs or response payloads

Do not stop at style issues if there is any business-logic, security, or consistency risk.
