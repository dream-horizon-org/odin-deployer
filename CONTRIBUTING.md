# Contributing to Odin Deployer

Thank you for your interest in contributing to Odin Deployer! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Environment Setup](#development-environment-setup)
- [Branching Strategy](#branching-strategy)
- [Commit Message Convention](#commit-message-convention)
- [Making Changes](#making-changes)
- [Testing](#testing)
- [Submitting Pull Requests](#submitting-pull-requests)
- [Code Review Process](#code-review-process)
- [Style Guide](#style-guide)
- [Documentation](#documentation)
- [Community](#community)

---

## Code of Conduct

This project adheres to a Code of Conduct that all contributors are expected to follow. Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before contributing.

---

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/<your-username>/odin-deployer.git
   cd odin-deployer
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/dream11/odin-deployer.git
   ```
4. **Create a feature branch**:
   ```bash
   git checkout -b feature/my-awesome-feature
   ```

---

## Development Environment Setup

### Prerequisites

- **Java 17+** (OpenJDK Temurin recommended)
- **Maven 3.8+**
- **Docker** (for running MySQL and LocalStack)
- **Git**
- **IDE** with Lombok plugin (IntelliJ IDEA, Eclipse, VS Code)

### Step-by-Step Setup

1. **Start local dependencies**:
   ```bash
   # MySQL
   docker run --name odin-mysql \
     -e MYSQL_ROOT_PASSWORD=root \
     -e MYSQL_DATABASE=odin_deployer \
     -p 3306:3306 -d mysql:8.2

   # LocalStack (for SQS)
   docker run --name odin-localstack \
     -p 4566:4566 -d localstack/localstack:latest

   # Create SQS queues
   aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name odin-requests
   aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name odin-responses
   ```

2. **Run database migrations**:
   ```bash
   mvn liquibase:update \
     -Dliquibase.url=jdbc:mysql://localhost:3306/odin_deployer \
     -Dliquibase.username=root \
     -Dliquibase.password=root
   ```

3. **Set environment variables**:
   ```bash
   export ODIN_MYSQL_MASTER_HOST=localhost
   export ODIN_MYSQL_SLAVE_HOST=localhost
   export ODIN_MYSQL_USERNAME=root
   export ODIN_MYSQL_PASSWORD=root
   
   # Generate JWT keys (for testing)
   openssl genrsa -out private.pem 2048
   openssl rsa -in private.pem -pubout -out public.pem
   export ODIN_PUBLIC_KEY=$(cat public.pem | base64)
   export ODIN_PRIVATE_KEY=$(cat private.pem | base64)
   
   # SQS config
   export ODIN_QUEUE_REQUEST_ENDPOINT=http://localhost:4566
   export ODIN_QUEUE_REQUEST_URL=http://localhost:4566/000000000000/odin-requests
   export ODIN_QUEUE_RESPONSE_ENDPOINT=http://localhost:4566
   export ODIN_QUEUE_RESPONSE_URL=http://localhost:4566/000000000000/odin-responses
   ```

4. **Build the project**:
   ```bash
   mvn clean package
   ```

5. **Run the application**:
   ```bash
   java -jar target/odin-deployer/odin-deployer-fat.jar
   ```

6. **Verify it's running**:
   ```bash
   curl http://localhost:8080/healthcheck
   # Expected: {"status":"RUNNING"}
   ```

### IDE Configuration

#### IntelliJ IDEA

1. Install **Lombok plugin**: `File → Settings → Plugins → Search "Lombok" → Install`
2. Enable annotation processing: `File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing`
3. Import as Maven project
4. Set JDK to 17: `File → Project Structure → Project SDK`

#### VS Code

1. Install extensions:
   - Language Support for Java (Red Hat)
   - Debugger for Java
   - Maven for Java
   - Lombok Annotations Support
2. Open project folder
3. Maven will auto-import dependencies

---

## Branching Strategy

We follow a **Git Flow** inspired model:

- `master`: Production-ready code
- `feature/*`: New features (e.g., `feature/add-rollback-support`)
- `bugfix/*`: Bug fixes (e.g., `bugfix/fix-component-deletion`)
- `chore/*`: Maintenance tasks (e.g., `chore/update-dependencies`)
- `docs/*`: Documentation updates

**Branch Naming Rules**:
- Use lowercase with hyphens
- Start with category prefix
- Be descriptive but concise

**Examples**:
```bash
git checkout -b feature/kubernetes-health-checks
git checkout -b bugfix/jwt-expiration-validation
git checkout -b chore/upgrade-vertx-4.5
git checkout -b docs/add-deployment-guide
```

---

## Commit Message Convention

We use **[Conventional Commits](https://www.conventionalcommits.org/)** for automated changelog generation and semantic versioning.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style/formatting (no logic change)
- `refactor`: Code restructuring (no behavior change)
- `perf`: Performance improvements
- `test`: Add or update tests
- `chore`: Build tools, CI, dependencies
- `revert`: Revert a previous commit

### Examples

```bash
# Feature
git commit -m "feat(service): add support for component rollback"

# Bug fix
git commit -m "fix(dao): prevent duplicate component task creation"

# Documentation
git commit -m "docs(readme): add JWT token generation example"

# Breaking change
git commit -m "feat(api)!: change gRPC service version to v2

BREAKING CHANGE: ServiceService.v1 is deprecated. Clients must migrate to ServiceService.v2."
```

### Best Practices

- **Subject line**: Max 72 characters, imperative mood ("add" not "added")
- **Body**: Wrap at 72 characters, explain *what* and *why* (not *how*)
- **Footer**: Reference issues (`Fixes #123`, `Closes #456`)
- **Scope**: Use component names (`service`, `dao`, `grpc`, `rest`, `ci`)

---

## Making Changes

### Code Guidelines

1. **Write tests** for all new functionality
   - Unit tests for business logic
   - Integration tests for database/queue interactions
   - Aim for >70% code coverage

2. **Follow Java best practices**:
   - Use `Optional` instead of null returns
   - Prefer immutability (use Lombok `@Value` or `@Builder`)
   - Handle exceptions appropriately (don't swallow errors)
   - Add JavaDoc for public APIs

3. **Keep methods small**: Aim for <50 lines per method

4. **Avoid code duplication**: Extract common logic into utilities

5. **Use Reactive patterns**: Prefer `Single`, `Completable`, `Flowable` from RxJava

### Protocol Buffers

When modifying `.proto` files:

1. **Never change field numbers** (breaks compatibility)
2. **Use `optional` or `repeated`** for new fields (allows backward compatibility)
3. **Run Buf lint**:
   ```bash
   buf lint src/main/proto
   ```
4. **Check for breaking changes**:
   ```bash
   buf breaking --against '.git#branch=master'
   ```

### Database Migrations

**Never** edit existing migration files. Create a new migration:

```bash
# Create new migration file
touch src/main/resources/db/mysql/migrations/$(date +%Y%m%d%H%M%S)_DescribeChange.sql
```

**Migration Template**:
```sql
--liquibase formatted sql

--changeset your-username:20241125120000-add-component-metadata
ALTER TABLE component ADD COLUMN metadata JSON DEFAULT NULL;

--rollback ALTER TABLE component DROP COLUMN metadata;
```

---

## Testing

### Running Tests

```bash
# Unit tests only
mvn test

# All tests (includes integration tests)
mvn verify

# Specific test class
mvn test -Dtest=ServiceBusinessTest

# Specific test method
mvn test -Dtest=ServiceBusinessTest#testDeployService

# With debug logging
mvn test -Dlogback.configurationFile=src/test/resources/logback-test.xml
```

### Writing Tests

#### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class ServiceBusinessTest {
  
  @Mock
  private ServiceTaskDao serviceTaskDao;
  
  @InjectMocks
  private ServiceBusiness serviceBusiness;
  
  @Test
  void testDeployService_Success() {
    // Arrange
    DeployServiceRequest request = DeployServiceRequest.newBuilder()
      .setEnvName("test-env")
      .build();
    when(serviceTaskDao.createServiceTask(any())).thenReturn(Single.just(1L));
    
    // Act
    TestObserver<DeployServiceResponse> observer = serviceBusiness
      .deployService(Single.just(request))
      .test();
    
    // Assert
    observer.assertComplete();
    observer.assertValueCount(1);
  }
}
```

#### Integration Test Example

```java
@ExtendWith(VertxExtension.class)
class ServiceServiceIT extends AbstractTestSetup {
  
  @Test
  void testDeployServiceEndToEnd(Vertx vertx, VertxTestContext testContext) {
    // Setup test data
    createEnvironment("test-env");
    
    // Call gRPC endpoint
    ServiceServiceGrpc.ServiceServiceStub stub = getServiceStub();
    DeployServiceRequest request = buildDeployRequest();
    
    stub.deployService(request, new StreamObserver<>() {
      @Override
      public void onNext(DeployServiceResponse response) {
        testContext.verify(() -> {
          assertThat(response.getServiceResponse().getServiceStatus()
            .getServiceStatus()).isEqualTo("DEPLOYING");
        });
      }
      
      @Override
      public void onCompleted() {
        testContext.completeNow();
      }
      
      @Override
      public void onError(Throwable t) {
        testContext.failNow(t);
      }
    });
  }
}
```

### Code Coverage

After running tests, view coverage report:

```bash
mvn jacoco:report
open target/site/jacoco/index.html
```

---

## Submitting Pull Requests

### Before Submitting

1. **Sync with upstream**:
   ```bash
   git fetch upstream
   git rebase upstream/master
   ```

2. **Run full test suite**:
   ```bash
   mvn clean verify
   ```

3. **Format code**:
   ```bash
   mvn fmt:format
   ```

4. **Lint protos** (if modified):
   ```bash
   buf lint src/main/proto
   ```

5. **Update documentation** if needed (README, JavaDoc)

### Creating the PR

1. **Push to your fork**:
   ```bash
   git push origin feature/my-awesome-feature
   ```

2. **Open PR** on GitHub against `master` branch

3. **Fill out PR template**:
   - Description of changes
   - Related issue(s)
   - Testing performed
   - Screenshots (if UI changes)
   - Checklist items

4. **PR Title**: Use conventional commit format
   ```
   feat(service): add component health checks
   ```

### PR Checklist

- [ ] Code follows project style (Google Java Format)
- [ ] Tests added/updated for changes
- [ ] All tests pass (`mvn verify`)
- [ ] Documentation updated (if needed)
- [ ] Commit messages follow Conventional Commits
- [ ] No merge conflicts with `master`
- [ ] PR description is clear and complete

---

## Code Review Process

### What to Expect

1. **Automated Checks**: CI runs tests, formatting checks, and builds Docker image
2. **Maintainer Review**: A maintainer will review within 3-5 business days
3. **Feedback**: Address review comments by pushing new commits
4. **Approval**: Requires 1 approval from a maintainer (see [CODEOWNERS](.github/CODEOWNERS))
5. **Merge**: Maintainer will merge via "Squash and Merge" (preserves commit message)

### Review Criteria

- **Correctness**: Does it work as intended?
- **Tests**: Are edge cases covered?
- **Performance**: Any obvious bottlenecks?
- **Security**: No credentials, proper validation?
- **Maintainability**: Is code readable and well-structured?
- **Compatibility**: No breaking changes (unless justified)?

### Responding to Feedback

- Address comments promptly
- Push fixup commits (will be squashed on merge)
- Ask questions if feedback is unclear
- Mark conversations as "Resolved" once addressed

---

## Style Guide

### Java

We use **Google Java Format**:

```bash
# Check formatting
mvn fmt:check

# Auto-format
mvn fmt:format
```

### Additional Conventions

1. **Naming**:
   - Classes: `PascalCase` (e.g., `ServiceBusiness`)
   - Methods: `camelCase` (e.g., `deployService`)
   - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`)
   - Packages: `lowercase` (e.g., `com.dream11.odin.service`)

2. **Lombok**:
   - Use `@Slf4j` for logging
   - Use `@Value` for immutable DTOs
   - Use `@Builder` for complex constructors
   - Use `@RequiredArgsConstructor(onConstructor = @__({@Inject}))` for DI

3. **Logging**:
   ```java
   log.debug("Processing request: {}", request);  // Use placeholders
   log.error("Failed to connect to DB", exception); // Include stack trace
   ```

4. **RxJava**:
   - Always handle errors in reactive chains (`.onErrorResumeNext()`)
   - Use `.subscribeOn()` and `.observeOn()` appropriately
   - Avoid blocking calls (use `rxMethod()` variants)

### Protobuf

- Use `snake_case` for field names
- Add comments for all messages and fields
- Group related messages in same file

### SQL

- Use `UPPER CASE` for keywords
- Use `snake_case` for table/column names
- Always include `--rollback` statements in migrations

---

## Documentation

### When to Update Docs

- New features: Add section to README
- API changes: Update proto comments and generate docs
- Configuration changes: Update Configuration Reference in README
- Breaking changes: Add migration guide

### Documentation Standards

1. **README**: Keep examples up-to-date
2. **JavaDoc**: All public methods and classes
3. **Proto comments**: Explain field meanings and constraints
4. **Inline comments**: Only for complex logic (code should be self-documenting)

### Generating API Docs

```bash
# JavaDoc
mvn javadoc:javadoc
open target/site/apidocs/index.html

# Proto docs (if buf gen configured)
buf generate
```

---

## Community

### Getting Help

- **GitHub Issues**: For bugs and feature requests
- **GitHub Discussions**: For questions and general discussion
- **Code Comments**: Tag maintainers (`@akshaypatidar1999`) for reviews

### Reporting Bugs

Use the **Bug Report** issue template and include:

- Odin Deployer version
- Java version
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs/stack traces

### Proposing Features

Use the **Feature Request** issue template and include:

- Use case / problem statement
- Proposed solution
- Alternatives considered
- Willingness to implement (PR)

---

## Release Process

*(For maintainers)*

1. Merge all PRs for the release
2. Update version in `pom.xml` (remove `-SNAPSHOT`)
3. Create Git tag: `git tag v1.2.3`
4. Push tag: `git push origin v1.2.3`
5. Create GitHub Release from tag (triggers release workflow)
6. CI builds and publishes artifacts
7. Bump version in `pom.xml` (e.g., `1.2.4-SNAPSHOT`)

---

## Thank You!

Your contributions make Odin Deployer better for everyone. If you have questions about contributing, feel free to open a discussion or reach out to the maintainers.

Happy coding! 🚀

