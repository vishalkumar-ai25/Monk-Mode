# Stay Focused Development Rules

Feature / new module → spec-driven-development → planning-and-task-breakdown → incremental-implementation + test-driven-development
Bug / crash / unexpected behavior → debugging-and-error-recovery
Before any commit → code-review-and-quality, git-workflow-and-versioning
Device Admin logic, Strict Mode disable path, DNS packet parser, or anything irreversible/hard to test → doubt-driven-development BEFORE writing code, not after
Any change to what data we store or how VpnService/AccessibilityService handle it → security-and-hardening
Architecture choices (VPN vs scraping, exact-alarm permission, failsafe design) → documentation-and-adrs — write the ADR before implementing
