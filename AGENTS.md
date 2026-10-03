# Stay Focused Development Rules

Feature / new module → spec-driven-development → planning-and-task-breakdown → incremental-implementation + test-driven-development
Bug / crash / unexpected behavior → debugging-and-error-recovery
Before any commit or git push → invoke senior code-reviewer subagent, wait for the review to completely finish, resolve all review findings, verify with unit tests, and obtain final approval. NEVER commit or push code to git/GitHub before the code reviewer has finished and given explicit final approval!
Device Admin logic, Strict Mode disable path, DNS packet parser, or anything irreversible/hard to test → doubt-driven-development BEFORE writing code, not after (invoke the 'adversarial-reviewer' subagent with isolated ARTIFACT + CONTRACT; resolve all findings before proceeding)
Any change to what data we store or how VpnService/AccessibilityService handle it → security-and-hardening
Architecture choices (VPN vs scraping, exact-alarm permission, failsafe design) → documentation-and-adrs — write the ADR before implementing

## Subagents & Review Roster
- `code-reviewer`: Senior code reviewer for post-implementation multi-axis review (correctness, readability, architecture, security, performance) before git commit/push.
- `adversarial-reviewer`: Adversarial supervisor subagent following doubt-driven-development to stress-test architectural plans, invariant claims, high-blast-radius decisions, and critical tool actions with an issues-only, disprove-biased mandate.
