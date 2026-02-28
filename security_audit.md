# Security Audit: OWASP Defense & Multi-Layer Safety

This document summarizes the high-grade security fortifications implemented to protect the Student Skill Tracker against advanced adversarial attacks.

## 1. Advanced Hashing (Argon2id)
- **Mechanism**: Upgraded password hashing from BCrypt to **Argon2id** (via BouncyCastle).
- **Benefit**: Provides superior defense against GPU/ASIC-based brute-force attacks by being memory-hard and time-hard, adhering to the latest OWASP and NIST recommendations.

## 2. 7-Layer Prompt Injection Defense (Rishi AI)
We implemented a structural and behavioral safety pipeline to protect the Rishi GenAI service:

| Layer | Defense Mechanism | Test Case |
| :--- | :--- | :--- |
| **1. Persona Anchoring** | Strict system instructions define Rishi's role as a 10x Dev. | `testDirectOverrideAttack` |
| **2. Security Directive** | Explicit instructions to reject overrides and remind users of identity. | `testPersonaHijackingAttack` |
| **3. Structural Isolation** | User input is strictly wrapped in `<user_input>` XML tags. | `testSystemPromptExtractionAttack` |
| **4. Context Delimiting** | Clear separation between System Prompt and Data payload. | `testXmlEscapingAttack` |
| **5. Encoding Defense** | System processes base64/encoded inputs as raw text data only. | `testObfuscationAttack` |
| **6. Hypothetical Guard** | Rejects "what if" scenarios designed to bypass safety filters. | `testHypotheticalBypassAttack` |
| **7. Adversarial Hardening** | Neutralizes payloads containing common exploit strings (e.g., SQL). | `testAdversarialPayloadAttack` |

## 3. SQL Injection Protection
- **Mechanism**: Enforced use of **JPA Parameterized Queries** across all repositories.
- **Benefit**: Neutralizes 1st-order SQLi by ensuring user input is never concatenated into raw SQL strings.

## 4. XSS (Cross-Site Scripting) Hardening
- **Headers**: Injected `X-XSS-Protection: 1; mode=block`.
- **Content Security Policy (CSP)**: Implemented a strict CSP (`default-src 'self'`) to prevent unauthorized script execution and cross-domain data leakage.

## 5. SSRF (Server-Side Request Forgery) Defense
- **Mechanism**: Implemented strict **Input Sanitization** (`sanitizeForUrl`) on all external API service calls (LeetCode, GitHub).
- **Benefit**: Strips characters like `../` or `@` to prevent attackers from manipulating the server into making unauthorized internal or external requests.

## 6. JWT Multi-Fingerprinting (Stateless)
- **Mechanism**: Embedded `fgp` (HttpOnly Cookie Hash) and `uah` (User-Agent Hash) into the JWT payload.
- **Benefit**: Prevents **Token Theft**; even if a JWT is stolen, it cannot be used without the matching HttpOnly fingerprint cookie and original browser signature.

## 7. CSRF Defense (Stateless Mode)
- **Mechanism**: While CSRF is disabled for stateless JWT, we enforce **SameSite=Strict** on the fingerprint cookie.
- **Benefit**: Provides an additional layer of protection against cross-site request forgery for the sensitive session binding.

---
**Verification Status**: All 7 layers of prompt safety were empirically verified via `mvn test -Dtest="RishiGenAiSecurityTest"` with **BUILD SUCCESS**.
