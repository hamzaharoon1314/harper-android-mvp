# Upgrading Harper Core

This project relies on `harper-core` via FFI to perform all grammar analysis. Because the grammar engine frequently receives updates to its dictionary, rule set, and optimizations, you will occasionally need to upgrade the core dependency.

## Generating Release Notes for Core-Version Changes

When upgrading `harper-core` in `Cargo.toml`, you must summarize the upstream changes in the Android app's release notes.

1. Find the old version and the new version in `rust/harper-android/Cargo.toml`.
2. Visit the [Harper Releases Page](https://github.com/elijah-potter/harper/releases) to read the changelog between these versions.
3. Distill the changes into an Android-friendly format for the Google Play Store `WhatsNew` section.
   
   *Example:*
   > **Harper 1.2.0**
   > - Improved detection of subject-verb agreement.
   > - Added 2,000 new dictionary words.
   > - Fixed false positives in markdown blocks.

## Rollback Procedure for Bad Upgrades

If a bad `harper-core` upgrade causes production crashes, memory leaks, or an unacceptable rate of false-positive grammar suggestions, you must roll back quickly.

1. **Revert the Version**: Open `rust/harper-android/Cargo.toml` and change the `harper-core` dependency back to the last known-good exact version (e.g., from `harper-core = "=2.10.1"` back to `harper-core = "=2.10.0"`).
2. **Update the Lockfile**: Run `cargo update -p harper-core` in the `rust/harper-android` directory.
3. **Commit and Push**:
   ```bash
   git commit -am "fix: rollback harper-core to 2.10.0 due to [insert reason]"
   git push origin main
   ```
4. **Deploy a Hotfix**: GitHub Actions will automatically produce a new artifact. Draft a new release in the Play Console to override the bad version.
