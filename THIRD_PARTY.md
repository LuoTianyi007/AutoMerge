# Third-party components — AutoMerge 1.3

AutoMerge application code remains GPL-3.0-or-later. The FFmpeg dependency in this release is the LGPL minimal build; it is dynamically linked. Source inspection, replacement and rebuilding are permitted.

## FFmpegKit minimal Android 6.1.4

- Coordinate: `io.github.jamaismagic.ffmpeg:ffmpeg-kit-main-min-16kb:6.1.4`.
- Project: https://github.com/JamaisMagic/ffmpeg-kit-16KB
- Exact wrapper/build commit: `c7ca204219d549f563799bacbbdfeb19e31530f2`.
- CI: https://github.com/JamaisMagic/ffmpeg-kit-16KB/actions/runs/21397164611 — successful workflow, same head commit.
- Release: https://github.com/JamaisMagic/ffmpeg-kit-16KB/releases/tag/ffmpeg-kit-android-min-2026-01-27T12-56-32
- AAR SHA-256: `9f7bbf88628a8c0c5b30063e545c25d78183971eb088635ca11d1112950f3518`.
- The Maven AAR downloaded for this application matches the GitHub release asset's published SHA-256 exactly.
- License: LGPL-3.0-or-later. Native configuration enables `--enable-version3`, zlib and MediaCodec; it does not enable GPL components or external codec libraries.

## Corresponding sources

The complete, unfiltered upstream source archives are in `vendor/sources/`, with checksums and exact commits in `dependency-manifest.json`:

| Component | Upstream reference | Resolved source commit | License |
|---|---|---|---|
| FFmpegKit wrapper and build scripts | CI commit | c7ca204219d549f563799bacbbdfeb19e31530f2 | LGPL-3.0-or-later |
| arthenica/FFmpeg | n6.1.4 | 34277e12e80031c7f89494ba543684bc1dd0be8f | LGPL-2.1-or-later; build uses LGPL v3 |
| arthenica/cpu_features | v0.10.1 | d3b2440fcfc25fe8e6d0d4a85f06d68e98312f5b | Apache-2.0 |
| arthenica/gnu-config (build helper) | v20210814 | 805517123cbfe33d17c989a18e78c5789fab0437 | Upstream config script licenses/exceptions |
| tanersener/smart-exception | v0.2.1 | e77d0004790d39e5e57888ae8e7292f85711d12f | BSD-3-Clause |

Smart Exception Java and Common are both version 0.2.1. Their binary hashes and download URLs are pinned in the manifest. Android platform libraries are provided by the device; the APK includes no bundled libc++ library. Development tools are not part of the APK.

Release source archive: `AutoMerge-1.3-source.zip`, published alongside the APK. The same source archives are included in the repository. Build instructions and evidence are in `NATIVE_BUILD.md` and `provenance/`.

This release replaces the old untraceable 6.1.1 AAR. It does not retrospectively establish source correspondence for versions 1.0–1.2. Local checks establish hashes, source references, packaging and signatures; they do not claim a locally rebuilt, bit-identical native binary or a legal certification.
