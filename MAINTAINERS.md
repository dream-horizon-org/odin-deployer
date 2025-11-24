# Maintainers Guide

This document provides guidance for Odin Deployer maintainers on managing the project, reviewing contributions, and releasing new versions.

## Current Maintainers

- **Akshay Patidar** ([@akshaypatidar1999](https://github.com/akshaypatidar1999)) - Lead Maintainer

## Maintainer Responsibilities

### Core Duties

1. **Code Review**: Review and merge pull requests in a timely manner (target: 3-5 business days)
2. **Issue Triage**: Label and prioritize issues, close duplicates and stale issues
3. **Release Management**: Create releases, manage changelogs, and ensure versioning
4. **Security**: Respond to security reports, coordinate patches and disclosures
5. **Community Support**: Answer questions, guide contributors, maintain positive community
6. **Documentation**: Keep docs up-to-date with code changes
7. **CI/CD**: Maintain build pipelines and automated workflows

### Time Commitment

- **Minimum**: 5-10 hours per week
- **Peak periods**: Release weeks, major feature development, security incidents

---

## Review Process

### Pull Request Review Checklist

#### Initial Screening (< 5 minutes)

- [ ] PR title follows Conventional Commits format
- [ ] Description is clear and complete
- [ ] Related issue(s) are linked
- [ ] CI checks pass (tests, formatting, Docker build)
- [ ] No merge conflicts

#### Code Review (15-30 minutes)

**Correctness**
- [ ] Code does what it claims to do
- [ ] Edge cases are handled
- [ ] Error handling is appropriate
- [ ] No obvious bugs

**Tests**
- [ ] New functionality has tests
- [ ] Tests cover edge cases
- [ ] Integration tests pass
- [ ] Code coverage remains above 70%

**Architecture**
- [ ] Follows existing patterns
- [ ] Uses appropriate abstractions
- [ ] No unnecessary complexity
- [ ] Respects separation of concerns

**Performance**
- [ ] No obvious performance issues
- [ ] Database queries are efficient (use indexes)
- [ ] Reactive patterns used correctly (no blocking calls)
- [ ] Resource cleanup (connections, file handles)

**Security**
- [ ] Input validation present
- [ ] No hardcoded secrets
- [ ] SQL injection prevented (parameterized queries)
- [ ] Authentication/authorization checked

**Style & Maintainability**
- [ ] Code follows Google Java Format
- [ ] Variables/methods are well-named
- [ ] Comments explain *why*, not *what*
- [ ] No excessive nesting (max 3-4 levels)

**Documentation**
- [ ] Public APIs have JavaDoc
- [ ] README updated (if needed)
- [ ] Proto files have comments
- [ ] CHANGELOG updated (if user-facing change)

**Database**
- [ ] Migrations are backward-compatible
- [ ] Rollback statements present
- [ ] Indexes added for new queries

**Breaking Changes**
- [ ] Justified and documented
- [ ] Migration guide provided
- [ ] Marked in PR title (`feat!:` or `fix!:`)

### Providing Feedback

**Be constructive and specific**:
- ✅ "Consider extracting this into a helper method to improve readability"
- ❌ "This code is messy"

**Use GitHub's review features**:
- **Comment**: Questions or suggestions
- **Approve**: Ready to merge (no significant issues)
- **Request Changes**: Must be addressed before merge

**Be timely**:
- First response within 3 business days
- Follow-ups within 2 business days
- Use "Review Later" label if you can't review immediately

---

## Release Process

### Versioning

We follow **Semantic Versioning** (semver):
- **MAJOR** (X.0.0): Breaking changes
- **MINOR** (0.X.0): New features (backward-compatible)
- **PATCH** (0.0.X): Bug fixes (backward-compatible)

**Pre-1.0 Exception**: During 0.x.x releases, minor versions may include breaking changes.

### Release Cadence

- **Minor releases**: Monthly (or when significant features accumulate)
- **Patch releases**: As needed (critical bugs, security fixes)
- **Major releases**: As needed (breaking changes, architecture shifts)

### Release Checklist

#### 1. Pre-Release (1-2 days before)

- [ ] Review all merged PRs since last release
- [ ] Ensure CI is green on `master` branch
- [ ] Check for open security issues (should be none)
- [ ] Update CHANGELOG.md:
  ```markdown
  ## [1.2.3] - 2024-11-25
  
  ### Added
  - New component health check API (#123)
  
  ### Fixed
  - JWT token expiration validation (#124)
  
  ### Changed
  - Upgraded Vert.x to 4.5.0 (#125)
  
  ### Deprecated
  - Old health check endpoint (will be removed in v2.0)
  
  ### Removed
  - Nothing
  
  ### Security
  - Fixed SQL injection vulnerability in component search (#126)
  ```
- [ ] Test release candidate:
  ```bash
  git checkout master
  git pull
  mvn clean verify
  ```

#### 2. Create Release (< 1 hour)

- [ ] Update version in `pom.xml`:
  ```bash
  mvn versions:set -DnewVersion=1.2.3
  git commit -am "chore: bump version to 1.2.3"
  git push origin master
  ```

- [ ] Create and push Git tag:
  ```bash
  git tag v1.2.3
  git push origin v1.2.3
  ```

- [ ] Create GitHub Release:
  1. Go to https://github.com/dream11/odin-deployer/releases/new
  2. Select tag: `v1.2.3`
  3. Title: `v1.2.3 - <Short Description>`
  4. Body: Copy from CHANGELOG.md
  5. **Uncheck "This is a pre-release"**
  6. Click "Publish release"

- [ ] Wait for release workflow to complete:
  - Tests pass
  - JAR uploaded to release assets
  - Docker image pushed (tags: `1.2.3`, `latest`)

#### 3. Post-Release

- [ ] Verify Docker image:
  ```bash
  docker pull <registry>/<repository>:1.2.3
  docker run <registry>/<repository>:1.2.3
  curl http://localhost:8080/healthcheck
  ```

- [ ] Bump to next development version:
  ```bash
  mvn versions:set -DnewVersion=1.2.4-SNAPSHOT
  git commit -am "chore: bump version to 1.2.4-SNAPSHOT"
  git push origin master
  ```

- [ ] Announce release:
  - GitHub Discussions post
  - Update README badges (if needed)
  - Notify users (if distribution list exists)

- [ ] Monitor issues for bug reports

#### 4. Hotfix Release (if needed)

For critical bugs in production:

1. Create hotfix branch:
   ```bash
   git checkout v1.2.3
   git checkout -b hotfix/1.2.4
   ```

2. Apply fix and test:
   ```bash
   # Make changes
   mvn clean verify
   ```

3. Update version and release:
   ```bash
   mvn versions:set -DnewVersion=1.2.4
   git commit -am "fix: critical bug in component lifecycle"
   git tag v1.2.4
   git push origin hotfix/1.2.4 v1.2.4
   ```

4. Merge back to master:
   ```bash
   git checkout master
   git merge hotfix/1.2.4
   git push origin master
   ```

---

## CI/CD Management

### GitHub Actions Workflows

| Workflow                      | Trigger              | Purpose                                  |
|-------------------------------|----------------------|------------------------------------------|
| `ci.yaml`                     | PR, push to master   | Run tests, build Docker image            |
| `release.yml`                 | GitHub Release       | Publish JAR and Docker image             |
| `google-java-format.yml`      | PR                   | Check Java code formatting               |
| `pr-title-checker.yml`        | PR                   | Validate PR title (Conventional Commits) |
| `buf.yaml`                    | Proto changes        | Lint protobuf files                      |

### Secrets Configuration

Required repository secrets (Settings → Secrets and variables → Actions):

- `ODIN_PUBLIC_KEY`: Base64-encoded RSA public key (for tests)
- `ODIN_PRIVATE_KEY`: Base64-encoded RSA private key (for tests)
- `DOCKER_USERNAME`: Docker registry username
- `DOCKER_PASSWORD`: Docker registry password/token
- `GH_BOT_TOKEN`: GitHub token with write access (for release workflow)

### Variables Configuration

Required repository variables:

- `DOCKER_REGISTRY`: Docker registry URL (e.g., `ghcr.io`)
- `DOCKER_REPOSITORY`: Docker repository name (e.g., `dream11/odin-deployer`)

### Monitoring CI Failures

- Check **Actions** tab for failed workflows
- Review logs for specific failures
- Re-run if flaky (but investigate root cause)
- Fix and push if genuine issue

---

## Issue Management

### Triage Process

**Labels to Apply**:

| Label              | When to Use                                      |
|--------------------|--------------------------------------------------|
| `bug`              | Something isn't working                          |
| `enhancement`      | New feature or request                           |
| `documentation`    | Improvements or additions to documentation       |
| `good first issue` | Easy for new contributors                        |
| `help wanted`      | Extra attention is needed                        |
| `question`         | Further information is requested                 |
| `wontfix`          | This will not be worked on                       |
| `duplicate`        | This issue already exists                        |
| `invalid`          | Doesn't seem right                               |
| `priority: high`   | Should be addressed urgently                     |
| `priority: low`    | Nice to have                                     |

**Milestones**:
- `v1.0.0`: Critical for first stable release
- `v1.1.0`: Next minor release
- `Backlog`: Future consideration

**Closing Issues**:
- Close duplicates with comment linking to original
- Close stale issues (no activity for 60+ days) with warning
- Close resolved issues when fix is released

---

## Security Response

### When a Vulnerability is Reported

1. **Acknowledge** within 48 hours
2. **Assess severity** (Critical, High, Medium, Low)
3. **Create private fix branch** (don't push to public repo yet)
4. **Develop and test fix**
5. **Coordinate disclosure** with reporter (90-day embargo for critical)
6. **Prepare advisory** (GitHub Security Advisory)
7. **Release patch** (hotfix release)
8. **Publish advisory** and notify users
9. **Credit reporter** in release notes (if desired)

### Security Issue Checklist

- [ ] Acknowledged reporter
- [ ] Assessed severity
- [ ] Created private fix
- [ ] Tested fix thoroughly
- [ ] Updated tests to prevent regression
- [ ] Coordinated disclosure timeline
- [ ] Created GitHub Security Advisory (draft)
- [ ] Released patch version
- [ ] Published advisory
- [ ] Notified users
- [ ] Credited reporter

---

## Branch Protection Rules

**master** branch should have:
- [ ] Require pull request reviews before merging (1 approval)
- [ ] Require status checks to pass before merging:
  - CI (tests)
  - google-java-format
  - pr-title-checker
- [ ] Require branches to be up to date before merging
- [ ] Require conversation resolution before merging
- [ ] Do not allow bypassing the above settings (even for admins)

**Setup**: Settings → Branches → Branch protection rules → Add rule

---

## Communication Guidelines

### Responding to Issues/PRs

- **Be welcoming**: Thank contributors for their time
- **Be patient**: Not everyone is familiar with the codebase
- **Be clear**: Explain decisions and provide context
- **Be respectful**: Disagree on ideas, not people

**Templates**:

**Welcoming first-time contributor**:
```markdown
Thank you for your contribution! 🎉 This is your first PR to Odin Deployer.

I'll review this soon. In the meantime, please ensure:
- [ ] CI checks pass
- [ ] You've read CONTRIBUTING.md

Feel free to ask questions if anything is unclear!
```

**Requesting changes**:
```markdown
Thanks for the PR! I've left a few comments. Once you address them, I'll give this another review.

No rush—take your time to make the changes. Let me know if you have questions!
```

**Merging PR**:
```markdown
Looks great! Thank you for your contribution. This will be included in the next release (v1.2.4).

🎉 Welcome to the Odin Deployer contributors list!
```

---

## Onboarding New Maintainers

When adding a new maintainer:

1. **Grant permissions**:
   - Add to GitHub team with "Maintain" role
   - Add to CODEOWNERS file
   - Grant access to CI secrets (if needed)

2. **Share knowledge**:
   - Walk through codebase architecture
   - Review this MAINTAINERS.md guide
   - Shadow a release cycle

3. **Update docs**:
   - Add to MAINTAINERS.md
   - Add to README contact section

4. **Announce**:
   - GitHub Discussions post
   - Update CODEOWNERS

---

## Stepping Down as Maintainer

If you need to step down:

1. **Notify** other maintainers (2+ weeks notice if possible)
2. **Transfer ownership** of ongoing work
3. **Remove permissions** (GitHub team, CODEOWNERS)
4. **Update docs** (MAINTAINERS.md, README)
5. **Announce** in GitHub Discussions

**Inactive maintainers**: If no activity for 6+ months, may be moved to "Emeritus Maintainers" list.

---

## Resources

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [GitHub Security Advisories](https://docs.github.com/en/code-security/security-advisories)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

---

## Questions?

If you have questions about maintainer duties, reach out to the lead maintainer or open a GitHub Discussion.

---

**Last Updated**: November 25, 2024

