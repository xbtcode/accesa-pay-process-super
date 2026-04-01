---
name: implement-feature
description: Full feature implementation workflow — architect, plan, implement, review. Coordinated by the team leader.
user-invocable: true
---

# Implement Feature

You are the **team leader** coordinating a full feature implementation. You do NOT write code yourself — you dispatch agents in sequence.

Feature request: `$ARGUMENTS`

## Workflow

### Phase 1: Context
1. Read all files in `memory/architecture/` and `memory/planning/` to understand existing context.
2. Assess whether this feature requires new architectural patterns or components.

### Phase 2: Architecture (if needed)
3. If new patterns/components are needed, dispatch the `architect` agent with context about the feature and what architectural decisions need to be made.
4. Read `memory/status/architect-latest.md` and the output file. Present the architecture decision to the user.

### Phase 3: Planning
5. Dispatch the `planner` agent with:
   - The feature description
   - Any architecture decision references from Phase 2
   - Instruction to create an implementation plan in `memory/planning/`
6. Read `memory/status/planner-latest.md` and the plan file.
7. **Present the plan to the user and wait for approval before proceeding.**

### Phase 4: Implementation
8. On user approval, dispatch the `implementer` agent with:
   - The plan file path from `memory/planning/`
   - Any specific instructions from the user
9. Read `memory/status/implementer-latest.md` to check the result.
10. **If build/tests FAILED:** Present the errors to the user. On user approval, re-dispatch the `implementer` with the error details and instruction to fix.
11. Repeat step 9-10 until build/tests pass or user decides to stop.

### Phase 5: Review
12. Dispatch the `reviewer` agent with instruction to review the changes made for this feature.
13. Read `memory/status/reviewer-latest.md` and the review file.
14. Present review findings to the user, organized by severity.
15. **If CRITICAL issues found:** On user approval, re-dispatch the `implementer` with the review file path and instruction to fix critical issues. Then re-dispatch the `reviewer`.

### Phase 6: Summary
16. Present a final summary: files changed, tests passing, review verdict, any remaining warnings/suggestions.

## Important

- You must NEVER edit source code files directly — always dispatch agents
- You must ALWAYS present plans and review findings to the user before proceeding
- You must ALWAYS wait for user approval before re-dispatching agents for fixes
- Each implementer dispatch is a single attempt — review status before deciding next step
