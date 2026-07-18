(() => {
  "use strict";

  const REPO = "illuminazionetech/VRClip";
  const RELEASES_LATEST_API = `https://api.github.com/repos/${REPO}/releases/latest`;
  const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`;

  const ua = navigator.userAgent || "";
  const isQuest = /OculusBrowser|Quest\b/i.test(ua);
  const isAndroid = !isQuest && /Android/i.test(ua);
  const platform = isQuest ? "quest" : isAndroid ? "android" : "other";

  // ---- Tabs -----------------------------------------------------------
  const tabButtons = Array.from(document.querySelectorAll(".tab-btn"));
  const tabPanels = Array.from(document.querySelectorAll(".tab-panel"));

  function activateTab(name) {
    tabButtons.forEach((btn) => btn.classList.toggle("active", btn.dataset.tab === name));
    tabPanels.forEach((panel) => panel.classList.toggle("active", panel.dataset.tab === name));
  }

  tabButtons.forEach((btn) => {
    btn.addEventListener("click", () => activateTab(btn.dataset.tab));
  });

  activateTab(platform === "quest" ? "quest" : "android");

  // ---- Release / APK fetch --------------------------------------------
  const downloadBtn = document.getElementById("download-btn");
  // download-btn is a real <a> so the actual download always goes through the browser's native
  // link-following (the single most reliably supported way to trigger a download, more so than
  // any scripted click()). While we're still fetching the release, its href is "#" and disabled,
  // so block that placeholder click from jumping to the top of the page.
  downloadBtn.addEventListener("click", (event) => {
    if (downloadBtn.getAttribute("aria-disabled") === "true") event.preventDefault();
  });
  const downloadLabel = document.getElementById("download-btn-label");
  const downloadMeta = document.getElementById("download-meta");
  const otherArch = document.getElementById("other-arch");
  const archList = document.getElementById("arch-list");

  function formatSize(bytes) {
    if (!bytes) return "";
    const mb = bytes / (1024 * 1024);
    return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;
  }

  function friendlyArchLabel(name) {
    if (/universal/i.test(name)) return "Universal (all architectures)";
    if (/arm64-v8a/i.test(name)) return "ARM 64-bit (recommended)";
    if (/armeabi-v7a/i.test(name)) return "ARM 32-bit (older devices)";
    if (/x86_64/i.test(name)) return "x86_64 (Intel/AMD 64-bit)";
    if (/(?<!_64)x86(?!_64)/i.test(name)) return "x86 (Intel/AMD 32-bit)";
    if (/preview/i.test(name) || /githubPreview/i.test(name)) return "Preview";
    return name;
  }

  function pickRecommendedAsset(apkAssets) {
    const byPattern = (re) => apkAssets.find((a) => re.test(a.name));
    return (
      byPattern(/^app-generic-arm64-v8a-release\.apk$/i) ||
      byPattern(/generic.*arm64-v8a.*release\.apk$/i) ||
      byPattern(/generic.*universal.*release\.apk$/i) ||
      byPattern(/^app-generic-release\.apk$/i) ||
      byPattern(/generic.*release\.apk$/i) ||
      apkAssets[0]
    );
  }

  // Captured lazily the first time the button is actually clicked, when the label is still
  // guaranteed to be the real "Download VRClip x.y.z" text (not a stale "Download started" left
  // over from a previous click), and reused on every later click so rapid/repeated clicks can't
  // clobber it into permanently showing the transient confirmation text instead of reverting.
  let originalDownloadLabel = null;
  let confirmDownloadTimeoutId = null;

  function confirmDownloadStarted() {
    if (originalDownloadLabel === null) originalDownloadLabel = downloadLabel.textContent;
    window.clearTimeout(confirmDownloadTimeoutId);
    downloadBtn.classList.add("started");
    downloadLabel.textContent = "Download started";
    confirmDownloadTimeoutId = window.setTimeout(() => {
      downloadBtn.classList.remove("started");
      downloadLabel.textContent = originalDownloadLabel;
    }, 2500);
  }

  function setFallbackToReleasesPage(message) {
    downloadBtn.href = RELEASES_PAGE;
    downloadBtn.target = "_blank";
    downloadBtn.rel = "noopener";
    downloadBtn.removeAttribute("aria-disabled");
    downloadLabel.textContent = "Go to the releases page";
    downloadMeta.innerHTML = message;
  }

  async function loadLatestRelease() {
    let release;
    try {
      const res = await fetch(RELEASES_LATEST_API, {
        headers: { Accept: "application/vnd.github+json" },
      });
      if (!res.ok) throw new Error(`GitHub API ${res.status}`);
      release = await res.json();
    } catch (err) {
      setFallbackToReleasesPage(
        "Could not fetch the latest version automatically. " +
          `<a href="${RELEASES_PAGE}" target="_blank" rel="noopener">Open the releases page</a>.`
      );
      return;
    }

    const apkAssets = (release.assets || []).filter((a) => a.name.endsWith(".apk"));
    if (apkAssets.length === 0) {
      setFallbackToReleasesPage(
        `No APK found in ${release.tag_name || "the latest release"}. ` +
          `<a href="${RELEASES_PAGE}" target="_blank" rel="noopener">Open the releases page</a>.`
      );
      return;
    }

    const recommended = pickRecommendedAsset(apkAssets);
    const version = release.tag_name || release.name || "";

    downloadBtn.href = recommended.browser_download_url;
    downloadBtn.rel = "noopener";
    downloadBtn.removeAttribute("aria-disabled");
    downloadLabel.textContent = `Download VRClip ${version}`.trim();
    downloadMeta.innerHTML =
      `${friendlyArchLabel(recommended.name)} · ${formatSize(recommended.size)} · ` +
      `<a href="${recommended.browser_download_url}" rel="noopener">direct link</a> · ` +
      `<a href="${RELEASES_PAGE}" target="_blank" rel="noopener">all releases</a>`;
    downloadBtn.addEventListener("click", confirmDownloadStarted);

    const others = apkAssets.filter((a) => a.name !== recommended.name);
    if (others.length > 0) {
      archList.innerHTML = "";
      others
        .sort((a, b) => a.name.localeCompare(b.name))
        .forEach((asset) => {
          const a = document.createElement("a");
          a.href = asset.browser_download_url;
          a.rel = "noopener";
          a.innerHTML =
            `<span>${friendlyArchLabel(asset.name)}</span>` +
            `<span class="size">${formatSize(asset.size)}</span>`;
          archList.appendChild(a);
        });
      otherArch.hidden = false;
    }
  }

  loadLatestRelease();
})();
