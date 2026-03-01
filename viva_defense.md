# Viva Defense Script

This document contains concise, defensible answers to the most likely attack vectors during your viva, specifically focusing on the theoretical and architectural critiques of the system.

## Q2: Is 8 seconds a valid proxy for System 1 cognition?
**The Attack:** Kahneman's work doesn't define an 8-second threshold. Your inference logic (fast = System 1, slow = System 2) lacks psychometric validity.
**Your Defense:** "We use the 8-second threshold as an *exploratory behavioral proxy* for user engagement rather than a clinical psychometric boundary. It serves as a functional baseline to differentiate instantaneous recall from active problem-solving. While a true clinical study is the logical next step, this heuristic demonstrates the functional mechanics of our tracking engine within this systems engineering project."

## Q4: Is behavioral telemetry a valid substitute for SM-2 self-report?
**Your Defense:** "The primary novel contribution of this system is the hypothesis that passive telemetry—specifically compilation time and error rates—can serve as a low-friction proxy for cognitive load. While convergent validity against self-reported measures has not yet been formally established, existing literature (e.g., Kornell & Bjork, 2008) supports the use of behavioral proxies for metacognition. Our system operationalizes this to remove 'rating fatigue', presenting a testable platform for future empirical research."

## Q10: The AdminController security hole.
**Your Defense:** "This was a critical oversight identified during prototyping that has since been fully resolved. We hardened the endpoint by activating method-level security (`@EnableMethodSecurity`) and enforcing `@PreAuthorize("hasRole('ADMIN')")` on the export endpoint. Data confidentiality is now strictly maintained."

## Q12: SM-2 updating on syntax errors is a bug.
**Your Defense:** "We corrected this unintended consequence. The `CompilerController` now explicitly filters compilation events, ensuring that mastery updates and SM-2 state degradation only occur upon *successful* compilations or formal LeetCode submissions. Syntax errors are no longer penalized."

## New: How do you prevent users from hacking your server via the compiler? (RCE/DoS)
**Your Defense:** "We have implemented multiple security layers for code execution. First, a **Security Blacklist** scans the source code for malicious keywords like `Runtime.getRuntime` or `os.system` before execution. Second, we enforce strict **Execution Timeouts** and **Memory Quotas** (e.g., `-Xmx128m`) to prevent infinite loops or memory-heavy scripts from causing a Denial of Service. In a production environment, we would further isolate these processes inside Docker containers."

## New: How do you prevent brute-force login attacks?
**Your Defense:** "We've integrated a `LoginAttemptService` that tracks failed attempts by IP address. If an attacker attempts to guess a password 5 times unsuccessfully, the system automatically triggers a **15-minute lockout** for that IP, effectively neutralizing automated credential stuffing."

## New: How do you prevent IDOR (Insecure Direct Object Reference)?
**Your Defense:** "We ensure that every request is checked against the authenticated user's profile. Even if an attacker manually changes a URL ID, the backend's `isOwner()` logic verifies that the `Principal` matches the resource being requested, returning a `403 Forbidden` for unauthorized access."
