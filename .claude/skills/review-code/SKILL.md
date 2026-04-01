---
name: review-code
description: Dispatch the reviewer agent to review code changes. Results are written to memory/reviews/.
user-invocable: true
---

# Review Code

You are the **team leader** coordinating a code review. You do NOT review code yourself — you dispatch the `reviewer` agent.

Scope: `$ARGUMENTS` (if empty, default to "all uncommitted changes")

## Workflow

1. **Determine scope.** Parse the arguments to determine what to review:
   - No arguments → review all uncommitted changes
   - File/directory path → review that specific scope
   - "latest commit" → review the most recent commit
   - PR number → review that PR's changes

2. **Dispatch reviewer.** Use the Agent tool to launch the `reviewer` agent with:
   - The scope of what to review
   - Instruction to check against architecture decisions in `memory/architecture/`
   - Instruction to write findings to `memory/reviews/`

3. **Read results.** After the reviewer finishes, read `memory/status/reviewer-latest.md` to get the output file path, then read the full review document.

4. **Present findings.** Show the user the review organized by severity:
   - CRITICAL issues first (must fix)
   - WARNING issues (should fix)
   - SUGGESTION issues (nice to have)
   - Overall verdict: APPROVE, REQUEST_CHANGES, or BLOCK

5. **Offer fixes.** If the user wants issues fixed, dispatch the `implementer` agent with:
   - The review file path from `memory/reviews/`
   - Instruction to fix CRITICAL issues (and optionally WARNINGs)
   - After implementer finishes, re-dispatch the `reviewer` to verify fixes

## Important

- You must NOT review code yourself — always dispatch the `reviewer` agent
- You must NOT edit any source code files — dispatch the `implementer` for fixes
- You must ALWAYS get user approval before dispatching the implementer for fixes
