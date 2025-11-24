# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

---

## [0.0.3] - 2024-XX-XX

*Note: This is the current version in development. Below is a template for future changelog entries.*

### Added
- Initial open-source release
- gRPC APIs for service deployment, environment management, and component operations
- REST APIs for component operations and health checks
- JWT-based authentication
- MySQL persistence layer with Liquibase migrations
- SQS integration for asynchronous task processing
- Docker multi-arch images (amd64, arm64)
- Comprehensive test suite (unit + integration tests with Testcontainers)

### Changed
- N/A (initial release)

### Fixed
- N/A (initial release)

### Security
- JWT token validation with RSA256
- Database connection pooling with configurable timeouts
- Input validation for gRPC and REST endpoints

---

## How to Update This File

When creating a new release:

1. Move items from `[Unreleased]` to a new version section
2. Add release date in format `YYYY-MM-DD`
3. Create link at bottom of file
4. Keep categories even if empty (shows intent)

### Category Descriptions

- **Added**: New features
- **Changed**: Changes to existing functionality
- **Deprecated**: Soon-to-be removed features (still work)
- **Removed**: Now removed features
- **Fixed**: Bug fixes
- **Security**: Vulnerability fixes

### Example Entry

```markdown
## [1.2.3] - 2024-11-25

### Added
- Component health check endpoints (#123)
- Support for multi-region deployments (#124)

### Changed
- Upgraded Vert.x to 4.5.0 (#125)
- Improved error messages for validation failures (#126)

### Fixed
- JWT token expiration not properly validated (#127)
- Deadlock in service task creation under high load (#128)

### Security
- Fixed SQL injection vulnerability in component search (#129)
```

---

<!-- Version comparison links -->
[unreleased]: https://github.com/dream11/odin-deployer/compare/v0.0.3...HEAD
[0.0.3]: https://github.com/dream11/odin-deployer/releases/tag/v0.0.3

