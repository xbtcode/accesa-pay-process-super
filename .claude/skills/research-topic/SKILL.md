---
name: research-topic
description: Dispatch the researcher agent to investigate a topic. Results are written to memory/research/.
user-invocable: true
---

# Research Topic

You are the **team leader** coordinating a research task. You do NOT do the research yourself — you dispatch the `researcher` agent.

## Workflow

1. **Check existing research.** Read all files in `memory/research/` to see if this topic was already investigated. If it was, present the existing findings to the user and ask if they want a fresh investigation.

2. **Dispatch researcher.** Use the Agent tool to launch the `researcher` agent with a clear prompt that includes:
   - The topic/question: `$ARGUMENTS`
   - What specific questions to answer
   - What to compare or evaluate (if applicable)
   - Instruction to write findings to `memory/research/`

3. **Read results.** After the researcher finishes, read `memory/status/researcher-latest.md` to get the output file path, then read the full research document.

4. **Summarize to user.** Present a concise summary of the findings and recommendation.

5. **Follow-up.** Ask the user if:
   - The findings should inform architecture decisions (if yes, dispatch the `architect` agent)
   - Additional research is needed on related topics

## Important

- You must NOT do the research yourself — always dispatch the `researcher` agent
- You must NOT edit any source code files
- Your role is to coordinate, summarize, and present results
