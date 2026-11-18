---
name: publish-pushca-client
description: Release io.github.maksimbugay:pushca-client (client/java) to Maven Central via the Central Publisher Portal. Use when asked to publish, release or deploy a new pushca-client version.
---

# Publish pushca-client to Maven Central

Project: `client/java` in this repo. Artifact `io.github.maksimbugay:pushca-client`.
Publishing goes through the Central Publisher Portal (OSSRH is shut down).

## 1. Pre-flight

```bash
cd client/java
git status -sb                       # clean, on master, in sync with origin
grep -m1 -n "<version>" pom.xml      # project version = the version to release
git tag | tail -3                    # the version must not already be tagged
gpg --list-secret-keys 02B9F39B1FFA2E1F56BC1704C708755D601CA9CE   # signing key present, not expired (expires 2028-11-12)
```

- If the pom version is not the requested one, bump `<version>` in `pom.xml` and commit it (ask before committing/pushing).
- A released version can never be re-published. If the requested version already exists on Central, stop and tell the user.
- Do not run the full test suite (the user runs it); the deploy build runs only what the pom already runs.

## 2. Upload and validate

```bash
mvn -Prelease clean deploy 2>&1 | tail -40
```

- **Never pass `-B`**: in batch mode maven-gpg-plugin 3.2.x disables pinentry and signing fails with "No pinentry" once the gpg-agent cache expires. Without `-B`, a pinentry-mac dialog asks the user for the passphrase (it is never stored in the pom).
- The `release` profile uses `central-publishing-maven-plugin` with `autoPublish=false`, `waitUntil=validated`: it only uploads and validates. Take the `deploymentId` from the line `Uploaded bundle successfully, ... deploymentId: <id>`.
- Must end with `Deployment <id> has been validated` and `BUILD SUCCESS`.

## 3. Publish (irreversible)

Publishing cannot be undone. Proceed only if the user explicitly asked to publish this version in the current request; otherwise confirm first.

```bash
.claude/skills/publish-pushca-client/central.sh publish <deploymentId>   # expect HTTP 204
.claude/skills/publish-pushca-client/central.sh status  <deploymentId>   # PUBLISHING -> PUBLISHED
```

(Paths are relative to the repo root `/Users/mbugai/work/pushca-public`.) Alternative: the Publish button at https://central.sonatype.com/publishing/deployments.

- `PUBLISHING` takes ~40 min. The error `Deployment components info not found` during PUBLISHING is harmless.
- `FAILED` → show the errors to the user; nothing went public.
- To wait, check status every ~10 min (don't poll tightly). Once `PUBLISHED`, it appears at https://repo1.maven.org/maven2/io/github/maksimbugay/pushca-client/<version>/ shortly after.

## 4. Tag

Release tags are annotated and local only (they have not been pushed to origin):

```bash
git tag -a v<version> -m "pushca-client <version>" <release commit>
```

Push the tag only if the user asks.

## 5. Report

Tell the user: version, deploymentId, current state (PUBLISHING/PUBLISHED), tag created, and anything skipped.
