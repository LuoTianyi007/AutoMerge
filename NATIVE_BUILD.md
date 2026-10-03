# FFmpeg native dependency provenance and rebuild

## Verified chain

1. GitHub release tag `ffmpeg-kit-android-min-2026-01-27T12-56-32` points to commit `c7ca204219d549f563799bacbbdfeb19e31530f2` (saved `provenance/min-tag.json`).
2. Successful workflow run **21397164611**, started 2026-01-27T12:27:35Z, has the same `head_sha` (saved `provenance/min-runs.json`).
3. The workflow builds the main/min AAR from that checkout using **Ubuntu 24.04, JDK 17, Android NDK r27d**. Its command is:

   ```bash
   ./android.sh --disable-arm-v7a --enable-android-media-codec --enable-android-zlib
   ```

   The `arm-v7a-neon` variant remains enabled; the AAR contains `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`. AutoMerge packages only the two ARM ABIs.
4. The workflow assembles the release using its uploaded AAR artifacts. The release API's `digest` for `ffmpeg-kit-main-min-ndk-r27-16k.aar` is `sha256:9f7bbf88628a8c0c5b30063e545c25d78183971eb088635ca11d1112950f3518` (saved `provenance/upstream-release.json`).
5. The downloaded Maven 6.1.4 AAR has exactly that hash. AutoMerge's build script checks the hash before extracting it.
6. The pinned checkout's `scripts/source.sh` selects FFmpeg `n6.1.4`, CPU Features `v0.10.1` and GNU config `v20210814`. Full archives of those references, their resolved commit IDs and archive hashes accompany the release. The AAR's embedded native configuration also names NDK r27d and the minimal feature configuration. No `--enable-gpl` appears.

References:

- https://github.com/JamaisMagic/ffmpeg-kit-16KB/actions/runs/21397164611
- https://github.com/JamaisMagic/ffmpeg-kit-16KB/tree/c7ca204219d549f563799bacbbdfeb19e31530f2
- https://github.com/arthenica/FFmpeg/tree/34277e12e80031c7f89494ba543684bc1dd0be8f
- https://ffmpeg.org/legal.html

## Rebuild the native AAR

Use Linux/Ubuntu 24.04 with the upstream workflow's build prerequisites. The complete prerequisite commands are saved in `provenance/min-workflow.yml`, so they need not be reconstructed from a summary. Install JDK 17 and the Android command-line SDK, with `platforms;android-35` and NDK r27d. Official NDK archive: https://dl.google.com/android/repository/android-ndk-r27d-linux.zip.

Verify source archive hashes against `dependency-manifest.json`, then extract the FFmpegKit archive into an empty build directory, removing its single outer directory. Extract the dependency archives, removing each outer directory, into:

```text
ffmpeg-kit-build/src/ffmpeg/       <- ffmpeg-34277e...zip
ffmpeg-kit-build/src/cpu-features/ <- cpu-features-d3b244...zip
ffmpeg-kit-build/src/config/       <- gnu-config-805517...zip
```

The upstream source downloader uses populated `src/` directories rather than fetching replacements. Retain all upstream patch files and scripts; do not use stock FFmpeg in place of the archived arthenica fork.

In the root of the extracted FFmpegKit checkout:

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_ROOT=/path/to/android-ndk-r27d
export JAVA_HOME=/path/to/jdk-17
export ARG_ARTIFACT_NAME=ffmpeg-kit-main-min-16kb
export ARG_VERSIOM_NAME=6.1.4
export ARG_VERSIOM_CODE=60104
./android.sh --disable-arm-v7a --enable-android-media-codec --enable-android-zlib
find prebuilt -type f -name 'ffmpeg-kit.aar'
```

The variable spelling `VERSIOM` is upstream's spelling, preserved from the workflow. Maven publishing credentials are not needed to assemble the AAR; do not publish to upstream coordinates. Gradle/build-tool retrieval may require network access. The wrapper, all native source inputs, patches and original CI instructions are archived here. Toolchain packages remain available from their official publishers.

The build in this session consumed the hash-matched upstream CI AAR. It did **not** locally execute this Linux native rebuild, and does not promise byte-identical output from different hosts or toolchain installations. To deliberately consume a freshly rebuilt AAR, replace `ffmpeg.aar` and update the manifest hash after recording its own build evidence.
