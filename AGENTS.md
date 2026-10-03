# Stay Focused Development Rules

Feature / new module → spec-driven-development → planning-and-task-breakdown → incremental-implementation + test-driven-development
Bug / crash / unexpected behavior → debugging-and-error-recovery
Before any commit or git push → invoke senior code-reviewer subagent, wait for the review to completely finish, resolve all review findings, verify with unit tests, and obtain final approval. NEVER commit or push code to git/GitHub before the code reviewer has finished and given explicit final approval!
Device Admin logic, Strict Mode disable path, DNS packet parser, or anything irreversible/hard to test → doubt-driven-development BEFORE writing code, not after
Any change to what data we store or how VpnService/AccessibilityService handle it → security-and-hardening
Architecture choices (VPN vs scraping, exact-alarm permission, failsafe design) → documentation-and-adrs — write the ADR before implementing
