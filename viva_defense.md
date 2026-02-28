# Viva Defense Script

This document contains concise, defensible answers to the most likely attack vectors during your viva, specifically focusing on the theoretical and architectural critiques of the system.

## Q2: Is 8 seconds a valid proxy for System 1 cognition?
**The Attack:** Kahneman's work doesn't define an 8-second threshold. Your inference logic (fast = System 1, slow = System 2) lacks psychometric validity and wasn't validated with an established instrument like the Cognitive Reflection Test.
**Your Defense:** "You are absolutely correct that the 8-second threshold is a heuristic rather than a clinically validated psychometric boundary. In the scope of this project, we are not attempting to publish a new psychological instrument. Instead, we are using the core concept of Dual Process Theory—fast/intuitive vs. slow/deliberate—as an *exploratory behavioral proxy* for user engagement. The threshold serves as a functional baseline to differentiate instantaneous recall from active problem-solving within the constraints of our platform. A true clinical validation against the Cognitive Reflection Test would require a formal IRB-approved study, which is the logical next step for future research, but falls outside the scope of this BTech proof-of-concept."

## Q4: Is behavioral telemetry a valid substitute for SM-2 self-report?
**The Attack:** SM-2 relies on self-reported recall quality (0-5). You've replaced it with an automated score based on time and errors without demonstrating convergent validity.
**Your Defense:** "The substitution of behavioral telemetry for self-reporting is actually the primary novel contribution of this system. We hypothesize that passive telemetry—specifically compilation time and error rates—can serve as a low-friction proxy for cognitive load. While we acknowledge that convergent validity against self-reported measures has not yet been formally established in this iteration, there is existing literature (e.g., Kornell & Bjork, 2008) supporting behavioral proxies for metacognition. Our system operationalizes this concept to remove the 'rating fatigue' inherent in traditional Anki-style systems, presenting a testable platform for future empirical studies."

## Q5: The arbitrary constants problem.
**The Attack:** Your scoring weights and hyperparameters (divisors, bonuses) are completely arbitrary. They lack calibration and make the scores scientifically meaningless for comparison.
**Your Defense:** "I concede that the current scoring constants are uncalibrated heuristics. The system was designed with a parameterizable architecture specifically so that these weights could be adjusted later. For this proof-of-concept, the relative differences in scores for a *single user over time* demonstrate the functional mechanics of the tracking engine. We fully acknowledge that comparing absolute scores between different students is currently invalid without first conducting a pilot study to empirically calibrate the scales against a normalized distribution. The architecture is ready for that calibration phase."

## Q10: The AdminController security hole.
**The Attack:** Your `/api/admin/export/research-data` endpoint was exposing all student data without authorization, violating GDPR/PDPA.
**Your Defense:** "This was a critical oversight during rapid prototyping that has since been fully resolved. We identified the issue and immediately hardened the endpoint. Method-level security (`@EnableMethodSecurity`) has been activated in our `SecurityConfig`, and the `@PreAuthorize("hasRole('ADMIN')")` annotation is now strictly enforced on the export endpoint. Data confidentiality is restored." *(Note: You can show them the fixed code if asked).*

## Q12: SM-2 updating on syntax errors is a bug.
**The Attack:** Your system penalizes the user's SM-2 learning curve (resetting repetitions) every time they hit 'Run' with a simple syntax error, artificially inflating forgetting velocity.
**Your Defense:** "Thank you for highlighting that; it was an unintended consequence of our initial event-tracking design. We have since corrected this logic. The `CompilerController` now explicitly filters compilation events, ensuring that mastery updates and SM-2 state degradation only occur upon *successful* compilations (or formal LeetCode submissions). Syntax errors are no longer weaponized against the user's learning curve." *(Note: You can show them the fixed `CompilerController` code).*

---
**General Strategy Note:** When attacked on methodology, always fall back to: *"This is a proof-of-concept systems engineering project, not a finalized clinical psychology study. We built the platform to enable that future research."*
