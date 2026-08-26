# Security Policy

## Supported versions

BottleVault is deployed continuously from `main`. There are no maintained
release branches or long-term-support versions. Only the most recently
published `:latest` image is supported. If you self-host from an older
commit or image tag, update before reporting an issue that may already be
fixed.

## Reporting a vulnerability

Please report security vulnerabilities privately using GitHub's
[private vulnerability reporting](https://github.com/toastedcoffee/BottleVault/security/advisories/new)
(Security tab → Report a vulnerability) rather than a public issue.

This is a solo-maintained project, not a project with a security team or an
SLA. I'll do my best to acknowledge reports within a week and to keep you
updated as I work on a fix. Please allow time to investigate and patch
before any public disclosure.

## Scope

In scope: vulnerabilities in the application code, the Docker images, or the
provided `docker-compose` configuration.

Out of scope: vulnerabilities arising from how you've configured your own
deployment (reverse proxy, TLS termination, Cloudflare Access or equivalent,
host firewall, etc.). Those are yours to secure, not this project's.
