# Global Instructions for AI Agents

All agents (Windsurf, Claude Code, Codex, and others) MUST read and adhere to the latest instructions from the local repository before and during execution.

**Note**: This file acts as the primary instruction set. Any specific rules found within the `.agents/` folder at the root of this repository override general knowledge.

Please refer to `.agents/README.md` for the central index and routing hub of all specialized instructions.

## Repository Skill (MANDATORY)

This repository ships a platform-specific operating skill in [`SKILLS.md`](SKILLS.md). Before writing or modifying any code in this repository, you MUST read and apply [`SKILLS.md`](SKILLS.md). Its rules govern how code in this repository is built.

## Git Workflow & Commits (MANDATORY)

- **Before creating any branch**, you MUST read and follow [`.agents/git/workflow.md`](.agents/git/workflow.md). Branch names MUST use the strict `{type}/{primary-noun}` (or `{type}/{primary-noun}-{secondary-noun}`) format. NEVER use a random or auto-generated branch name, and NEVER work directly on `master`.
- **Before creating any commit**, you MUST read and follow [`.agents/git/commits.md`](.agents/git/commits.md). Every commit message MUST follow the Conventional Commits specification.
- **Commit scope**: Do NOT bundle all work into a single commit. Commit each change or group of related changes separately, scoped per the rules above, and verify the diff before committing.
