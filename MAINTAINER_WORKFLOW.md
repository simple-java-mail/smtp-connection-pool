# Maintainer Workflow

This document describes the normal maintenance loop for SMTP Connection Pool. It is adapted from Simple Java Mail's maintainer workflow so the projects use the same GitHub labels, release milestones, and release-note standards.

For the repository-specific multi-module release checks, read [RELEASING.md](RELEASING.md) as part of the release phase. That companion covers the Jakarta Mail provider, Camel adapter, executable demos, Java baselines, and Maven Central bundle.

---

## 1. Interpret the Request

Classify the request before changing repository or GitHub state:

| Request shape | Main mode | Release? |
| --- | --- | --- |
| "Pick up #N" | Issue-driven implementation | No, unless explicitly requested |
| "Triage and fix the open issues" | Triage and implementation | No, unless explicitly requested |
| "Handle dependency PRs" | Dependency maintenance | No, unless explicitly requested |
| "Fix #N and release a patch" | Implementation plus release | Yes, after verification |
| "Prepare a minor release" | Release preparation | Stop before publication unless publication was authorized |

Preparing code, notes, a milestone, or a release plan is not permission to publish. A request or approval to make a semantic-version release is standing authorization for that release through CircleCI, Maven Central verification, GitHub release creation, and milestone closure unless the maintainer withdraws it.

Do not interpret the words "patch" or "minor" as release approval when they only describe a code change.

---

## 2. Start from Live State

Ground every task in the local checkout and GitHub state:

```powershell
git status --short --branch
git fetch --prune --tags origin
git branch -vv
gh auth status
gh repo view simple-java-mail/smtp-connection-pool
```

This repository uses `master` as its only long-lived branch. Do not introduce a `develop`-branch flow from Simple Java Mail. Use a short-lived topic branch for reviewed implementation work when appropriate, and merge or fast-forward it to `master` before a release.

Before changing `master`, confirm it is aligned with `origin/master`:

```powershell
git switch master
git pull --ff-only origin master
```

Do not rewrite shared history. If a fast-forward is not possible, inspect the divergence and stop before guessing.

---

## 3. Triage GitHub Work

Read the issue or pull request, comments, labels, milestone, and related repository history before choosing a fix:

```powershell
gh issue view 123 --repo simple-java-mail/smtp-connection-pool --comments `
    --json number,title,body,labels,milestone,author,url,comments
gh issue list --repo simple-java-mail/smtp-connection-pool --state open --limit 100 `
    --json number,title,labels,milestone,author,url
gh pr list --repo simple-java-mail/smtp-connection-pool --state open --limit 100 `
    --json number,title,author,baseRefName,headRefName,url,labels,statusCheckRollup
```

### Canonical labels

Use the same canonical labels as Simple Java Mail:

- Added functionality: choose exactly one of `enhancement` or `major feature`.
- Work type: `bug`, `maintenance`, `documentation`, `security`, `dependencies`, or `3rdparty-problem`.
- Triage: `duplicate`, `invalid`, `question`, `need-user-input`, `needs-research`, `will close soon`, `postponed indefinitely`, or `wontfix`.
- Assistance and priority: `help wanted`, `Priority-Low`, `Priority-Medium`, or `Priority-High`.
- Automation: `java` for dependency pull requests that update Java code.

`enhancement` is for an incremental addition. `major feature` is for a headline capability deserving prominent release treatment; it does not imply a SemVer major version. Never put both on one issue. Orthogonal labels such as `security`, `dependencies`, `3rdparty-problem`, and a priority may accompany the primary type.

When an old label duplicates a canonical label, migrate every assignment before deleting or renaming it. Preserve genuinely distinct project-specific labels.

Re-read an issue's current labels immediately before changing them. Treat a maintainer's removal or replacement as deliberate.

### Release milestones

Release milestones use the exact numeric version without a `v` prefix. Keep descriptions empty unless the maintainer explicitly requests a cross-version advisory.

```powershell
$repo = "simple-java-mail/smtp-connection-pool"
$version = "4.0.2"
$plannedDate = "2026-08-20T00:00:00Z"
$milestones = gh api "repos/$repo/milestones?state=all&per_page=100" --paginate | ConvertFrom-Json
$milestone = $milestones | Where-Object title -eq $version

if (-not $milestone) {
    $milestone = gh api -X POST "repos/$repo/milestones" `
        -f title=$version -f state=open -f due_on=$plannedDate -f description='' | ConvertFrom-Json
} elseif ($milestone.state -eq "open") {
    $milestone = gh api -X PATCH "repos/$repo/milestones/$($milestone.number)" `
        -f due_on=$plannedDate -f description='' | ConvertFrom-Json
}
```

Assign every issue and pull request represented by the release, including dependency PRs included in a maintenance roll-up. GitHub treats pull requests as issues for milestone updates:

```powershell
gh api -X PATCH "repos/$repo/issues/123" -F milestone=$milestone.number
gh api "repos/$repo/issues?milestone=$($milestone.number)&state=all&per_page=100" `
    --paginate --jq '.[] | [.number,.state,.title] | @tsv'
```

Exclude rejected, superseded, or unrelated proposals. Before publication, cross-check every issue and PR link in the version's notes against milestone membership.

After publication, close the milestone only when every included item is closed. Replace the planned due date with the actual release date at UTC midnight:

```powershell
$publishedAt = gh release view $version --repo $repo --json publishedAt --jq .publishedAt
$actualDate = ([DateTimeOffset]$publishedAt).UtcDateTime.ToString("yyyy-MM-ddT00:00:00Z")
gh api -X PATCH "repos/$repo/milestones/$($milestone.number)" `
    -f state=closed -f due_on=$actualDate -f description=''
```

For historical reconstruction, use the tag's original release date rather than the date on which GitHub bookkeeping was backfilled.

### Upstream fixes

SMTP Connection Pool sits above `clustered-object-pool` and `generic-object-pool`. Confirm which repository owns a defect before changing this one. If the fix belongs upstream:

1. Create or update the upstream issue.
2. Fix, verify, and release the upstream library when authorized.
3. Complete that repository's issue, milestone, and GitHub release bookkeeping.
4. Update this repository only after the upstream artifact is available from Maven Central.

Do not release, retag, or push a sibling repository without explicit scope covering that repository.

---

## 4. Implement Deliberately

Read the relevant code and tests before editing. Prefer a failing regression test for reproducible defects.

For public API or configuration changes:

- Keep the direct pool, Jakarta provider, Camel adapter, executable demos, Javadocs, and README aligned where applicable.
- Preserve clear ownership of a physical connection pool; never stack the direct pool around the `smtppool` provider.
- Keep credential and OAuth2 token material out of exceptions, logs, `toString()` output, and test fixtures.
- Treat graceful shutdown, forced shutdown, lease invalidation, and credential rotation as one lifecycle contract.
- Preserve Java 8 compatibility for the core pool and Jakarta provider. The Camel adapter and demo may use their documented newer baselines.
- Add a runnable demo when a new integration path would otherwise be difficult to validate or adopt.
- Keep `smtp-connection-pool-demo` build-tested but unpublished.

For dependency changes:

- Resolve the proposed artifacts from Maven Central; do not validate against local-only sibling builds.
- Check Java bytecode and transitive runtime compatibility for each affected module.
- Distinguish a routine dependency update from an upstream bug or security fix that users need to understand.

---

## 5. Verify

Run focused tests first, then the complete checks proportional to the change.

The complete multi-module build uses JDK 21:

```powershell
mvn clean verify
```

The core pool and Jakarta provider must also pass on an actual JDK 8:

```powershell
mvn -pl smtp-connection-pool-jakarta-provider -am clean test `
    -Djacoco.skip=true -Dlicense.skip=true
```

The full build covers provider discovery, Spring and Camel integration, real-server demo smoke tests, static analysis, Javadocs, published manifest checks, JPMS consumption, and the checksum-pinned binary compatibility comparison.

If a build applies generated license headers, remove generated source changes before committing unless the generated artifact itself is under inspection:

```powershell
mvn com.mycila:license-maven-plugin:3.0:remove
```

For release preparation, follow the additional effective-POM, non-publishing deploy rehearsal, artifact-set, and bundle checks in [RELEASING.md](RELEASING.md).

---

## 6. Documentation and Release Notes

`RELEASE.txt` is the complete repository release history. The README is the developer landing page and should keep its current-version examples and a concise current release section rather than duplicating the whole archive.

For user-facing work:

- Update `RELEASE.txt`, the README's current version and release summary, Javadocs, and relevant module/demo documentation together.
- Explain behavior and compatibility, not internal implementation effort.
- Link issues and pull requests for supporting detail, but make the note understandable without opening those links.
- Add migration guidance for changed APIs, Java baselines, artifact coordinates, provider selection, defaults, or lifecycle behavior.

Release-note retention rules:

- Keep the complete history in `RELEASE.txt`.
- Major releases may use a narrative section when it clarifies a new integration model.
- Minor and patch releases normally use concise, self-explanatory bullets.
- A repository-history heading may cover a version range, but every bullet in a range must identify the exact version that first shipped it.
- Order version-prefixed bullets newest first within a range.
- Never combine changes first released in different versions into one ambiguous bullet.
- Group routine dependency updates into a compact maintenance bullet unless a dependency fixes a user-visible defect or security problem.

### GitHub releases

Create one GitHub release for every published tag, including every patch tag. The tag is numeric and the display title is `vX.Y.Z`.

Every GitHub release body must be a permanent, self-contained record of that tag:

- State what changed in that version.
- State compatibility or migration impact directly.
- Link issues, PRs, commits, Maven Central, and tagged documentation only as supporting references.
- Never use "see release notes" as a substitute for a summary.
- Keep build, test, packaging-validation, and release-process evidence out of the public body.
- Do not describe changes that landed after the tag.
- Attach assets only to their matching tag; normal Maven artifacts remain in Maven Central.

Create the release after the tag and Maven Central artifacts exist:

```powershell
gh release create $version --repo simple-java-mail/smtp-connection-pool `
    --title "v$version" --notes-file RELEASE_NOTES.md
```

After publication, add a short availability comment to issues materially delivered by the release.

---

## 7. Commit and Push

Stage only intended files and inspect the result:

```powershell
git status --short
git diff
git add <paths>
git diff --cached --check
git diff --cached --stat
git commit -m "fix(scope): concise summary"
git push origin HEAD
```

Use semantic subjects such as `fix(pool): ...`, `feat(provider): ...`, `docs(release): ...`, and `build(ci): ...`.

Keep unrelated implementation, documentation, build, and release-note changes in separate commits when they are independently meaningful. A documentation-only bookkeeping commit may use `[skip ci]`; implementation and release-lane changes must run CI.

For a task that does not include a release:

1. Push the implementation or documentation change.
2. Update the issue with a concise summary and a usage example when useful.
3. Apply the intended labels and milestone.
4. Close only fully delivered issues.
5. Leave planned release notes clearly unreleased.
6. Report checks and clean repository status.

---

## 8. Release

Only publish when the maintainer authorized a semantic-version release. Read [RELEASING.md](RELEASING.md) before starting this phase.

Before approving CircleCI:

1. Inspect open dependency PRs and include only safe, release-ready updates that fit the authorized scope.
2. Run the complete JDK 21 verification and the JDK 8 core/provider verification on the final candidate.
3. Update `RELEASE.txt`, README version/examples, and relevant module documentation.
4. Create or reuse the exact-version open milestone with the planned release date and an empty description.
5. Assign every represented issue and PR and cross-check note links against membership.
6. Verify that required generic and clustered pool versions are already available from Maven Central.
7. Confirm the demo builds but is excluded from publication, and that all published modules share the version.
8. Confirm the worktree is clean and `master` is current.
9. Push `master` and wait for both the JDK 21 and JDK 8 CircleCI jobs.

Do not pre-edit POM versions. The `github-maven-deploy` CircleCI workflow owns version rewriting, deployment, the conventional release commit, and the numeric tag.

CircleCI exposes patch, minor, major, and as-is approval gates. Approve exactly the gate matching the authorized version, once. A green deploy job is not enough: verify the remote release commit and tag and every expected Maven Central artifact.

After deployment:

1. Fetch the release commit and tags.
2. Verify the parent POM and all three published library modules in Maven Central; confirm the demo is absent.
3. Create the self-contained GitHub release for the exact tag.
4. Add concise availability comments and close fully delivered issues.
5. Recheck milestone membership and confirm every included item is closed.
6. Set the milestone due date to the actual publication date and close it.
7. Confirm README, `RELEASE.txt`, examples, tag, GitHub release, Maven Central, and milestone agree.
8. Start a Simple Java Mail dependency update only after this supporting release is complete.

If a Central artifact is wrong or missing, assume the published version is immutable. Correct the release lane and publish a new patch; do not move or replace the old tag.

---

## 9. Defensive Dependency Sweep

Run a dependency-PR sweep before every planned release, even when the original request did not mention dependencies:

```powershell
gh pr list --repo simple-java-mail/smtp-connection-pool --state open --author app/dependabot `
    --json number,title,baseRefName,mergeStateStatus,statusCheckRollup,url
```

Include a dependency update only when it is compatible with the affected Java baselines, resolves cleanly, has passing checks, fits the authorized release scope, and can be verified with the final candidate. Leave uncertain, incompatible, or behavior-changing upgrades for separate work.

Every included dependency PR belongs in the release milestone even when several PRs are summarized in one release-note bullet.

---

## 10. Definition of Done

For a non-release task:

- The intended change is committed and pushed.
- Relevant tests pass or any unavailable check is explained.
- GitHub issues, labels, and milestones are current.
- User-facing changes have release notes or a clearly marked unreleased entry.
- The worktree is clean and aligned with its upstream branch.

For a release task:

- The defensive dependency sweep is complete.
- The numeric tag exists remotely and Maven Central contains the expected parent and three library modules.
- The demo was tested and was not published.
- One self-contained GitHub release exists for the exact tag with title `vX.Y.Z`.
- The release body describes changes and compatibility without internal verification narration.
- README, `RELEASE.txt`, examples, module docs, tag, release, and Maven Central agree.
- The exact-version milestone contains every represented issue and PR; all items and the milestone are closed.
- The closed milestone's due date equals the actual release date.
- Related issues contain a concise availability comment.
- The local checkout is clean and aligned with `origin/master`.
