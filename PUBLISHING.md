# Publishing

The library publishes through [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin).
Its coordinates and POM live in `gradle.properties` (`GROUP`, `VERSION_NAME`, `POM_*`). Every
variant builds from any OS, iOS included: Kotlin cross-compiles the iOS klibs, and only linking an
app needs a Mac.

Before the first release, check the `POM_*` URLs in `gradle.properties`. They assume a GitHub
repository at `github.com/M1nexoff/theme-rift`.

## JitPack

JitPack builds straight from a git tag, so there is nothing to upload and no keys to manage.

1. Push this repository to GitHub, public, as `M1nexoff/theme-rift`.
2. Tag a release and push the tag:
   ```sh
   git tag 0.1.0
   git push origin 0.1.0
   ```
3. Open <https://jitpack.io/#M1nexoff/theme-rift>, find the tag and press **Get it**. The first
   request starts the build, and its log is linked from that page.

JitPack runs `jitpack.yml`: JDK 21 through SDKMAN, then `:theme-rift:publishToMavenLocal`, with
JitPack's `GROUP`, `ARTIFACT` and `VERSION` variables passed in as the coordinates. The artifacts
come out as:

```kotlin
implementation("com.github.M1nexoff.theme-rift:theme-rift:0.1.0")
```

If the build fails at `sdk install java`, SDKMAN may have retired that identifier. Pick a current
`21.x` one from `sdk list java` and update `jitpack.yml`.

## Maven Central

### One-time setup

1. **Account and namespace.** Sign in at <https://central.sonatype.com> with the GitHub account
   `M1nexoff`. The namespace `io.github.m1nexoff` is then verified automatically. With a different
   account, change `GROUP` to the namespace you own, and the package name to match.
2. **Publishing token.** In the portal, open *View Account → Generate User Token*. That gives you a
   username and password pair.
3. **Signing key.** Maven Central only accepts signed artifacts.
   ```sh
   gpg --full-generate-key                                   # RSA 4096 is a safe choice
   gpg --list-secret-keys --keyid-format SHORT               # the id is after "rsa4096/"
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --export-secret-keys --armor <KEY_ID> | grep -v '\-\-' | grep -v '^=.' | tr -d '\n'
   ```
   The last command prints the key on one line, which is the form Gradle needs.
4. **Credentials.** Put them in `~/.gradle/gradle.properties`, your user-level file and **never the
   one in this repository**:
   ```properties
   mavenCentralUsername=<token username>
   mavenCentralPassword=<token password>
   signingInMemoryKey=<the one-line key>
   signingInMemoryKeyId=<KEY_ID>
   signingInMemoryKeyPassword=<key passphrase>
   ```
   On CI, set the same names as `ORG_GRADLE_PROJECT_<name>` environment variables. Signing turns on
   only when `signingInMemoryKey` is present, so local and JitPack builds never need the key.

### Each release

1. Set `VERSION_NAME` in `gradle.properties` and add an entry to `CHANGELOG.md`.
2. Run the tests: `./gradlew :theme-rift:desktopTest`.
3. Upload:
   ```sh
   ./gradlew :theme-rift:publishToMavenCentral
   ```
   This uploads and validates the deployment and stops there. Review it under *Deployments* in the
   portal and press **Publish**. Once you trust the process,
   `./gradlew :theme-rift:publishAndReleaseToMavenCentral` does both in one step.
4. Tag the commit (`git tag <version> && git push origin <version>`) so JitPack and Maven Central
   carry the same versions.

A release can take up to about 30 minutes to appear on `repo1.maven.org` after it is published.
