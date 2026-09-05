# Automated PR code review with a custom, skill-driven workflow

Resolves [#5](https://github.com/IgorJonski/Snoozeloo/issues/5). Researched 2026-09-05 against primary sources only (docs.github.com, github.blog changelogs, anthropics/claude-code-action + code.claude.com, docs.coderabbit.ai, docs.cloud.google.com, docs.cubic.dev). Every claim carries a URL; anything I could not confirm from a primary source is marked **(unconfirmed)**.

Context: solo developer, public Kotlin Multiplatform repo, GitHub Education (Student Developer Pack) account. Goal: every PR reviewed automatically, ideally by GitHub Copilot, following a **custom** workflow with named sub-reviews (like the local `/code-review` skill, which runs a *Standards* reviewer and a *Spec* reviewer in parallel sub-agents).

## Summary

- **No hosted reviewer lets you orchestrate named, parallel sub-reviews.** Every SaaS reviewer (Copilot, CodeRabbit, Gemini, Cubic) is a single review pass that you *instruct* via Markdown/YAML. The closest analogues to "named sub-reviews" are CodeRabbit's `pre_merge_checks.custom_checks` (named pass/fail checks) and Cubic's `custom_rules` (named agents that can link repo files). Only **`claude-code-action`** literally runs the repo's own `/code-review` skill, sub-agents included.
- **GitHub Copilot code review is free for a verified student** via the *Copilot Student* plan, which includes full Copilot code review ([plans](https://docs.github.com/en/copilot/get-started/plans)). Automatic review on every PR is a **branch ruleset** option, but the docs list only Pro/Pro+/Max as eligible; whether the Student plan exposes the ruleset toggle is **(unconfirmed)** — verify in repo Settings → Rules → Rulesets.
- Copilot code review is customized with `.github/copilot-instructions.md`, path-scoped `.github/instructions/*.instructions.md`, `AGENTS.md`, and — since the July 2026 GA — **agent skills** read from `.github/skills`, `.claude/skills`, or `.agents/skills`. A skill directory named `code-review` is the documented way to guarantee code review picks it up. `.github/agents/*.agent.md` custom agents are for the coding agent / CLI, **not** for code review.
- **GitHub Actions minutes are free on public repos**, so `claude-code-action` costs only Claude tokens (API key) or subscription usage (Claude Pro/Max OAuth token). Anthropic's hosted *Code Review* product is Team/Enterprise only at ~$15–25/review, so it's out of scope for an individual.
- CodeRabbit and Cubic are free for public repos with real customization hooks; Gemini Code Assist on GitHub is customizable only via one style guide and its GitHub app appears to require a Google Cloud project **(unconfirmed)**.

**Recommendation:** run **both** a zero-cost baseline and the exact custom workflow: (1) Copilot code review auto-requested by a ruleset, tuned with instructions files and a checked-in `code-review` skill; (2) `claude-code-action` running the repo's own `/code-review` skill on every same-repo PR, authenticated with a Claude subscription token if available, otherwise an API key with a turn cap. Details in [Recommendation](#recommendation).

## Comparison table

| | GitHub Copilot code review | `claude-code-action` (self-hosted workflow) | CodeRabbit | Gemini Code Assist on GitHub | Cubic |
|---|---|---|---|---|---|
| Cost for a student, public repo | $0 with Copilot Student (code review included); reviews draw on an AI-credit allowance of unstated size; Actions minutes free on public repos | Actions minutes free on public repos; Claude tokens via API key (pay per token) **or** included in a Claude Pro/Max subscription via OAuth token | $0 — "free reviews forever for public repositories" | $0 for individuals per Google blog; GitHub app may need a Google Cloud project + billing account **(unconfirmed)** | $0 — "automatically free for all public repositories" (fair-use) |
| Reviews every PR automatically | Yes via branch ruleset "Automatically request Copilot code review" (docs list Pro/Pro+/Max; Student **unconfirmed**) | Yes — `on: pull_request` `[opened, synchronize, …]`; fork PRs excluded (no secrets) | Yes — `reviews.auto_review.enabled` defaults `true` | Yes — `pull_request_opened.code_review` on by default | Yes — reviews new PRs once installed |
| Named sub-reviews / choose which checks run | No orchestration. Instructions + skills shape one review; MCP (read-only) for context | **Yes** — `prompt: "/code-review"` runs the repo skill including its parallel Standards/Spec sub-agents | Partial — `pre_merge_checks.custom_checks[]` are named pass/fail checks (`off`/`warning`/`error`); `path_instructions` per glob | No — one `.gemini/styleguide.md` + severity threshold | Partial — `reviews.custom_rules[]` named agents with `description` or linked `file_paths`, include/exclude globs (5 agents on free tier) |
| Reads repo skill/standard files | `.github/copilot-instructions.md`, `.github/instructions/*.instructions.md` (`applyTo`), `AGENTS.md`, skills in `.github/skills` / `.claude/skills` / `.agents/skills` | Everything Claude Code reads: `CLAUDE.md`/`AGENTS.md`, `.claude/skills`, `.claude/agents`, `.claude/settings.json` | `knowledge_base.code_guidelines.filePatterns` defaults include `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, `REVIEW.md` | `.gemini/config.yaml`, `.gemini/styleguide.md` only | `cubic.yaml` `custom_rules[].file_paths` (any repo file, read at PR head, 10k chars per agent) |
| Setup effort | Enable plan → create ruleset → add instruction files | Install Claude GitHub App → add secret → commit workflow YAML + skill | Install GitHub App → optional `.coderabbit.yaml` | Install app (+ Google Cloud project?) → optional `.gemini/` | Install app → optional `cubic.yaml` |
| Key limits | Comment-only review by default; no re-review on push unless "Review new pushes"; may repeat dismissed comments; default effort moves Lite→Balanced 2026-09-28 (costlier) | Cannot submit formal PR reviews/approve; inline comments only via MCP tool; only same-repo PRs get secrets | Paid tiers rate-limited (5 reviews/hr/dev on Essentials); free-tier limits unstated | Quota ≥100 PR reviews/day/installation | Starter: 20 reviews/mo for private; public unlimited under fair use |

## Per-tool details

### 1. GitHub Copilot code review

**What it is.** Copilot reviews the PR diff and leaves a *Comment* review with severity-tagged findings and one-click suggested changes; it "leaves a 'Comment' review, not an 'Approve' review" by default ([use-code-review](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/use-code-review)). Excluded files include dependency manifests, logs and SVGs ([concept page](https://docs.github.com/en/copilot/concepts/agents/code-review)).

**Cost / plan for a student.**
- Verified students get the **Copilot Student** plan: "Available to verified students. Get access to Copilot's features for free." The plan comparison lists full *Copilot code review* for Student (Copilot Free only gets "Review selection" in VS Code), an unstated "allowance of GitHub AI Credits", and "auto model selection only" ([plans](https://docs.github.com/en/copilot/get-started/plans), [individual plans](https://docs.github.com/en/copilot/concepts/billing/individual-plans), [GitHub Education for students](https://docs.github.com/en/education/about-github-education/github-education-for-students/about-github-education-for-students)).
- Since 2026-06-01 Copilot bills by **AI credits** (token-based) instead of premium requests ([usage-based billing](https://github.blog/news-insights/company-news/github-copilot-is-moving-to-usage-based-billing/)). A review "typically consumes an estimated $0.05 USD to $1 USD worth of AI credits with 'Lite' effort, and $0.25 USD to $5 USD worth of AI credits with 'Balanced' effort" ([concept page](https://docs.github.com/en/copilot/concepts/agents/code-review)). The legacy figure was 13 premium requests per review ([legacy requests page](https://docs.github.com/en/copilot/concepts/billing/copilot-requests)).
- Reviews also consume **GitHub Actions minutes** since 2026-06-01, but "There are no changes to public repositories, where Actions minutes remain free" ([changelog](https://github.blog/changelog/2026-04-27-github-copilot-code-review-will-start-consuming-github-actions-minutes-on-june-1-2026/)).
- The default effort switches from Lite to **Balanced on 2026-09-28** ([changelog](https://github.blog/changelog/2026-08-28-upcoming-changes-to-github-copilot-policies-and-billing/), [effort levels GA](https://github.blog/changelog/2026-08-07-copilot-code-review-effort-levels-are-generally-available/)). Set it explicitly to Lite under Settings → Copilot → Code review if the credit allowance is tight.
- **(unconfirmed)** The Student plan's monthly credit allowance and whether it is treated like Pro for ruleset auto-review. Pro is $10/mo with 1,000 credits + 500 flex ([individual plans](https://docs.github.com/en/copilot/concepts/billing/individual-plans)).

**Automatic review on every PR.** Repository admins create a branch ruleset: Settings → Rulesets → New branch ruleset → Enforcement *Active* → Target branches (e.g. default branch) → Branch rules → check **"Automatically request Copilot code review"**, optionally **"Review new pushes"** and **"Review draft pull requests"** → Create. The page states "This is only available if you are on the Copilot Pro, Copilot Pro+, or Copilot Max plans" ([configure-automatic-review](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/configure-automatic-review)). There is also a *personal* setting to auto-review your own PRs (same page). Student eligibility for these toggles: **(unconfirmed)** — check the UI; if absent, fall back to requesting Copilot as a reviewer manually or to option 2 below.

**Customizing the review.**
- `.github/copilot-instructions.md` — repo-wide, Markdown, "no longer than 2 pages", "must not be task specific" ([add-repository-instructions](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions)).
- `.github/instructions/NAME.instructions.md` with `applyTo: "**/*.kt"` frontmatter — path-specific; "only supported for Copilot cloud agent and Copilot code review". Optional `excludeAgent: "cloud-agent"` keeps a file review-only ([same page](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions), [configure-coding-guidelines](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/configure-coding-guidelines)).
- `AGENTS.md` (nearest in the tree wins) and root `CLAUDE.md` are also read ([same page](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions)). This repo already has both.
- Instructions are read "from the head branch (the branch with your changes), not the base branch" ([use-code-review](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/use-code-review)).
- **Agent skills** — GA for code review since 2026-07-29 for Pro/Pro+/Business/Enterprise ([changelog](https://github.blog/changelog/2026-07-29-copilot-code-review-agent-skills-and-mcp-now-generally-available/)). Project skills live in `.github/skills`, `.claude/skills`, or `.agents/skills`; a `SKILL.md` needs `name` and `description` frontmatter; "If you want to ensure that Copilot code review will read and use a skill, use a review-focused skill directory name such as `code-review`" ([add-skills](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/customize-cloud-agent/add-skills), [about-agent-skills](https://docs.github.com/en/copilot/concepts/agents/about-agent-skills)). MCP servers (repo Settings → Copilot → MCP servers) are read-only during review; GitHub and Playwright MCP are on by default ([changelog](https://github.blog/changelog/2026-07-29-copilot-code-review-agent-skills-and-mcp-now-generally-available/)).
- **Custom agents** (`.github/agents/*.agent.md`) are invoked from the agents panel, issue assignment, or Copilot CLI `/agent`; the docs do not describe using them for code review ([create-custom-agents](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/create-custom-agents)). Not a code-review mechanism.
- **Named sub-reviews:** not supported. You can describe "Standards" and "Spec" *sections* in the skill/instructions and ask Copilot to report under those headings, and the default GitHub MCP lets it read the linked issue for a spec check, but there is no way to run two isolated reviewers in parallel.

**Limits.** Copilot re-reviews pushes only if "Review new pushes" is on; it "may repeat the same comments again, even if they have been dismissed"; comment-only unless approvals are enabled (public preview) ([use-code-review](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/use-code-review), [configure-automatic-review](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/configure-automatic-review)).

### 2. Anthropic `claude-code-action` (Claude Code in GitHub Actions)

**What it is.** A GitHub Action that runs Claude Code in your workflow. Two modes, auto-detected: *interactive* (no `prompt` → responds to `@claude` mentions) and *automation* (a `prompt` → runs on any event without a mention) ([code.claude.com/github-actions](https://code.claude.com/docs/en/github-actions), [usage.md](https://github.com/anthropics/claude-code-action/blob/main/docs/usage.md)).

**Runs the repo's own skill — the exact requirement.** "The `prompt` input accepts a skill invocation as well as plain text: for a skill in your repository's `.claude/skills/` directory, run `actions/checkout` before the `anthropics/claude-code-action` step so the skill files are available on the runner, then pass `/skill-name` as the `prompt`" ([github-actions](https://code.claude.com/docs/en/github-actions)). Project sub-agents in `.claude/agents/` and skills that delegate to sub-agents work in non-interactive (`-p`) mode, which is what the action uses ([sub-agents](https://code.claude.com/docs/en/sub-agents)). So the local `/code-review` skill (Standards + Spec in parallel sub-agents) runs unchanged once it is checked into the repo.

**Cost for a student on a public repo.**
- "GitHub Actions usage is free for … public repositories that use standard GitHub-hosted runners" ([Actions billing](https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-actions)).
- Model usage: "each interaction consumes tokens … If you authenticate with an OAuth token, runs use your Claude subscription instead of API billing." The OAuth token is "available on Pro, Max, Team, and Enterprise plans. Generate one by running `claude setup-token` locally" ([github-actions](https://code.claude.com/docs/en/github-actions), [setup.md](https://github.com/anthropics/claude-code-action/blob/main/docs/setup.md)). No per-review price is published; cap with `--max-turns` and a workflow `timeout-minutes`.
- For contrast, Anthropic's hosted *Code Review* (no workflow file) is "available for Team and Enterprise subscriptions" and "Each review averages $15-25" ([code-review](https://code.claude.com/docs/en/code-review)) — not applicable to an individual student.

**Automatic on every PR.** Trigger on `pull_request: [opened, synchronize, ready_for_review, reopened]`. "On public repositories, GitHub withholds secrets from runs triggered by fork pull requests, so the review runs only on pull requests from branches in the same repository" ([github-actions](https://code.claude.com/docs/en/github-actions)) — fine for a solo repo. The action also rejects bot actors unless listed in `allowed_bots` and requires the triggering user to have write access.

**Posting the review.** By default results go to the run log; add `--allowedTools "mcp__github_inline_comment__create_inline_comment,Bash(gh pr comment:*)"` in `claude_args` and tell the prompt to post ([solutions.md](https://github.com/anthropics/claude-code-action/blob/main/docs/solutions.md)). Limits: "Claude cannot submit formal GitHub PR reviews" and "cannot approve pull requests"; no Bash unless allowed ([capabilities-and-limitations.md](https://github.com/anthropics/claude-code-action/blob/main/docs/capabilities-and-limitations.md)).

**Setup.** Install the [Claude GitHub App](https://github.com/apps/claude) (admin), add `ANTHROPIC_API_KEY` or `CLAUDE_CODE_OAUTH_TOKEN` as a repo secret, commit the workflow. `/install-github-app` from a local Claude Code session automates all three ([github-actions](https://code.claude.com/docs/en/github-actions)). Workflow permissions: `contents`, `pull-requests`, `issues`, `id-token: write` ([setup.md](https://github.com/anthropics/claude-code-action/blob/main/docs/setup.md)).

### 3. CodeRabbit

- **Cost:** "install CodeRabbit on a public repository, and receive free reviews forever for public repositories"; paid Essentials is $24/dev/mo annual with 5 PR reviews/hour/dev ([pricing](https://www.coderabbit.ai/pricing)). Free-tier rate limits: **(unconfirmed)**.
- **Automatic:** `reviews.auto_review.enabled` defaults to `true`; `drafts`, `base_branches`, `labels`, `ignore_title_keywords` filters ([configuration reference](https://docs.coderabbit.ai/reference/configuration)).
- **Customization:** `reviews.path_instructions[]` (glob + instructions); `pre_merge_checks.custom_checks[]` with `name` (≤50 chars), `mode: off|warning|error`, `instructions` (≤10,000 chars) — the closest thing to named checks; `knowledge_base.code_guidelines.filePatterns` defaults include `**/AGENTS.md`, `**/CLAUDE.md`, `.github/copilot-instructions.md`, `**/REVIEW.md`, so this repo's `AGENTS.md` is picked up automatically; `reviews.profile: quiet|chill|assertive`; 60+ linters via `reviews.tools` ([configuration reference](https://docs.coderabbit.ai/reference/configuration), [review instructions guide](https://docs.coderabbit.ai/guides/review-instructions)). Whether `pre_merge_checks` are available on the free/OSS tier: **(unconfirmed)**.
- **Setup:** sign up with GitHub, add the repo in the dashboard, optional `.coderabbit.yaml` at repo root ([quickstart](https://docs.coderabbit.ai/getting-started/quickstart)).
- **Sub-reviews:** custom checks are named and independently reported, but they are pass/fail gates inside one review, not isolated reviewers, and they cannot reference skill files beyond the guideline patterns.

### 4. Gemini Code Assist on GitHub

- **Cost:** Google's announcement says Gemini Code Assist, including GitHub code review, is free for individuals ([blog](https://blog.google/innovation-and-ai/technology/developers-tools/gemini-code-assist-free/)). The current docs are under Google Cloud and describe setup through a Google Cloud project; a search summary indicated a project with a billing account is required for the GitHub app even though usage is free **(unconfirmed — the set-up page could not be fetched)** ([review-repo-code](https://docs.cloud.google.com/gemini/docs/code-review/review-repo-code)).
- **Quota:** "at least 100 pull request reviews per day" per installation, not counted against general Code Assist quotas ([quotas](https://docs.cloud.google.com/gemini/docs/quotas)).
- **Automatic:** `pull_request_opened.code_review` "Posts a code review on pull request open" and is enabled by default; `/gemini review` on demand ([customize-repo-review](https://docs.cloud.google.com/gemini/docs/code-review/customize-repo-review)).
- **Customization:** `.gemini/config.yaml` (`comment_severity_threshold`, `max_review_comments`, `ignore_patterns`, `have_fun`) and one `.gemini/styleguide.md` of natural-language rules ([customize-repo-review](https://docs.cloud.google.com/gemini/docs/code-review/customize-repo-review)). No named checks, no path scoping, no skill files.

### 5. Cubic

- **Cost:** "cubic is free for public repositories. Just connect cubic to a public repository to get AI reviews" and "cubic is automatically free for all public repositories" subject to fair use; Starter is free with 20 reviews/mo and up to 5 custom agents; Team $30/dev/mo annual ([pricing-plans](https://www.cubic.dev/pricing-plans), [subscription](https://docs.cubic.dev/account/subscription)).
- **Automatic:** "Once installed, cubic will automatically review new pull requests" ([docs home](https://docs.cubic.dev/)).
- **Customization:** `cubic.yaml` at repo root with `reviews.custom_instructions`, `reviews.ignore`, `reviews.sensitivity`, and `reviews.custom_rules[]` — each rule has a `name` plus either an inline `description` or `file_paths` (repo-relative files "read … from the pull request head commit"), with `include`/`exclude` globs; "The agent text and linked files share one 10,000 character limit per custom agent" ([cubic-yaml](https://docs.cubic.dev/configure/cubic-yaml), [custom-agents](https://docs.cubic.dev/ai-review/custom-agents)). `AGENTS.md`/`CLAUDE.md` are not read implicitly — link them via `file_paths`.
- **Sub-reviews:** named custom agents can point straight at `.claude/skills/code-review/SKILL.md` or `AGENTS.md`, which is the best "reference skill files in the repo" story among the SaaS tools, but they still run inside a single review and the 10k-char cap per agent applies.

## Recommendation

**Primary: GitHub Copilot code review, auto-requested by a ruleset and steered by instruction files + a checked-in `code-review` skill.** It is free on the Student plan, native to the PR UI, needs no secrets, and now reads skills. Its weakness (no parallel named reviewers) is covered by the secondary.

**Secondary: `claude-code-action` running the repo's own `/code-review` skill on every same-repo PR.** This is the only option that executes the Standards/Spec parallel workflow as written. Cost is zero on the Actions side; model usage comes from a Claude Pro/Max subscription token if you have one, otherwise an API key capped by `--max-turns`. If neither is acceptable, substitute **CodeRabbit** (free on public repos, named `custom_checks`, reads `AGENTS.md`) as the secondary.

If the ruleset toggle turns out to be unavailable on the Student plan, promote `claude-code-action` (or CodeRabbit) to primary and request Copilot reviews manually.

### Files to add

1. **`.claude/skills/code-review/SKILL.md`** — copy of the local `~/.claude/skills/code-review/SKILL.md`. One file serves both tools: Copilot scans `.claude/skills` and the `code-review` directory name is the documented trigger; `claude-code-action` runs it via `prompt: "/code-review"`. Add `allowed-tools` frontmatter (`Agent`, `Bash(git diff:*)`, `Bash(git log:*)`, `Bash(git rev-parse:*)`, `Bash(gh issue view:*)`, `Bash(gh pr comment:*)`, `mcp__github_inline_comment__create_inline_comment`) so the sub-agents and posting work in CI, and change step 1's "ask for the fixed point" to default to `origin/<base branch>` when running non-interactively.

2. **`.github/copilot-instructions.md`** — short, repo-wide: point at `AGENTS.md`, state the two review axes (Standards vs. Spec — report under separate headings, never merge), the KMP layering rules, and "skip anything ktlint/detekt enforce". Keep it under two pages.

3. **`.github/instructions/kotlin.instructions.md`**
   ```markdown
   ---
   applyTo: "**/*.kt,**/*.kts"
   excludeAgent: "cloud-agent"
   ---
   Review Kotlin against the layering in AGENTS.md (ViewModel → use case → repository → data source),
   MVI state/action/event conventions, Result-based error handling, and commonTest coverage.
   Cite the standard you are applying. Flag Fowler smells as judgement calls, not violations.
   ```
   Optionally `compose.instructions.md` (`applyTo: "**/ui/**/*.kt"`) for recomposition/stability rules.

4. **Branch ruleset** (UI, not a file): Settings → Rulesets → New branch ruleset → Active → target default branch → "Automatically request Copilot code review" + "Review new pushes" → Create. Then Settings → Copilot → Code review → effort **Lite** to conserve credits (default becomes Balanced on 2026-09-28).

5. **`.github/workflows/claude-code-review.yml`**
   ```yaml
   name: Claude Code Review
   on:
     pull_request:
       types: [opened, synchronize, ready_for_review, reopened]
   concurrency:
     group: claude-review-${{ github.event.pull_request.number }}
     cancel-in-progress: true
   jobs:
     review:
       if: github.event.pull_request.head.repo.full_name == github.repository && !github.event.pull_request.draft
       runs-on: ubuntu-latest
       timeout-minutes: 20
       permissions:
         contents: read
         pull-requests: write
         issues: read
         id-token: write
       steps:
         - uses: actions/checkout@v6
           with:
             fetch-depth: 0   # the skill diffs against the merge-base
         - uses: anthropics/claude-code-action@v1
           with:
             # or: anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}
             claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
             prompt: |
               /code-review origin/${{ github.event.pull_request.base.ref }}
               Spec source: the issue referenced by this PR (#${{ github.event.pull_request.number }} body and commits).
               Post the aggregated report with `gh pr comment ${{ github.event.pull_request.number }}` and file
               inline comments for concrete findings.
             claude_args: |
               --max-turns 40
               --allowedTools "Agent,Bash(git diff:*),Bash(git log:*),Bash(git rev-parse:*),Bash(gh issue view:*),Bash(gh pr comment:*),mcp__github_inline_comment__create_inline_comment"
   ```
   Secrets: `CLAUDE_CODE_OAUTH_TOKEN` from `claude setup-token` (Pro/Max) or `ANTHROPIC_API_KEY`. Install the Claude GitHub App first (`/install-github-app` does app + secret + workflow in one go).

6. *(If CodeRabbit is chosen instead of the action)* **`.coderabbit.yaml`**
   ```yaml
   reviews:
     profile: chill
     path_instructions:
       - path: "**/*.kt"
         instructions: "Apply AGENTS.md layering and MVI rules; cite the rule; skip what ktlint/detekt enforce."
   pre_merge_checks:
     custom_checks:
       - name: "Standards"
         mode: warning
         instructions: "Fail if any changed Kotlin file violates a documented standard in AGENTS.md or docs/agents/*.md. Quote the rule."
       - name: "Spec"
         mode: warning
         instructions: "Fail if the PR omits a requirement from its linked issue, adds unrequested behaviour, or implements a requirement incorrectly. Quote the issue line."
   ```

### Things to verify after enabling (not confirmable from docs)

- The Student plan shows the "Automatically request Copilot code review" ruleset option and what its monthly AI-credit allowance is.
- Copilot code review actually loads `.claude/skills/code-review/SKILL.md` (attribution appears in the review comment when a skill was used).
- Whether CodeRabbit's `pre_merge_checks` and Gemini's GitHub app are available at $0 for an individual public repo.
