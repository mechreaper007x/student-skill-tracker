# Security Audit: OWASP Defense & Advanced Hardening

This document summarizes the high-grade security fortifications implemented to protect the Student Skill Tracker against advanced adversarial attacks.

## 1. Advanced Hashing (Argon2id)
- **Mechanism**: Upgraded password hashing from BCrypt to **Argon2id** (via BouncyCastle).
- **Benefit**: Superior defense against GPU/ASIC-based brute-force attacks.

## 2. 7-Layer Prompt Injection Defense (Rishi AI)
| Layer | Defense Mechanism | Benefit |
| :--- | :--- | :--- |
| **1. Persona Anchoring** | Hardcoded System Prompt | Fixes identity as "Rishi". |
| **2. Security Directive** | OWASP Guardrail | Rejects persona overrides. |
| **3. Structural Isolation** | XML Delimiting | Wraps input in `<user_input>` tags. |
| **4. Context Delimiting** | Logical Separation | Prevents command escaping. |
| **5. Encoding Defense** | Data Treatment | Processes encoded text as raw data. |
| **6. Hypothetical Guard** | Bypass Rejection | Blocks social engineering tricks. |
| **7. Adversarial Hardening** | Exploit Neutralization | Blocks payload injections. |

## 3. Remote Code Execution (RCE) Defense
- **Mechanism**: Implemented a **Security Blacklist** in `SecurityUtils.java`.
- **Enforcement**: All compilers (Java, Python, C++, JS) now scan source code for dangerous keywords like `Runtime.getRuntime`, `os.system`, `ProcessBuilder`, and `reflect`.
- **Benefit**: Prevents attackers from executing system-level commands through the compiler.

## 4. Logic-Based DoS (Denial of Service) Prevention
- **Mechanism**: Enforced strict **Execution Timeouts** using `Process.waitFor(timeout, unit)`.
- **Resource Limits**: Added `-Xmx128m` to Java execution to prevent memory exhaustion.
- **Benefit**: Prevents infinite loops or memory-heavy scripts from crashing the server.

## 5. Insecure Direct Object Reference (IDOR) Protection
- **Mechanism**: Added `@PreAuthorize` and ownership checks (`isOwner()`) in `StudentController.java`.
- **Benefit**: Ensures students can only view or edit their own dashboards and data.

## 6. Brute-Force & Credential Stuffing Defense
- **Mechanism**: Implemented `LoginAttemptService` to track failed logins by IP address.
- **Lockout**: Automatically blocks an IP for 15 minutes after 5 consecutive failed attempts.
- **Benefit**: Neutralizes automated password cracking scripts.

## 7. JWT Multi-Fingerprinting (Stateless)
- **Mechanism**: Embedded `fgp` (HttpOnly Cookie Hash) and `uah` (User-Agent Hash) into the JWT.
- **Benefit**: Stolen tokens are mathematically useless without the original browser fingerprint.

## 8. Dependency Vulnerability Mitigation
- **Update**: Upgraded `pom.xml` dependencies (Spring Boot 3.4.3, BCProv 1.80) to patch known CVEs.

---
**Verification Status**: All safety layers were empirically verified via automated tests and manual audit.
