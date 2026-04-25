# BLPS Project Context For Agents

## What this repo is

This repository is a single-module Spring Boot 3.2 / Java 17 REST service for handling penalty claims between three actors:

- `LANDLORD` creates a claim and can provide additional materials.
- `TENANT` responds to a claim if penalty grounds were found.
- `ADMIN` performs intake, assessment, final support decision, and user deactivation.

The business reference process is documented in [bpmn/blps1.bpmn](../bpmn/blps1.bpmn), but the executable truth is the Java code and SQL schema.

## Runtime topology

The app is not a microservice system. It is one application connected to three external components:

- business Postgres DB: claims, messages, status history, attachments
- auth Postgres DB: users, roles, privileges
- MinIO: object storage for attachments

Infrastructure and seed data live in:

- [docker-compose.yml](../docker-compose.yml)
- [sql/init_1.sql](../sql/init_1.sql)
- [sql/init_2.sql](../sql/init_2.sql)
- [sql/test_users_1.sql](../sql/test_users_1.sql)
- [docs/how_minio.txt](../docs/how_minio.txt)

Configuration comes from `.env` via `spring.config.import=file:.env[.properties]`. Use [.env.sample](../.env.sample) as the template and do not hardcode secrets into code or docs.

## Distributed transaction topology

This project really does use distributed transactions across two physical Postgres databases:

- `postgres-business` for claim workflow data
- `postgres-auth` for users / roles / privileges

Why this is true in practice:

- both containers enable `max_prepared_transactions=100` in [docker-compose.yml](../docker-compose.yml)
- both datasources are XA datasources based on `PGXADataSource`
- Hibernate is configured for JTA in both persistence units
- Narayana is the transaction manager and Hibernate JTA platform
- services use shared JTA `TransactionTemplate` beans

Relevant files:

- [src/main/java/blps/itmo/config/BusinessPersistenceConfig.java](../src/main/java/blps/itmo/config/BusinessPersistenceConfig.java)
- [src/main/java/blps/itmo/config/AuthPersistenceConfig.java](../src/main/java/blps/itmo/config/AuthPersistenceConfig.java)
- [src/main/java/blps/itmo/config/TransactionTemplateConfig.java](../src/main/java/blps/itmo/config/TransactionTemplateConfig.java)
- [src/main/java/blps/itmo/config/NarayanaJtaPlatform.java](../src/main/java/blps/itmo/config/NarayanaJtaPlatform.java)

The two databases are XA participants. MinIO is not.

## Core architecture

Primary packages:

- `config`: dual JPA persistence units, Narayana JTA, Security, MinIO client
- `controller`: REST endpoints
- `service`: business logic and transaction boundaries
- `entity.business`: business DB model
- `entity.auth`: auth/RBAC DB model
- `repository.business` / `repository.auth`: Spring Data repositories
- `dto`: request/response contracts
- `security`: Basic auth, principal, privilege constants
- `exception`: REST error handling

Important entry files:

- [src/main/java/blps/itmo/BlpsApplication.java](../src/main/java/blps/itmo/BlpsApplication.java)
- [src/main/resources/application.yml](../src/main/resources/application.yml)
- [build.gradle.kts](../build.gradle.kts)

## Source-of-truth map

When you need the real behavior, check these files first:

- claim lifecycle and transitions:
  [src/main/java/blps/itmo/service/ClaimService.java](../src/main/java/blps/itmo/service/ClaimService.java)
- user deactivation flow:
  [src/main/java/blps/itmo/service/UserService.java](../src/main/java/blps/itmo/service/UserService.java)
- MinIO upload/confirm/attach flow:
  [src/main/java/blps/itmo/service/MinioService.java](../src/main/java/blps/itmo/service/MinioService.java)
- public API surface:
  [src/main/java/blps/itmo/controller](../src/main/java/blps/itmo/controller)
- RBAC and method security:
  [src/main/java/blps/itmo/security/Privileges.java](../src/main/java/blps/itmo/security/Privileges.java),
  [src/main/java/blps/itmo/config/SecurityConfig.java](../src/main/java/blps/itmo/config/SecurityConfig.java),
  [src/main/java/blps/itmo/security/AppUserDetailsService.java](../src/main/java/blps/itmo/security/AppUserDetailsService.java)
- DB schemas and enum definitions:
  [sql/init_1.sql](../sql/init_1.sql), [sql/init_2.sql](../sql/init_2.sql)
- realistic end-to-end examples:
  [rest-client/scenarios/README.md](../rest-client/scenarios/README.md),
  [rest-client/claims-rbac.http](../rest-client/claims-rbac.http)

## Domain model and invariants

### Business process from BPMN

The BPMN in [bpmn/blps1.bpmn](../bpmn/blps1.bpmn) is the business reference model for a penalty-claim workflow:

1. landlord initiates a claim with evidence
2. platform checks whether the data is sufficient
3. if not sufficient, landlord provides additional materials
4. platform assesses whether there are grounds for penalty
5. if yes, tenant can provide a response
6. support makes the final decision
7. outcome is either penalty applied or claim closed without penalty

The BPMN is conceptual. There is no Camunda runtime or workflow engine in this repository. The real execution model is a hand-coded state machine in `ClaimService`.

### BPMN to API mapping

Map BPMN elements to REST behavior like this:

- `UserTask_CreateClaim` -> `POST /api/claims`
- `Activity_1p30r0x` + `ExclusiveGateway_EnoughData` -> `POST /api/claims/{id}/intake`
- `UserTask_ProvideDocs` -> `POST /api/claims/{id}/additional-info`
- `Activity_0gw4i7b` + `ExclusiveGateway_RulesViolated` -> `POST /api/claims/{id}/assessment`
- `IntermediateThrowEvent_RequestComment` + `UserTask_RespondComment` -> `POST /api/claims/{id}/tenant-response`
- `UserTask_SupportReview` + `ExclusiveGateway_ApprovePenalty` -> `POST /api/claims/{id}/support-decision`
- `ServiceTask_ApplyPenalty` -> `supportDecision(applyPenalty=true)` plus penalty counter update in auth DB
- `ServiceTask_CloseWithoutPenalty` -> either `assessment(penaltyGrounds=false)` or `supportDecision(applyPenalty=false)`

### Scenario matrix from `rest-client/scenarios`

The `.http` scenarios act as the current executable business specification:

- `00-rbac-smoke.http`: authn/authz smoke checks
- `10-intake-additional-assessment-no.http`: insufficient data -> landlord adds materials -> admin closes without penalty during assessment
- `11-intake-additional-final-penalty.http`: insufficient data -> landlord adds materials -> tenant responds -> final penalty applied
- `12-intake-additional-final-no-penalty.http`: insufficient data -> landlord adds materials -> tenant responds -> final no-penalty decision
- `20-intake-ok-assessment-no.http`: sufficient data immediately -> admin closes without penalty during assessment
- `21-intake-ok-final-penalty.http`: sufficient data immediately -> tenant responds -> final penalty applied
- `22-intake-ok-final-no-penalty.http`: sufficient data immediately -> tenant responds -> final no-penalty decision

Together they cover the main decision branches:

- enough data now? yes/no
- penalty grounds found? yes/no
- if grounds exist and tenant responds, does support apply penalty? yes/no

Treat these files as the best behavioral reference after the Java service code.

### Claim statuses

The claim state machine is implemented in `ClaimService` and backed by the `claimstatus` enum in `sql/init_1.sql`.

Main path:

1. `SUBMITTED` after `POST /api/claims`
2. `NEED_ADDITIONAL_INFO` or `UNDER_ASSESSMENT` after intake
3. `UNDER_ASSESSMENT` after landlord replies with additional info
4. `AWAITING_TENANT_RESPONSE` or `CLOSED_NO_PENALTY` after assessment
5. `SUPPORT_REVIEW` after tenant response
6. `PENALTY_APPLIED` or `CLOSED_NO_PENALTY` after final support decision

Implementation note: `INTAKE_REVIEW` exists in the enum and is accepted by `intakeDecision`, but no current code path actually sets a claim into `INTAKE_REVIEW`. Treat it as a latent or unfinished status unless the task explicitly activates it.

If you change statuses or transitions, update all of these together:

- Java enum `ClaimStatus`
- Postgres enum/check constraints in `sql/init_1.sql`
- service transition logic in `ClaimService`
- any affected rest-client/Postman scenarios

### Cross-database model

There are no cross-DB foreign keys. Business tables store user ids as plain numeric values that refer to rows in the auth DB. Never assume JPA relations between business entities and auth users exist or should exist.

### Transactions

Cross-DB consistency is implemented with Narayana JTA and `TransactionTemplate`, not with local `@Transactional` assumptions.

Key files:

- [src/main/java/blps/itmo/config/BusinessPersistenceConfig.java](../src/main/java/blps/itmo/config/BusinessPersistenceConfig.java)
- [src/main/java/blps/itmo/config/AuthPersistenceConfig.java](../src/main/java/blps/itmo/config/AuthPersistenceConfig.java)
- [src/main/java/blps/itmo/config/TransactionTemplateConfig.java](../src/main/java/blps/itmo/config/TransactionTemplateConfig.java)

If a change writes to both Postgres databases, keep it inside the JTA transaction templates already used by services.

Current multi-DB write paths:

- `ClaimService.createClaim(...)`
  business DB: create claim + status history + attach evidence
  auth DB: reads landlord/tenant and also writes a new disabled user row
- `ClaimService.supportDecision(...)` when `applyPenalty=true`
  business DB: final claim decision + history + optional note
  auth DB: increments tenant `penalty_count`
- `UserService.deactivateUser(...)`
  auth DB: disables the user
  business DB: closes all open claims where the user is landlord or tenant

Not every service method is multi-write. Many actions only write the business DB and read the auth DB for role validation.

### Attachments and MinIO

MinIO is not XA-aware. The attachment flow is intentionally a saga:

1. `POST /api/storage/attachments/init` creates DB metadata with `uploaded=false`
2. client uploads directly to MinIO via presigned `PUT`
3. `POST /api/storage/attachments/confirm` verifies the object and marks the attachment uploaded
4. claim-related actions attach confirmed objects to claims/messages inside a DB transaction

Do not collapse this into a fake 2PC design. Read [docs/how_minio.txt](../docs/how_minio.txt) before changing attachment behavior.

Important nuance: `initAttachment` and `confirmUpload` run inside the JTA transaction template, but MinIO itself is still outside the XA boundary. These methods combine DB writes with external MinIO calls, not true distributed commit with MinIO.

### Security model

Authentication is HTTP Basic against the auth DB.

Authorization has two layers:

- controller method guards with `@PreAuthorize`
- service-level ownership checks for landlord/tenant access

Never trust request payload actor ids when the authenticated principal already supplies them. This project intentionally derives the acting user from `AppUserPrincipal`.

### Important implementation quirks

These are current behavioral facts, even if they look odd:

- `ClaimService.createClaim(...)` currently inserts a disabled auth-DB user whose email is derived from claim title and id. This makes claim creation a true auth+business distributed write, but it is not explained by the BPMN.
- `TenantResponseRequest.agree` exists in the DTO, but current service logic does not use it to influence state transitions or decisions.
- `getAdditionalInfoAttachmentKeys(...)` actually returns presigned download URLs, not raw object keys.
- `ClaimResponse` does not expose assessment, penalty amount, history, or messages, so scenario final `GET`s mostly validate reachability and top-level claim state rather than the full internal decision payload.

### BPMN vs implementation gaps

Assume the following are not implemented unless you add them:

- no workflow engine execution of the BPMN
- no timer-driven path for the BPMN 3-day tenant response timeout
- no automatic transition from `AWAITING_TENANT_RESPONSE` to `SUPPORT_REVIEW` without tenant action
- no actual notification dispatch for `NotifyDecision`
- no external penalty application integration beyond updating business state and incrementing `penalty_count`
- BPMN assignee metadata is not authoritative; actual access control is role/privilege-based in Spring Security

### Response shape

`ClaimResponse` is intentionally compact. It currently returns only base claim data plus attachment download URLs. It does not expose every internal field from the entity. If a task needs assessment/penalty/history/message data, extend the DTO and controller/service flow deliberately instead of assuming it is already part of the API.

## How to run and verify

Useful local commands:

- infra up: `docker compose up -d`
- compile: `./gradlew compileJava`
- tests: `./gradlew test`
- app locally: `./gradlew bootRun`
- packaged jar: `./gradlew bootJar`

There is currently no meaningful automated test suite under `src/test`; practical verification lives in:

- `rest-client/scenarios/*.http`
- `rest-client/claims-rbac.http`
- `postman/collections/**`

The REST client files keep their own `@baseUrl`; make sure it matches `SERVER_PORT` from `.env` before using them.

## Editing rules specific to this repo

- Before changing behavior, read the relevant service method end-to-end. DTO names and BPMN labels are not enough to infer real logic.
- When working on process logic, compare three layers together: BPMN, `rest-client/scenarios`, and `ClaimService`. The BPMN alone is not the implemented contract.
- When changing privileges or roles, synchronize Java constants, SQL seed data, and controller annotations.
- When changing schema-level enums or constraints, keep Java enums/entities in sync.
- When changing upload behavior, preserve `init -> upload -> confirm -> attach` semantics unless the task explicitly redesigns the workflow.
- When touching distributed logic, explicitly decide whether the change affects business DB only, auth DB only, both Postgres DBs under JTA, or MinIO saga steps outside XA.
- When adding or changing endpoints, update at least one HTTP scenario or Postman request so the contract remains reproducible.
- Check `git status` before editing. The worktree may already contain user changes; do not revert them unless explicitly asked.

## Fast orientation order

If the task is broad and you need to understand the repo quickly, read in this order:

1. `build.gradle.kts`
2. `src/main/resources/application.yml`
3. `docker-compose.yml`
4. `sql/init_1.sql`
5. `sql/init_2.sql`
6. `bpmn/blps1.bpmn`
7. `rest-client/scenarios/*`
8. `controller/*`
9. `service/ClaimService.java`
10. `service/MinioService.java`
