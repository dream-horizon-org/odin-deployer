## Description

<!-- Provide a clear and concise description of your changes -->

## Related Issue(s)

<!-- Link to related issues using keywords: Fixes #123, Closes #456, Relates to #789 -->

Fixes #

## Type of Change

<!-- Mark relevant options with an 'x' -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Code refactoring
- [ ] Test addition/improvement
- [ ] CI/CD changes
- [ ] Dependency update

## Changes Made

<!-- List the key changes in bullet points -->

- 
- 
- 

## Testing Performed

<!-- Describe the tests you ran and their results -->

### Unit Tests

```bash
# Commands used to run tests
mvn test -Dtest=MyNewTest
```

**Results**: [All passed / X failures - explain]

### Integration Tests

```bash
mvn verify
```

**Results**: [All passed / X failures - explain]

### Manual Testing

<!-- Describe manual testing steps if applicable -->

1. Step 1
2. Step 2
3. Expected result: ...
4. Actual result: ...

## Screenshots / Logs (if applicable)

<!-- Add screenshots or relevant log outputs -->

<details>
<summary>Logs</summary>

```
Paste logs here
```

</details>

## Breaking Changes

<!-- If this is a breaking change, describe the impact and migration path -->

**Impact**: [What breaks and for whom]

**Migration Guide**:
1. Step to migrate
2. Step to migrate

## Checklist

<!-- Mark completed items with an 'x' -->

### Code Quality

- [ ] My code follows the project's style guidelines (Google Java Format)
- [ ] I have run `mvn fmt:format` to format my code
- [ ] I have performed a self-review of my code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have removed any debug/console log statements

### Testing

- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes (`mvn test`)
- [ ] New and existing integration tests pass locally (`mvn verify`)
- [ ] Code coverage remains above 70% (check `target/site/jacoco/index.html`)

### Documentation

- [ ] I have updated the README.md (if needed)
- [ ] I have updated JavaDoc comments for public APIs
- [ ] I have updated proto comments (if proto files changed)
- [ ] I have updated CONTRIBUTING.md (if development process changed)

### Database

- [ ] I have created Liquibase migration files (if schema changed)
- [ ] Migration includes rollback statements
- [ ] I have tested migration on local database

### Protobuf

- [ ] I have run `buf lint src/main/proto` and fixed issues
- [ ] I have run `buf breaking --against '.git#branch=master'` to check for breaking changes
- [ ] I have documented any breaking changes in the PR description

### Commits

- [ ] My commits follow the Conventional Commits specification
- [ ] My branch is up-to-date with the base branch (rebased if needed)
- [ ] I have resolved all merge conflicts

## Additional Notes

<!-- Any additional information for reviewers -->

## Reviewer Checklist (for maintainers)

- [ ] Code follows project conventions
- [ ] Tests are comprehensive
- [ ] Documentation is adequate
- [ ] No security issues introduced
- [ ] Performance is acceptable
- [ ] Breaking changes are justified and documented

