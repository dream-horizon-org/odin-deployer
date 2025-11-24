# Security Policy

## Supported Versions

We release patches for security vulnerabilities in the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.0.x   | :white_check_mark: |

**Note**: As this project is in early development (pre-1.0), we recommend using the latest release.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

We take security seriously and appreciate your efforts to responsibly disclose your findings.

### How to Report

Send an email to **security@dream11.com** with:

1. **Subject Line**: `[SECURITY] Odin Deployer - <Brief Description>`
2. **Details**:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if you have one)
   - Your contact information

### What to Expect

- **Acknowledgment**: Within 48 hours of your report
- **Initial Assessment**: Within 5 business days
- **Status Updates**: Every 7 days until resolution
- **Disclosure Timeline**: We aim to patch critical vulnerabilities within 30 days

### Disclosure Policy

- **Coordinated Disclosure**: We request a 90-day embargo for critical vulnerabilities
- **Credit**: Security researchers who follow responsible disclosure will be credited in release notes
- **CVE Assignment**: We will request a CVE for confirmed vulnerabilities

## Security Best Practices

### For Deployment

1. **Use TLS/SSL**:
   - Enable TLS for gRPC in production
   - Use SSL for MySQL connections
   - Enable HTTPS for REST endpoints

2. **Secret Management**:
   - Never commit secrets to Git
   - Use Kubernetes Secrets, AWS Secrets Manager, or HashiCorp Vault
   - Rotate JWT keys every 90 days
   - Use strong passwords for database (min 16 characters)

3. **Network Security**:
   - Run Odin Deployer in a private network/VPC
   - Restrict MySQL access to application subnet only
   - Use VPC endpoints for SQS (avoid internet routing)
   - Enable firewall rules (allow only required ports)

4. **Database Security**:
   - Use dedicated MySQL user with least privilege (no `SUPER`, `FILE`, `PROCESS`)
   - Enable MySQL audit logging
   - Regular backups with encryption at rest

5. **Container Security**:
   - Run as non-root user (add `USER` directive to Dockerfile)
   - Scan images for vulnerabilities (`trivy`, `clair`)
   - Use minimal base images (already using `eclipse-temurin:*-jre-ubi9-minimal`)
   - Pin dependency versions

6. **Authentication**:
   - Enforce JWT token expiration (max 24 hours recommended)
   - Use RSA 2048-bit or higher for JWT signing
   - Validate `orgid` claim for multi-tenancy

### For Development

1. **Dependencies**:
   - Keep dependencies up-to-date
   - Review `dependabot` alerts
   - Audit new dependencies before adding

2. **Code Review**:
   - All code changes must be reviewed
   - Enable branch protection (require PR reviews)
   - Use CODEOWNERS for sensitive files

3. **Testing**:
   - Include security tests (e.g., SQL injection, XSS)
   - Test authentication/authorization flows
   - Validate input sanitization

## Known Security Considerations

### JWT Token Security

- **Algorithm**: RS256 (RSA with SHA-256)
- **Key Storage**: Private keys must be stored securely and never checked into version control
- **Rotation**: No automatic key rotation (manual process)

**Mitigation**: Implement a key rotation strategy:
1. Generate new key pair
2. Update `ODIN_PRIVATE_KEY` and `ODIN_PUBLIC_KEY` env vars
3. Restart application
4. Tokens issued with old key will be rejected

### Database Credentials

- **Current**: Credentials stored in environment variables
- **Risk**: If pod/container is compromised, credentials are exposed

**Mitigation**:
- Use Kubernetes Secrets with encryption at rest
- Consider using AWS IAM authentication for RDS
- Implement secret rotation (AWS Secrets Manager, Vault)

### SQS Message Security

- **Current**: Messages are not encrypted by default
- **Risk**: Sensitive data in provisioning configs may be visible

**Mitigation**:
- Enable SQS Server-Side Encryption (SSE-SQS or SSE-KMS)
- Use VPC endpoints (messages never leave AWS network)
- Sanitize logs (don't log full messages)

### gRPC Security

- **Current**: gRPC runs on HTTP/2 without TLS by default (local dev)
- **Risk**: Man-in-the-middle attacks in production

**Mitigation**:
- Always use TLS in production (configure `HttpServerOptions.setSsl(true)`)
- Use mutual TLS (mTLS) for service-to-service communication
- Validate client certificates

## Security Advisories

Security advisories will be published in:
- **GitHub Security Advisories**: [Link to advisories when available]
- **Release Notes**: Detailed fixes in changelogs
- **Email Notification**: To known users (if opt-in list is created)

## Security Updates

Subscribe to security notifications:
1. **Watch this repository** on GitHub
2. Enable **Security alerts** in your notification settings
3. Monitor the [Releases](https://github.com/dream11/odin-deployer/releases) page

## Bug Bounty Program

We currently **do not** have a formal bug bounty program, but we deeply appreciate responsible disclosure and will:
- Publicly credit researchers (unless anonymity is requested)
- Prioritize reported issues
- Provide swag/merchandise for significant finds (on a case-by-case basis)

## Security Contact

- **Email**: security@dream11.com
- **Maintainer**: [@akshaypatidar1999](https://github.com/akshaypatidar1999)

---

**Last Updated**: November 25, 2024

