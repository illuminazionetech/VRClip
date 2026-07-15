# Security Policy

## Scope

VRClip downloads content from user-supplied URLs and executes bundled third-party
binaries (`yt-dlp`, `ffmpeg`, `aria2c`) against that untrusted input, then stores
and plays back the resulting files locally. Security issues in scope include, but
aren't limited to:

- Path traversal or arbitrary file write via filename/output templates
- Command/argument injection into the bundled `yt-dlp`/`ffmpeg`/`aria2c` invocations
- Sandbox escapes or privilege escalation triggered by processing a malicious
  downloaded file
- Unsafe handling of cookies/credentials stored for network requests
- Vulnerabilities in the Meta Spatial SDK immersive player or the custom OpenGL
  360°/3D renderer that could be triggered by a malicious video file

General bugs in yt-dlp/ffmpeg/aria2c themselves should be reported upstream to
those projects, not here.

## Reporting a Vulnerability

Please report security issues privately using GitHub's "Report a vulnerability"
feature (Security tab → Advisories) on this repository, rather than opening a
public issue. Include:

- A description of the issue and its potential impact
- Steps to reproduce (a minimal repro is very helpful)
- The app version and platform (Android version or Quest model) you tested on

We aim to acknowledge reports within a reasonable timeframe and will credit
reporters in the release notes unless you prefer to stay anonymous.
