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
    if (/universal/i.test(name)) return "Universale (tutte le architetture)";
    if (/arm64-v8a/i.test(name)) return "ARM 64-bit (consigliato)";
    if (/armeabi-v7a/i.test(name)) return "ARM 32-bit (dispositivi datati)";
    if (/x86_64/i.test(name)) return "x86_64 (Intel/AMD 64-bit)";
    if (/(?<!_64)x86(?!_64)/i.test(name)) return "x86 (Intel/AMD 32-bit)";
    if (/preview/i.test(name) || /githubPreview/i.test(name)) return "Anteprima";
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

  function triggerDownload(url) {
    // Deliberately not `window.location.href = url`: reassigning the current document's
    // location to a large binary (70-180MB release APK) makes the tab's own loading spinner
    // spin for the whole transfer on several mobile/Quest browsers, since the browser treats it
    // as an in-flight navigation until the attachment headers redirect it into download mode —
    // it looks stuck even though the file is downloading fine in the background. A detached
    // anchor click starts the same download without touching the page's navigation state.
    const a = document.createElement("a");
    a.href = url;
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  function confirmDownloadStarted() {
    const originalLabel = downloadLabel.textContent;
    downloadBtn.classList.add("started");
    downloadLabel.textContent = "Download avviato ✓";
    window.setTimeout(() => {
      downloadBtn.classList.remove("started");
      downloadLabel.textContent = originalLabel;
    }, 2500);
  }

  function setFallbackToReleasesPage(message) {
    downloadBtn.disabled = false;
    downloadLabel.textContent = "Vai alla pagina delle release";
    downloadMeta.innerHTML = message;
    downloadBtn.onclick = () => window.open(RELEASES_PAGE, "_blank", "noopener");
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
        "Impossibile recuperare automaticamente l'ultima versione. " +
          `<a href="${RELEASES_PAGE}" target="_blank" rel="noopener">Apri la pagina delle release</a>.`
      );
      return;
    }

    const apkAssets = (release.assets || []).filter((a) => a.name.endsWith(".apk"));
    if (apkAssets.length === 0) {
      setFallbackToReleasesPage(
        `Nessun APK trovato in ${release.tag_name || "l'ultima release"}. ` +
          `<a href="${RELEASES_PAGE}" target="_blank" rel="noopener">Apri la pagina delle release</a>.`
      );
      return;
    }

    const recommended = pickRecommendedAsset(apkAssets);
    const version = release.tag_name || release.name || "";

    downloadBtn.disabled = false;
    downloadLabel.textContent = `Scarica VRClip ${version}`.trim();
    downloadMeta.innerHTML =
      `${friendlyArchLabel(recommended.name)} · ${formatSize(recommended.size)} — ` +
      `<a href="${RELEASES_PAGE}" target="_blank" rel="noopener">tutte le release</a>`;
    downloadBtn.onclick = () => {
      triggerDownload(recommended.browser_download_url);
      confirmDownloadStarted();
    };

    const others = apkAssets.filter((a) => a.name !== recommended.name);
    if (others.length > 0) {
      archList.innerHTML = "";
      others
        .sort((a, b) => a.name.localeCompare(b.name))
        .forEach((asset) => {
          const a = document.createElement("a");
          a.href = asset.browser_download_url;
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
