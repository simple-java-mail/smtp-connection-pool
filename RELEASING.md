# Releasing smtp-connection-pool

This repository follows the orchestration in Simple Java Mail's [maintainer workflow](https://github.com/bbottema/simple-java-mail/blob/master/MAINTAINER_WORKFLOW.md), adapted to this multi-module supporting library. This file is the authoritative local guide; `how to release.txt` is retained only as a pointer for old bookmarks.

The [real-server demo suite](smtp-connection-pool-demo/README.md) is executable release evidence, not ancillary sample code. It must remain prominent in user documentation, pass with the rest of the build, and stay out of Maven Central.

## Authorization boundary

Preparing code, release notes, a milestone, and a draft release plan does not authorize publication. Do not push release commits, approve a CircleCI deployment job, create or move tags, publish artifacts, or create the final GitHub release without explicit maintainer approval for that release.

Website changes in the sibling `simple-java-mail/simplejavamail.org` checkout also require their own approval and release flow.

## Naming conventions

For a release numbered `X.Y.Z`:

- GitHub milestone: `X.Y.Z` (no `v`)
- Git tag: `X.Y.Z` (no `v`)
- GitHub release title: `vX.Y.Z`
- Conventional release commit: `released X.Y.Z [skip ci]`
- Published Maven modules: all use `X.Y.Z`

Choose the version using both compatibility and release-signaling intent. A major version may deliberately mark a significant architectural or product milestone even when the existing API remains binary-compatible.

## 1. Plan the release

1. Choose the exact SemVer version and a planned publication date.
2. Create or reuse an open milestone whose title is exactly the numeric version.
3. Set its due date to the planned publication date and leave its description empty.
4. Assign every issue and PR represented in the release to that milestone, including already-closed dependency work included in the notes.
5. Cross-check that every issue/PR link in `RELEASE.txt` and `README.md` is represented in the milestone.

Do not create a due-date-free milestone merely to reserve a version. If the date is not known, record the target in planning documents and create the milestone when scheduling begins.

## 2. Prepare and verify

1. Verify that `generic-object-pool 2.4.1` and `clustered-object-pool 4.0.2` are published in Maven Central. These versions define the shutdown-completion and retired-pool cleanup semantics used by the provider; do not release against local-only substitutes.
2. Update `RELEASE.txt`, the README release-note summary, Javadocs, and directly relevant usage examples.
3. Keep the current released version accurate; add a separate next-release section rather than relabeling an already published version as unreleased.
4. Run the complete multi-module verification on JDK 21, without skipping static analysis, including provider discovery and all framework integration tests.
5. Run `mvn -pl smtp-connection-pool-jakarta-provider -am clean test -Djacoco.skip=true -Dlicense.skip=true` on an actual JDK 8. This proves that the original pool and Jakarta provider still work there; the Java-17 Camel/demo modules remain in the JDK-21 build.
6. Confirm that the original library is still published as `org.simplejavamail:smtp-connection-pool` and that the three library JARs plus their shared parent POM will be deployed. The `smtp-connection-pool-demo` module must build and test but must not be published.
7. Compare the original library's API and artifact/module metadata with the preceding release using the checksum-pinned binary-compatibility check. Update `core.compatibility.version` and `core.compatibility.sha256` only when preparing the next release line.
8. Inspect the effective POM for every module and run `mvn -s .circleci/maven-central-settings.xml clean deploy -DskipPublishing=true` as a non-publishing deploy-lifecycle rehearsal. `skipPublishing` deliberately prevents Central staging and upload; inspect the locally installed parent POM, all three runtime JAR/source/Javadoc sets, and the demo's build/test output instead. Confirm separately that the CircleCI orb's version rewrite keeps parent and child versions aligned; change the pipeline before release if it does not.
9. Rehearse or inspect Central bundle creation closely enough to prove that the parent and three library modules are included and `smtp-connection-pool-demo` is excluded. Do not discover an accidental demo publication after approval.
10. Confirm the working tree contains only intended release changes.
11. Merge the reviewed implementation to `master` and allow both the complete JDK 21 build and the JDK 8 pool/provider CircleCI jobs to finish.

For the CI release path, do not pre-edit the POM to the desired release version. The `github-maven-deploy` CircleCI job owns version bumping, deployment, the release commit, and the tag.

## 3. Publish with CircleCI

After explicit release approval:

1. Choose exactly one approval job: patch, minor, major, or as-is.
2. Approve the job that matches the version chosen during planning. A major increment may be intentional release signaling even when the compatibility checks pass.
3. Approve it once and monitor the corresponding deploy job.
4. Verify the expected release commit and exact numeric tag on GitHub.
5. Verify every expected module and its signatures in Maven Central. A successful CI job alone is not publication proof.

## 4. Create the GitHub release

Create exactly one GitHub release for the exact tag. Its body must be self-contained and tag-specific:

- describe user-visible changes and compatibility directly;
- link the issues that define those changes;
- show a short usage/configuration example where it helps adoption;
- mention each newly published artifact;
- do not replace the content with “see release notes”; and
- omit build logs, test evidence, and internal process narration.

Attach only assets belonging to this repository and tag. Normal Maven artifacts belong in Maven Central, not as duplicate GitHub attachments.

## 5. Close the release bookkeeping

1. Add a concise availability comment to each affected issue, including the released version and a useful configuration link or example.
2. Close issues that are fully delivered; leave partial work open and state what remains.
3. Recheck milestone membership against the final release notes and merged PRs.
4. Ensure every milestone item is closed.
5. Replace the milestone's planned due date with the actual GitHub publication date.
6. Close the milestone.
7. Confirm `README.md`, `RELEASE.txt`, tag, GitHub release, Maven Central, and milestone all name the same version and scope.

## 6. Coordinate Simple Java Mail

Supporting-library releases happen first.

1. Complete all smtp-connection-pool bookkeeping above.
2. Update [Simple Java Mail #698](https://github.com/bbottema/simple-java-mail/issues/698) with the released version and links.
3. In the Simple Java Mail repository, update the dependency, integration, tests, and a `Supporting Libraries` release-note bullet.
4. Change simplejavamail.org only when the corresponding Simple Java Mail behavior is available. Do not change its `manifest/site.json` merely to reflect a provider-only release version.
5. Validate website changes with `npm run check`, `npm run verifyLinks:internal`, and `npm run build` (plus external-link verification when appropriate).
6. Release Simple Java Mail separately under its own explicit authorization and workflow.

## Release definition of done

- Exact-version milestone contains every represented issue/PR.
- The complete multi-module build is verified and compatibility with the original library is covered.
- The published generic/clustered pool prerequisites are resolved from Maven Central, and the JDK 8 pool/provider CI gate passes.
- The multi-module version/deploy rehearsal proves that the CircleCI job will publish every expected module at the same version.
- The real-server demo smoke tests pass, and the Central bundle excludes the demo module.
- Expected tag and all Maven Central artifacts are available.
- One self-contained GitHub release exists for the tag.
- Fixed issues carry release-availability comments and are closed.
- Milestone due date equals actual publication date and the milestone is closed.
- README, release notes, examples, GitHub, and Maven Central agree.
- Simple Java Mail follow-up work starts only after the supporting release is complete.
