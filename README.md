# Odin Deployer

> A reactive, gRPC-based deployment orchestration service for managing multi-component applications across environments

[![CI](https://github.com/dream11/odin-deployer/actions/workflows/ci.yaml/badge.svg)](https://github.com/dream11/odin-deployer/actions/workflows/ci.yaml)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Release](https://img.shields.io/github/v/release/dream11/odin-deployer?label=Latest%20Release)](https://github.com/dream11/odin-deployer/releases)

---

## Table of Contents

- [Motivation](#motivation)
- [Key Features](#key-features)
- [Quickstart](#quickstart)
- [Prerequisites and Supported Platforms](#prerequisites-and-supported-platforms)
- [Architecture Overview](#architecture-overview)
- [Core Concepts](#core-concepts)
  - [Services](#services)
  - [Components](#components)
  - [Environments](#environments)
  - [Provisioning Configs](#provisioning-configs)
  - [Add Component to a Service](#add-component-to-a-service)
  - [Remove Component from a Service](#remove-component-from-a-service)
- [Configuration Reference](#configuration-reference)
- [Running Tests](#running-tests)
- [CI/CD](#cicd)
- [Security and Secrets](#security-and-secrets)
- [Contributing](#contributing)
- [Code of Conduct](#code-of-conduct)
- [License](#license)
- [Contact and Maintainers](#contact-and-maintainers)
- [Roadmap](#roadmap)

---

## Motivation

**Odin Deployer** is designed to solve the challenge of orchestrating complex, multi-component application deployments across diverse cloud environments. Traditional deployment tools often struggle with:

- Managing dependencies between components (databases, message queues, application servers)
- Providing real-time, streaming deployment status
- Supporting dynamic component addition/removal without full redeployment
- Maintaining audit trails and rollback capabilities
- Integrating with heterogeneous cloud providers

Odin Deployer addresses these by providing a reactive, event-driven platform that treats every deployment as a set of orchestrated tasks with full lifecycle management.

---

## Key Features

- **Reactive Architecture**: Built on Vert.x with RxJava for non-blocking, high-throughput operations
- **gRPC & REST APIs**: Modern gRPC streaming APIs for real-time status updates, plus REST endpoints for integration
- **Multi-Environment Management**: Create, deploy, and manage services across isolated environments
- **Component Lifecycle Operations**: Deploy, undeploy, operate (scale/restart/update), and dynamically add/remove components
- **Task-Based Orchestration**: Every operation creates auditable tasks with state tracking (in-progress, successful, failed)
- **Provider-Agnostic**: Abstracts cloud provider specifics via provisioning configs and account management
- **Database-Driven State**: MySQL-backed persistence with Liquibase migrations for schema evolution
- **Queue Integration**: SQS-based asynchronous message processing for decoupled orchestration
- **JWT Authentication**: Secure API access with org-scoped authorization
- **Extensible Validation**: Pluggable validators for component dependencies, service states, and provisioning rules
- **Interceptor Framework**: Pre/post-deployment hooks for custom logic (e.g., config injection, notifications)

---

## Quickstart

Get Odin Deployer running locally in 5 steps:

1. **Clone the repository**
   ```bash
   git clone https://github.com/dream11/odin-deployer.git
   cd odin-deployer
   ```

2. **Set up MySQL database**
   ```bash
   # Using Docker
   docker run --name odin-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=odin_deployer -p 3306:3306 -d mysql:8.2
   
   # Run Liquibase migrations
   mvn liquibase:update -Dliquibase.url=jdbc:mysql://localhost:3306/odin_deployer \
     -Dliquibase.username=root -Dliquibase.password=root
   ```

3. **Configure environment variables** *(SUGGESTED)*
   ```bash
   export ODIN_MYSQL_MASTER_HOST=localhost
   export ODIN_MYSQL_SLAVE_HOST=localhost
   export ODIN_MYSQL_USERNAME=root
   export ODIN_MYSQL_PASSWORD=root
   export ODIN_PUBLIC_KEY="<your-jwt-public-key>"
   export ODIN_PRIVATE_KEY="<your-jwt-private-key>"
   # For local testing, SQS queues can be mocked with LocalStack
   export ODIN_QUEUE_REQUEST_ENDPOINT=http://localhost:4566
   export ODIN_QUEUE_REQUEST_URL=<sqs-queue-url>
   export ODIN_QUEUE_RESPONSE_ENDPOINT=http://localhost:4566
   export ODIN_QUEUE_RESPONSE_URL=<sqs-queue-url>
   ```

4. **Build the application**
   ```bash
   mvn clean package
   ```

5. **Run the application**
   ```bash
   java -jar target/odin-deployer/odin-deployer-fat.jar
   ```

   The service will start on:
   - gRPC: `localhost:8080`
   - REST API: `localhost:8080/v1/...`
   - Health check: `http://localhost:8080/healthcheck`

---

## Prerequisites and Supported Platforms

### Prerequisites

- **Java 17+** (OpenJDK or Temurin recommended)
- **Maven 3.8+**
- **MySQL 8.0+** (for persistence)
- **SQS** or **LocalStack** (for message queuing; configurable via `queueProvider`)
- **Docker** (optional, for containerized deployments)

### Supported Platforms

- **Development**: macOS (ARM64/x86_64), Linux (x86_64), Windows (WSL2)
- **Production**: Linux containers (Docker/Kubernetes)
- **Architectures**: `amd64`, `arm64` (multi-arch Docker images available)

---

## Architecture Overview

### High-Level Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Odin CLI / External Clients              │
└──────────────────────┬──────────────────────────────────────────┘
                       │ (gRPC/REST)
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Odin Deployer (Vert.x)                     │
│  ┌──────────────┐  ┌───────────────┐  ┌────────────────────┐   │
│  │ GrpcVerticle │  │ RestVerticle  │  │ ConsumerVerticle   │   │
│  │ (Port 8080)  │  │ (Port 8080)   │  │ (SQS Polling)      │   │
│  └──────┬───────┘  └───────┬───────┘  └─────────┬──────────┘   │
│         │                  │                     │              │
│         └──────────────────┼─────────────────────┘              │
│                            ▼                                    │
│              ┌──────────────────────────────┐                   │
│              │    Business Logic Layer      │                   │
│              │  - ServiceBusiness           │                   │
│              │  - EnvironmentBusiness       │                   │
│              │  - ComponentEnrichment       │                   │
│              │  - InterceptorService        │                   │
│              └──────────────┬───────────────┘                   │
│                             │                                   │
│              ┌──────────────┴───────────────┐                   │
│              │       DAO Layer              │                   │
│              │  - ServiceTaskDao            │                   │
│              │  - ComponentTaskDao          │                   │
│              │  - EnvironmentDao            │                   │
│              └──────────────┬───────────────┘                   │
└─────────────────────────────┼───────────────────────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           ▼                  ▼                  ▼
    ┌──────────┐      ┌──────────────┐   ┌─────────────┐
    │  MySQL   │      │ SQS Request  │   │SQS Response │
    │ (State)  │      │   Queue      │   │   Queue     │
    └──────────┘      └──────────────┘   └─────────────┘
                              │                  │
                              └────────┬─────────┘
                                       ▼
                            ┌──────────────────────┐
                            │ External Orchestrator│
                            │ (Provisioning Engine)│
                            └──────────────────────┘
```

### Component Breakdown

| Component                | Location                                  | Responsibility                                                                 |
|--------------------------|-------------------------------------------|--------------------------------------------------------------------------------|
| **MainLauncher**         | `com.dream11.odin.MainLauncher`           | Application entry point; initializes Vert.x and Guice DI                       |
| **MainVerticle**         | `com.dream11.odin.verticle.MainVerticle`  | Deploys all sub-verticles (gRPC, REST, Consumer)                               |
| **GrpcVerticle**         | `com.dream11.odin.verticle.GrpcVerticle`  | Exposes gRPC services (EnvironmentService, ServiceService, AuthService, Logs)  |
| **RestVerticle**         | `com.dream11.odin.verticle.RestVerticle`  | Exposes REST endpoints (`/v1/operate/...`, `/healthcheck`)                     |
| **ConsumerVerticle**     | `com.dream11.odin.verticle.ConsumerVerticle` | Polls SQS response queue for async operation results                        |
| **ServiceBusiness**      | `com.dream11.odin.service.ServiceBusiness` | Orchestrates deploy/undeploy/operate workflows                                |
| **EnvironmentBusiness**  | `com.dream11.odin.service.EnvironmentBusiness` | Manages environment lifecycle (create/delete/status)                      |
| **DAO Layer**            | `com.dream11.odin.dao.*`                  | MySQL data access for tasks, environments, components                          |
| **Proto Definitions**    | `src/main/proto/dream11/od/**/*.proto`    | gRPC service and DTO contracts                                                 |
| **Liquibase Migrations** | `src/main/resources/db/mysql/migrations/` | Database schema versioning                                                     |

---

## Core Concepts

### Services

A **Service** represents a deployable application composed of one or more components. It is defined by:

- **Service Definition** (JSON/Protobuf):
  - `name`: Unique identifier (e.g., `user-api`)
  - `version`: Semantic version (e.g., `1.2.3`)
  - `team`: Owning team
  - `components`: Array of component definitions

**Example Service Definition**:
```json
{
  "name": "user-api",
  "version": "1.0.0",
  "team": "backend-team",
  "components": [
    {
      "name": "api-server",
      "type": "webservice",
      "version": "2.1.0",
      "depends_on": ["database", "cache"],
      "config": {
        "port": 8080,
        "replicas": 3
      }
    },
    {
      "name": "database",
      "type": "postgres",
      "version": "14.5",
      "config": {
        "storage": "100Gi"
      }
    },
    {
      "name": "cache",
      "type": "redis",
      "version": "7.0",
      "config": {
        "memory": "2Gi"
      }
    }
  ]
}
```

**Service Lifecycle States**:
- `DEPLOYING`: Deployment in progress
- `DEPLOYED`: All components successfully deployed
- `FAILED`: One or more components failed
- `UNDEPLOYING`: Teardown in progress
- `UNDEPLOYED`: Service removed from environment

### Components

A **Component** is a building block of a service (e.g., database, web server, message queue). Each component has:

- **Component Definition**:
  - `name`: Unique within service (e.g., `database`)
  - `type`: Component type registered in Odin (e.g., `postgres`, `redis`, `webservice`)
  - `version`: Component version
  - `depends_on`: List of component names this component requires
  - `config`: Base configuration (JSON object)

Components are deployed according to their dependency graph (topologically sorted).

### Environments

An **Environment** is an isolated namespace for deploying services (e.g., `dev`, `staging`, `prod`). Key properties:

- `name`: Unique identifier
- `org_id`: Organization scope
- `provisioning_type`: Default provisioning strategy (e.g., `kubernetes`, `ecs`)
- `is_active`: Soft-delete flag
- `auto_deletion_time`: Scheduled cleanup (for ephemeral envs)

**Create Environment via gRPC**:
```protobuf
message CreateEnvironmentRequest {
  string env_name = 1;          // e.g., "dev-feature-branch"
  repeated string accounts = 2; // Provider account names
}
```

**Environment States**:
- `CREATING`: Provisioning infrastructure
- `READY`: Available for deployments
- `DELETING`: Teardown in progress
- `DELETED`: Removed

### Provisioning Configs

**Provisioning configs** define *how* and *where* components are deployed. They map components to cloud provider resources.

**Structure** (`src/main/resources/schema/provisioning/schema.json`):
```json
[
  {
    "component_name": "api-server",
    "deployment_type": "kubernetes-deployment",  // Provider-specific type
    "params": {
      "namespace": "production",
      "cpu": "2",
      "memory": "4Gi",
      "image": "myregistry/user-api:1.0.0"
    },
    "env_variables": {
      "DB_HOST": "postgres.prod.svc.cluster.local",
      "CACHE_URL": "redis://cache:6379"
    }
  },
  {
    "component_name": "database",
    "deployment_type": "rds-postgres",
    "params": {
      "instance_class": "db.t3.medium",
      "storage": "100",
      "multi_az": true
    }
  }
]
```

**Usage in Deployment**:
```bash
# Via gRPC (pseudo-code)
DeployServiceRequest {
  env_name: "prod"
  service_definition: <service-def.json>
  provisioning_config: <provisioning-config.json>
}
```

### Add Component to a Service

**Scenario**: Your service is running, and you need to add a new component (e.g., a cache layer).

#### Step-by-Step Process

1. **Define the new component** in your service definition:
   ```json
   {
     "name": "cache",
     "type": "redis",
     "version": "7.0",
     "config": {
       "memory": "2Gi"
     }
   }
   ```

2. **Create provisioning config** for the new component:
   ```json
   {
     "component_name": "cache",
     "deployment_type": "elasticache-redis",
     "params": {
       "node_type": "cache.t3.micro",
       "num_nodes": 2
     }
   }
   ```

3. **Call the OperateService gRPC endpoint**:
   ```protobuf
   OperateServiceRequest {
     env_name: "prod"
     service_name: "user-api"
     component_name: "cache"              // Name of the new component
     is_component_operation: false        // Service-level operation
     operation: "ADD_COMPONENT"
     config: {
       "component_definition": [
         {
           "name": "cache",
           "type": "redis",
           "version": "7.0",
           "config": {"memory": "2Gi"}
         }
       ],
       "provisioning_config": [
         {
           "component_name": "cache",
           "deployment_type": "elasticache-redis",
           "params": {"node_type": "cache.t3.micro"}
         }
       ]
     }
   }
   ```

4. **Monitor progress** via streaming response:
   ```protobuf
   OperateServiceResponse {
     service_response: {
       service_status: {
         service_status: "IN_PROGRESS"
         service_action: "ADD_COMPONENT"
       }
       components_status: [
         {
           component_name: "cache"
           component_status: "DEPLOYING"
           component_action: "DEPLOY"
         }
       ]
     }
   }
   ```

#### Under the Hood

- **Validation**: `AddComponentValidator` checks:
  - Component doesn't already exist in service
  - Dependencies are met
  - Provisioning config is valid
- **Task Creation**: A `service_task` and `component_task` are created in MySQL
- **Queue Message**: A provisioning request is sent to SQS (`request` queue)
- **Orchestrator**: External provisioning engine (e.g., Terraform/CloudFormation runner) processes the message
- **Response**: Orchestrator sends result to SQS (`response` queue), consumed by `ConsumerVerticle`
- **State Update**: Database updated; clients notified via gRPC stream

#### Rollback/Failure Handling

- If the component deployment fails, the task status is marked `FAILED`
- Retry the operation by calling `OperateService` again with the same payload
- To remove a failed component, use `REMOVE_COMPONENT` operation (see below)

### Remove Component from a Service

**Scenario**: Decommission a component (e.g., migrating from legacy cache to new one).

#### Step-by-Step Process

1. **Call OperateService with REMOVE_COMPONENT**:
   ```protobuf
   OperateServiceRequest {
     env_name: "prod"
     service_name: "user-api"
     component_name: "old-cache"
     is_component_operation: false
     operation: "REMOVE_COMPONENT"
     config: {
       "component_name": "old-cache"
     }
   }
   ```

2. **Validation Phase**:
   - `RemoveComponentValidator` ensures:
     - Component exists in the service
     - Component is in a terminal state (`DEPLOYED`, `FAILED`, or `UNDEPLOY` with `FAILED`/`IN_PROGRESS`)
     - No other components depend on it (dependency graph check)

3. **Orchestration**:
   - A `service_task` with action `UNDEPLOY` is created
   - Component resources are deprovisioned (cloud resources deleted)
   - Component task marked `SUCCESSFUL` or `FAILED`

4. **Side Effects**:
   - Component entry remains in DB for audit but is marked as undeployed
   - Service continues running with remaining components

#### Safety Notes

- **Dependency Check**: If component A depends on component B, removing B will fail validation. Remove A first.
- **Data Loss Warning**: Removing stateful components (databases, volumes) may result in data loss. Ensure backups before removal.
- **Migration Strategy**:
  1. Deploy new component (e.g., `new-cache`)
  2. Migrate traffic/data
  3. Remove old component (e.g., `old-cache`)

**Example Migration Workflow** *(SUGGESTED)*:
```bash
# Step 1: Add new component
odin-cli operate add-component --env prod --service user-api \
  --component-def new-cache.json --provisioning new-cache-provisioning.json

# Step 2: Update service config to use new-cache (via OPERATE operation)
odin-cli operate component --env prod --service user-api \
  --component api-server --operation update-config --config '{"CACHE_URL": "new-cache:6379"}'

# Step 3: Remove old component
odin-cli operate remove-component --env prod --service user-api --component old-cache
```

---

## Configuration Reference

Odin Deployer uses HOCON format (Lightbend Config) with environment variable overrides.

### Configuration Files

| File                                    | Purpose                                                      |
|-----------------------------------------|--------------------------------------------------------------|
| `src/main/resources/application.conf`   | Production config (env var placeholders)                     |
| `src/main/resources/application-default.conf` | Development defaults (for local testing)            |

### Key Configuration Sections

#### 1. MySQL Database

```hocon
mysql {
  master {
    connectOptions {
      host = ${?ODIN_MYSQL_MASTER_HOST}      // Default: localhost
      database = "odin_deployer"
      user = ${?ODIN_MYSQL_USERNAME}         // Default: root
      password = ${?ODIN_MYSQL_PASSWORD}     // Default: ""
      idleTimeout = 300                      // seconds
      queryTimeout = 180000                  // milliseconds
    }
    poolOptions {
      maxSize = 20                           // Connection pool size
    }
  }
  slave {
    // Same structure as master (for read replicas)
  }
}
```

#### 2. Message Queues (SQS)

```hocon
queue {
  request {
    endpoint = ${?ODIN_QUEUE_REQUEST_ENDPOINT}      // For LocalStack: http://localhost:4566
    queueUrl = ${?ODIN_QUEUE_REQUEST_URL}           // Full SQS queue URL
    queueProvider = "sqs"                           // Currently only SQS supported
    region = "us-east-1"
  }
  response {
    endpoint = ${?ODIN_QUEUE_RESPONSE_ENDPOINT}
    queueUrl = ${?ODIN_QUEUE_RESPONSE_URL}
    queueProvider = "sqs"
    region = "us-east-1"
    receiveConfig {
      maxMessages = 5                               // SQS long polling batch size
    }
  }
}
```

#### 3. JWT Authentication

```hocon
authConfig {
  jwtConfig {
    privateKey = ${ODIN_PRIVATE_KEY}                // RSA private key (PEM format, base64 encoded)
    publicKey = ${ODIN_PUBLIC_KEY}                  // RSA public key (PEM format, base64 encoded)
    expirationMillis = 360000000                    // Token TTL (100 hours for dev)
  }
}
```

**Generating Keys** *(SUGGESTED)*:
```bash
# Generate RSA key pair
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem

# Base64 encode for env vars
export ODIN_PRIVATE_KEY=$(cat private.pem | base64)
export ODIN_PUBLIC_KEY=$(cat public.pem | base64)
```

#### 4. Odin Account Manager (gRPC Client)

```hocon
odinAccountManagerConfig {
  host = ${?ODIN_ACCOUNT_MANAGER_HOST}              // Default: localhost
  port = ${?ODIN_ACCOUNT_MANAGER_PORT}              // Default: 80
  channel = "PLAINTEXT"                             // or "TLS"
}
```

#### 5. Interceptors (Pre/Post Deployment Hooks)

```hocon
interceptors {
  component = ${?ODIN_INTERCEPTOR_COMPONENT_URLS}   // Comma-separated HTTP endpoints
  config {
    timeout = 3                                     // seconds
    retryCount = 2
  }
}
```

#### 6. Log Store (Optional)

```hocon
logStoreConfig {
  host = ${?ODIN_LOGSTORE_HOST}
  port = ${?ODIN_LOGSTORE_PORT}
  enableSSL = false
  pollingFrequencySeconds = 2
  batchSize = 50
}
```

### Sample `.env` File *(SUGGESTED)*

```bash
# MySQL
export ODIN_MYSQL_MASTER_HOST=mysql.prod.example.com
export ODIN_MYSQL_SLAVE_HOST=mysql-replica.prod.example.com
export ODIN_MYSQL_USERNAME=odin_user
export ODIN_MYSQL_PASSWORD=super-secret-password

# SQS
export ODIN_QUEUE_REQUEST_ENDPOINT=https://sqs.us-east-1.amazonaws.com
export ODIN_QUEUE_REQUEST_URL=https://sqs.us-east-1.amazonaws.com/123456789/odin-requests
export ODIN_QUEUE_RESPONSE_ENDPOINT=https://sqs.us-east-1.amazonaws.com
export ODIN_QUEUE_RESPONSE_URL=https://sqs.us-east-1.amazonaws.com/123456789/odin-responses

# JWT
export ODIN_PUBLIC_KEY=$(cat keys/public.pem | base64)
export ODIN_PRIVATE_KEY=$(cat keys/private.pem | base64)

# Account Manager
export ODIN_ACCOUNT_MANAGER_HOST=account-manager.prod.example.com
export ODIN_ACCOUNT_MANAGER_PORT=443
export ODIN_ACCOUNT_MANAGER_CHANNEL=TLS

# Interceptors (optional)
export ODIN_INTERCEPTOR_COMPONENT_URLS=http://validation-service:8080/hook,http://metrics-collector:9090/hook
export ODIN_INTERCEPTOR_TIMEOUT_SECONDS=5
export ODIN_INTERCEPTOR_RETRY_COUNT=3
```

**Loading the config**:
```bash
source .env
java -jar target/odin-deployer/odin-deployer-fat.jar
```

---

## Running Tests

Odin Deployer uses JUnit 5, Testcontainers (MySQL, LocalStack), and Mockito for comprehensive testing.

### Unit Tests

```bash
mvn test
```

### Integration Tests

Integration tests spin up MySQL and SQS via Testcontainers:

```bash
mvn verify
```

**Environment Variables for Tests**:
- `ODIN_PUBLIC_KEY` and `ODIN_PRIVATE_KEY` must be set (see CI workflow)

### Code Coverage

Jacoco plugin generates coverage reports:

```bash
mvn clean verify
open target/site/jacoco/index.html
```

**Coverage Requirements**:
- Minimum: 70% line coverage (enforced in Maven build)

### Linting and Formatting

**Google Java Format** (via GitHub Actions):
- Check formatting: `mvn fmt:check`
- Auto-format: `mvn fmt:format`

**Buf (Protobuf Linting)**:
- Install Buf: `brew install bufbuild/buf/buf` (macOS)
- Lint protos: `buf lint src/main/proto`
- Breaking change detection: `buf breaking --against '.git#branch=master'`

---

## CI/CD

### Continuous Integration (`.github/workflows/ci.yaml`)

**Triggers**:
- Pull requests (`synchronize`, `opened`, `reopened`)
- Pushes to `master` branch

**Pipeline Steps**:
1. Checkout code
2. Set up JDK 17 (Temurin)
3. Set up QEMU and Docker Buildx (for multi-arch builds)
4. Run tests (`mvn clean verify`)
5. Publish test report (JUnit XML → GitHub Actions UI)
6. Validate version (must end with `-SNAPSHOT` for PRs)
7. Build and push Docker image (tagged with `<version>-SNAPSHOT`)

### Release Workflow (`.github/workflows/release.yml`)

**Triggers**:
- GitHub Release creation

**Pipeline Steps**:
1. Validate release is from `master` branch
2. Extract version from Git tag (format: `vX.Y.Z`)
3. Update `pom.xml` with release version
4. Run full test suite
5. Upload `odin-deployer-fat.jar` to GitHub Release assets
6. Build and push Docker image (tags: `<version>`, `latest`)
7. Bump version in `pom.xml` (automated script)

**Creating a Release**:
```bash
# Tag the release
git tag v1.2.3
git push origin v1.2.3

# Create GitHub Release from tag (triggers workflow)
```

### Docker Image

**Registry**: Configured via GitHub repository variables (`DOCKER_REGISTRY`, `DOCKER_REPOSITORY`)

**Tags**:
- `<version>-SNAPSHOT`: Development builds from `master`
- `<version>`: Production releases
- `latest`: Latest stable release

**Pull Image**:
```bash
docker pull <registry>/<repository>:latest
```

---

## Security and Secrets

### Secret Management

**DO NOT** commit secrets to the repository. Use:

1. **Environment Variables**: For local development and CI
2. **Kubernetes Secrets**: For production deployments
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: odin-deployer-secrets
   type: Opaque
   data:
     ODIN_MYSQL_PASSWORD: <base64-encoded>
     ODIN_PRIVATE_KEY: <base64-encoded>
   ```
3. **AWS Secrets Manager / HashiCorp Vault**: For managed secret rotation

### JWT Token Security

- **Algorithm**: RS256 (RSA with SHA-256)
- **Key Rotation**: Rotate keys every 90 days *(SUGGESTED)*
- **Token Claims**:
  - `sub`: User ID (email)
  - `orgid`: Organization ID (numeric)
  - `exp`: Expiration timestamp

**Example Token Payload**:
```json
{
  "sub": "user@example.com",
  "orgid": 12345,
  "iat": 1609459200,
  "exp": 1609545600
}
```

### Database Credentials

- Use **least privilege** MySQL users (no `SUPER` privilege required)
- Separate read-only user for slave connections
- Enable **SSL/TLS** for MySQL connections in production:
  ```hocon
  mysql.master.connectOptions {
    sslMode = "REQUIRED"
  }
  ```

### Network Security

- **gRPC**: Use TLS in production (configure via Vert.x `HttpServerOptions`)
- **SQS**: Use IAM roles (EC2/EKS instance profiles) instead of hardcoded credentials
- **Firewall**: Restrict MySQL and SQS access to application VPC/subnet

---

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

**Quick Start**:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make changes and add tests
4. Run `mvn fmt:format` and `mvn verify`
5. Commit with [Conventional Commits](https://www.conventionalcommits.org/) format
6. Open a pull request

---

## Code of Conduct

This project adheres to a Code of Conduct. By participating, you agree to uphold this code. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for details.

---

## License

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.

- **You may**: Use, modify, and distribute this software
- **You must**: Disclose source code changes, include the original license
- **LGPL specifics**: Can link with proprietary software (unlike GPL); modifications to this library must remain LGPL

See [LICENSE](LICENSE) for full terms.

**Source File Header** *(SUGGESTED)*:
```java
/*
 * Odin Deployer - Deployment orchestration service
 * Copyright (C) 2024 Dream11
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
```

---

## Contact and Maintainers

### Primary Maintainer

- **Akshay Patidar** ([@akshaypatidar1999](https://github.com/akshaypatidar1999))

### Governance

- **Code Review**: All PRs require 1 approval from a maintainer
- **Release Cadence**: Monthly minor releases; patch releases as needed
- **Security Issues**: Report to [security@dream11.com](mailto:security@dream11.com) (do not open public issues)

### Community

- **Issues**: [GitHub Issues](https://github.com/dream11/odin-deployer/issues)
- **Discussions**: [GitHub Discussions](https://github.com/dream11/odin-deployer/discussions)

---

## Roadmap

### Planned Features

- [ ] **Multi-Tenancy**: Full org isolation with RBAC
- [ ] **WebSocket API**: Real-time deployment logs and events
- [ ] **Terraform Integration**: Native Terraform provider for Odin
- [ ] **Component Templates**: Pre-built component definitions (Helm charts, Terraform modules)
- [ ] **Rollback Support**: One-click revert to previous service version
- [ ] **Cost Tracking**: Integrate with cloud billing APIs for cost attribution
- [ ] **GitOps Mode**: Sync deployments from Git repositories (Flux/ArgoCD-style)
- [ ] **Plugin System**: Extensible component types via SPI

### Proposing Changes

1. Open a **GitHub Discussion** for large features
2. Create a **GitHub Issue** for bug reports or small enhancements
3. Submit a **Pull Request** with implementation + tests

---

## Assumptions & Unknowns

### Assumptions Made

1. **External Orchestrator**: The system assumes an external provisioning engine (not included in this repo) processes SQS request queue messages and provisions cloud resources. This orchestrator must:
   - Understand component types (e.g., `kubernetes-deployment`, `rds-postgres`)
   - Support the provider(s) specified in provisioning configs
   - Return status updates to the SQS response queue

2. **Odin Account Manager**: The `odinAccountManagerConfig` references a separate gRPC service (`ProviderAccountService`) that manages cloud provider credentials. This service is not part of `odin-deployer`.

3. **Authentication**: JWT tokens are assumed to be issued by an external identity provider (IdP). Odin Deployer only **verifies** tokens, not issues them.

4. **Logging**: The `logStoreConfig` section references an external log aggregation system (likely Elasticsearch or similar). Deployment logs are pushed to this system but the implementation is abstracted.

### Unknowns

1. **Component Type Registry**: The database schema includes a `component` table with `component_type` and `component_version`, but the mechanism for registering new component types is unclear. Missing:
   - API/CLI to add new component types
   - Schema/defaults storage for component types
   - Validation of component configs against type schemas

2. **Interceptor Contract**: The `interceptors.component` config accepts HTTP URLs, but the request/response format for these hooks is not documented in the codebase searched:
   - Request payload schema
   - Expected response format
   - Retry/failure semantics

3. **Rules Engine**: The database schema includes `rules`, `rule_input`, and `rule_input_source` tables (suggesting a policy/validation engine), but no code references were found:
   - `src/main/resources/db/mysql/schema.sql` (lines 162-177)
   - Purpose: Unknown (pre-deployment validation? Cost policies? Compliance checks?)

4. **Scaler Lock Tracker**: A `scaler_lock_tracker` table exists (schema line 112-120), implying auto-scaling functionality, but no related business logic was found.

### Files Searched (for Unknowns)

- **Configuration**: `application.conf`, `application-default.conf`
- **Protos**: All `.proto` files in `src/main/proto/dream11/od/`
- **Java Source**: All files under `src/main/java/com/dream11/odin/`
- **Database**: `schema.sql`, `migrations/*.sql`
- **Documentation**: `README.md` (current file is first comprehensive docs)

---

## Additional Hygiene Files Checklist

To make this repository production-ready and OSS-friendly, add:

- [ ] `SECURITY.md`: Security policy (vulnerability disclosure, supported versions)
- [ ] `CHANGELOG.md`: Auto-generated from Git tags and Conventional Commits
- [ ] `.github/ISSUE_TEMPLATE/bug_report.md`: Structured bug reports
- [ ] `.github/ISSUE_TEMPLATE/feature_request.md`: Structured feature proposals
- [ ] `.github/pull_request_template.md`: PR checklist (tests, docs, changelog)
- [ ] `.github/dependabot.yml`: Auto-update Maven/Docker dependencies
- [ ] `ADOPTERS.md`: List of companies/projects using Odin Deployer
- [ ] `docs/` directory: Architecture Decision Records (ADRs), API reference, tutorials
- [ ] `examples/` directory: Sample service definitions and provisioning configs
- [ ] OpenAPI/Swagger spec for REST API (generated from JAX-RS annotations)

---

## Quick Reference: Common Commands

```bash
# Development
mvn clean package                # Build JAR
mvn verify                       # Run all tests + integration tests
mvn fmt:format                   # Auto-format Java code
buf lint src/main/proto          # Lint protobuf files

# Database
liquibase update                 # Apply migrations
liquibase rollback --count=1     # Rollback last migration

# Docker
docker build -t odin-deployer:local .
docker run -p 8080:8080 odin-deployer:local

# Testing
mvn test -Dtest=ServiceBusinessTest  # Run specific test
mvn jacoco:report                    # Generate coverage report
```

---

**Thank you for using Odin Deployer!** For questions, open an [issue](https://github.com/dream11/odin-deployer/issues) or start a [discussion](https://github.com/dream11/odin-deployer/discussions).
