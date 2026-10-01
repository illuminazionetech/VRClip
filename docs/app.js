(() => {
  "use strict";

  const REPO = "illuminazionetech/VRClip";
  const API_LATEST = `https://api.github.com/repos/${REPO}/releases/latest`;
  const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`;

  // ---- Italian texts (English is the text already in the page) ---------------------------
  const IT = {
    skip: "Vai al contenuto",
    "nav.features": "Funzioni",
    "nav.screens": "Schermate",
    "nav.news": "Novità",
    "nav.install": "Installazione",
    "nav.faq": "Domande",
    "hero.eyebrow": "Android · Meta Quest 2, 3, 3S, Pro",
    "hero.title": "Scaricalo. Guardalo in VR. Trasformalo in 3D.",
    "hero.lead":
      "VRClip salva video e audio da centinaia di siti e li riproduce come sono pensati per essere visti: 360° e 180° tutto intorno a te, 3D stereoscopico con la profondità vera, e i video piatti trasformati in 3D direttamente sul dispositivo.",
    "hero.guide": "Come installarlo",
    "dl.loading": "Scarica",
    "dl.other": "Altri file e architetture",
    "chip.free": "Gratuito e open source",
    "chip.noads": "Niente pubblicità né tracciamento",
    "chip.ondevice": "Conversione 3D sul dispositivo",
    "features.title": "Un'app sola, dal link al visore",
    "f1.t": "Download da centinaia di siti",
    "f1.d":
      "Incolla o condividi un link: yt-dlp sceglie i formati, aria2c può velocizzare il trasferimento, e copertina, capitoli e sottotitoli possono essere incorporati nel file.",
    "f2.t": "360°, 180° e 3D stereoscopico",
    "f2.d":
      "Il player riconosce la proiezione dai metadati del file, dal nome o dalla forma dell'immagine. Guardati intorno muovendo il telefono, usa un visore tipo Cardboard o gli occhiali rosso e ciano.",
    "f3.t": "Da 2D a 3D",
    "tag.new": "Novità",
    "f3.d":
      "Un modello di profondità che gira sul dispositivo costruisce il secondo occhio: in tempo reale mentre guardi, oppure come file 3D alla massima qualità che sostituisce quello piatto.",
    "f4.t": "Nativo su Meta Quest",
    "f4.d":
      "La libreria è un normale pannello; la riproduzione apre una scena immersiva costruita con Meta Spatial SDK, con uno schermo stereo vero, il passthrough e una barra di controllo che puoi spostare.",
    "f5.t": "Si aggiorna da solo, in sicurezza",
    "f5.d":
      "Le nuove versioni si installano dall'app, solo dopo aver verificato che checksum e firma corrispondono alla release ufficiale.",
    "f6.t": "Riservato per costruzione",
    "f6.d": "Niente account, statistiche o pubblicità. File e cronologia restano sul tuo dispositivo.",
    "screens.title": "Uno sguardo dentro",
    "screens.lead": "Schermate di VRClip 1.2 su telefono, tablet e Meta Quest.",
    "alt.queue": "Coda dei download con avanzamento e velocità",
    "cap.queue": "Coda dei download",
    "alt.library": "Libreria con i badge 360°, 180° 3D e 3D",
    "cap.library": "Libreria, con quello che hai già visto",
    "alt.player": "Controlli del player sopra un video",
    "cap.player": "Player: gesti, velocità, tracce, picture-in-picture",
    "alt.360": "Video a 360 gradi nel player",
    "cap.360": "Video 360°: muovi il telefono per guardarti intorno",
    "alt.3d": "Player con la conversione 2D→3D in tempo reale attiva",
    "cap.3d": "2D→3D mentre guardi",
    "alt.quest": "Libreria su Meta Quest con la barra di navigazione laterale",
    "cap.quest": "Libreria su Meta Quest",
    "alt.settings": "Impostazioni del player e della conversione da 2D a 3D",
    "cap.settings": "Impostazioni del player e del 3D",
    "alt.tablet": "Libreria su tablet",
    "cap.tablet": "Su tablet",
    "depth.title": "Come il 2D diventa 3D",
    "depth.p1":
      "VRClip stima quanto è lontano ogni punto dell'immagine con Depth Anything V2 Small, un modello di profondità che gira sulla GPU del telefono o del visore. Dall'immagine e dalla sua profondità disegna quello che vedrebbe ciascun occhio, riempiendo le zone nascoste dietro gli oggetti vicini con lo sfondo accanto.",
    "depth.p2":
      "Nel player la conversione avviene in tempo reale. Dalla libreria elabora ogni fotogramma alla massima qualità e, quando il nuovo file risulta completo, sostituisce il video piatto con uno 3D affiancato. Il modello si scarica una volta (circa 90 MB) e viene verificato prima dell'uso.",
    "depth.c1": "1. Il fotogramma piatto",
    "depth.c2": "2. La profondità stimata (chiaro è vicino)",
    "depth.c3": "3. Occhio sinistro e destro, affiancati",
    "depth.c4": "Lo stesso fotogramma come anaglifo rosso e ciano",
    "news.title": "Novità",
    "news.loading": "Caricamento delle note dell'ultima versione da GitHub…",
    "install.title": "Installa VRClip",
    "install.android": "Telefono o tablet Android",
    "install.quest": "Meta Quest",
    "a1.t": "Scarica l'APK",
    "a1.d":
      "Usa il pulsante di download in cima a questa pagina dal telefono: sceglie il file adatto al tuo dispositivo (arm64-v8a su quasi tutti i telefoni degli ultimi anni).",
    "a2.t": "Consenti l'installazione",
    "a2.d": "Android chiede una volta di consentire le installazioni dal browser. Conferma e torna al download.",
    "a3.t": "Apri il file e installa",
    "a3.d": "Apri l'.apk scaricato dalla notifica o dalla cartella Download e tocca Installa.",
    "a4.t": "Primo avvio",
    "a4.d":
      "Concedi a VRClip l'accesso all'archivio per salvare i download in Download/VRClip, e le notifiche per seguirne l'avanzamento. Da lì in poi l'app si aggiorna da sola.",
    "q1.t": "Attiva la modalità sviluppatore",
    "q1.d":
      "Le app che non vengono dal Meta Horizon Store richiedono la modalità sviluppatore. Crea un'organizzazione sviluppatore gratuita su developers.meta.com con l'account usato sul visore, poi nell'app Meta Horizon sul telefono apri Dispositivi, scegli il visore, Impostazioni del visore, Modalità sviluppatore, e attivala. Riavvia il visore.",
    "q2.t": "Scarica l'APK",
    "q2.d": "Da computer o telefono scarica da questa pagina l'APK universale o arm64-v8a (il pulsante propone quello giusto).",
    "q3.t": "Installalo sul visore",
    "q3.d":
      "Collega il visore al computer con un cavo USB-C, accetta \"Consenti debug USB\" dentro il visore, poi installa con SideQuest (trascina l'APK nella finestra) o da terminale con <code>adb install -r VRClip.apk</code>. Le versioni recenti di Horizon OS possono anche installare un APK aperto dall'app File del visore; se la tua lo propone, il cavo non serve.",
    "q4.t": "Apri VRClip",
    "q4.d":
      "Nel visore apri la libreria delle app e scegli il filtro Origini sconosciute (in alcune versioni \"App da origini sconosciute\"). VRClip si apre come pannello, come qualsiasi app di Horizon OS.",
    "q5.t": "Scarica e guarda",
    "q5.d":
      "Incolla un link, o condividilo con VRClip da un'altra app. Finito il download, aprilo dalla Libreria: il player mostra uno schermo grande davanti a te, oppure avvolge intorno a te i video 360° e 180°. La barra sotto il video ha riproduzione, avanzamento, 3D, proiezione, passthrough e ricentra.",
    "q6.t": "Trasforma un video piatto in 3D",
    "q6.d":
      "Premi 3D sulla barra per convertire mentre guardi, oppure apri i dettagli del video nella Libreria e scegli Converti in 3D per creare un file 3D permanente. La prima volta VRClip scarica il modello di profondità.",
    "q.note":
      "Indietro esce dal player e riporta la Libreria. La barra dei controlli si può afferrare e spostare; Ricentra rimette schermo e barra davanti a te.",
    "faq.title": "Domande",
    "faq1.q": "Ho VRClip 1.1 o precedente: perché l'aggiornamento non va?",
    "faq1.a":
      "Dalla 1.2 le versioni sono firmate con una chiave nuova e definitiva. Android non permette di aggiornare un'app firmata con una chiave usando un file firmato con un'altra, quindi il passaggio richiede una reinstallazione: se vuoi conservare la libreria esportala dal menu della Libreria (Esporta), disinstalla VRClip e installa la 1.2. I file in Download/VRClip non vengono toccati. Da lì in poi gli aggiornamenti si installano dall'app.",
    "faq2.q": "Su quali dispositivi funziona?",
    "faq2.a": "Android 9 o successivo su telefoni e tablet (ARM e x86), e Meta Quest 2, 3, 3S e Pro.",
    "faq3.q": "Dove sono i miei file?",
    "faq3.a": "In Download/VRClip sul dispositivo, o nella cartella che scegli nelle impostazioni.",
    "faq4.q": "Quanto dura una conversione in 3D?",
    "faq4.a":
      "Dipende dalla GPU del dispositivo, dalla durata e dalla risoluzione del video: elabora ogni fotogramma, quindi sulla maggior parte dei telefoni impiega più della durata del video. Continua anche a schermo spento e mostra l'avanzamento in una notifica; l'originale resta intatto finché il file 3D non è completo.",
    "faq5.q": "VRClip invia dati da qualche parte?",
    "faq5.a":
      "No. Si collega solo ai siti da cui scarichi, a GitHub per gli aggiornamenti e, se usi il 2D→3D, una volta al server pubblico di Qualcomm AI Hub per scaricare il modello di profondità. Leggi l'<a href=\"privacy.html\">informativa sulla privacy</a>.",
    "faq6.q": "Si può scaricare da qualsiasi sito?",
    "faq6.a": "VRClip è uno strumento: rispetta i termini dei siti che usi e il diritto d'autore di ciò che scarichi.",
    "foot.code": "Codice sorgente",
    "foot.releases": "Tutte le versioni",
    "foot.issues": "Segnala un problema",
    "foot.privacy": "Privacy",
    "foot.license": "Licenza GPLv3",
    "foot.credits":
      "VRClip deriva da <a href=\"https://github.com/JunkFood02/Seal\">Seal</a> di JunkFood02 e usa <a href=\"https://github.com/yt-dlp/yt-dlp\">yt-dlp</a>, FFmpeg, aria2 e <a href=\"https://github.com/DepthAnything/Depth-Anything-V2\">Depth Anything V2</a>. Non è affiliato a Meta.",
  };

  // Texts produced by the script, in both languages.
  const DYN = {
    en: {
      download: (v) => `Download VRClip ${v}`.trim(),
      downloadQuest: (v) => `Download VRClip ${v} for Quest`.trim(),
      releasesPage: "Open the releases page",
      fetchFailed: "Could not reach GitHub to find the latest version.",
      noApk: "The latest release has no APK yet.",
      direct: "direct link",
      allReleases: "all releases",
      started: "Download started",
      published: (d) => `Published ${d}`,
      notesEnglish: "",
      notesEmpty: "This release has no notes.",
      seeOnGithub: "Read it on GitHub",
      arch: {
        universal: "Universal (every device)",
        arm64: "ARM 64-bit (recommended)",
        arm32: "ARM 32-bit (older devices)",
        x86_64: "x86_64 (Intel/AMD 64-bit)",
        x86: "x86 (Intel/AMD 32-bit)",
      },
      preview: "Preview channel",
    },
    it: {
      download: (v) => `Scarica VRClip ${v}`.trim(),
      downloadQuest: (v) => `Scarica VRClip ${v} per Quest`.trim(),
      releasesPage: "Apri la pagina delle versioni",
      fetchFailed: "Non è stato possibile raggiungere GitHub per trovare l'ultima versione.",
      noApk: "L'ultima versione non ha ancora un APK.",
      direct: "link diretto",
      allReleases: "tutte le versioni",
      started: "Download avviato",
      published: (d) => `Pubblicata il ${d}`,
      notesEnglish: "Le note di rilascio sono in inglese.",
      notesEmpty: "Questa versione non ha note.",
      seeOnGithub: "Leggile su GitHub",
      arch: {
        universal: "Universale (ogni dispositivo)",
        arm64: "ARM 64 bit (consigliato)",
        arm32: "ARM 32 bit (dispositivi meno recenti)",
        x86_64: "x86_64 (Intel/AMD 64 bit)",
        x86: "x86 (Intel/AMD 32 bit)",
      },
      preview: "Canale di anteprima",
    },
  };

  const ua = navigator.userAgent || "";
  const isQuest = /OculusBrowser|Quest/i.test(ua);
  const isAndroid = !isQuest && /Android/i.test(ua);

  // ---- Language ---------------------------------------------------------------------------
  const storage = {
    get(key) {
      try {
        return window.localStorage.getItem(key);
      } catch (e) {
        return null;
      }
    },
    set(key, value) {
      try {
        window.localStorage.setItem(key, value);
      } catch (e) {
        /* private mode: the choice just is not remembered */
      }
    },
  };

  function detectLanguage() {
    const fromUrl = new URLSearchParams(location.search).get("lang");
    if (fromUrl === "it" || fromUrl === "en") return fromUrl;
    const saved = storage.get("vrclip-lang");
    if (saved === "it" || saved === "en") return saved;
    const preferred = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || "en"];
    for (const tag of preferred) {
      const code = String(tag).toLowerCase().slice(0, 2);
      if (code === "it") return "it";
      if (code === "en") return "en";
    }
    return "en";
  }

  const original = new Map();
  document.querySelectorAll("[data-i18n],[data-i18n-html],[data-i18n-alt]").forEach((el) => {
    original.set(el, { text: el.textContent, html: el.innerHTML, alt: el.getAttribute("alt") });
  });

  let lang = detectLanguage();

  function applyLanguage(next, remember) {
    lang = next;
    document.documentElement.lang = next;
    original.forEach((value, el) => {
      const key = el.dataset.i18n || el.dataset.i18nHtml || el.dataset.i18nAlt;
      const it = next === "it" ? IT[key] : undefined;
      if (el.dataset.i18nAlt) {
        el.setAttribute("alt", it !== undefined ? it : value.alt);
      } else if (el.dataset.i18nHtml) {
        el.innerHTML = it !== undefined ? it : value.html;
      } else {
        el.textContent = it !== undefined ? it : value.text;
      }
    });
    document.querySelectorAll(".lang-switch button").forEach((b) => {
      b.setAttribute("aria-pressed", String(b.dataset.lang === next));
    });
    document.title =
      next === "it" ? "VRClip: scarica, guarda in VR, trasforma il 2D in 3D" : "VRClip: download, watch in VR, turn 2D into 3D";
    if (remember) storage.set("vrclip-lang", next);
    renderDownload();
    renderRelease();
  }

  document.querySelectorAll(".lang-switch button").forEach((b) => {
    b.addEventListener("click", () => applyLanguage(b.dataset.lang, true));
  });

  // ---- Tabs ---------------------------------------------------------------------------------
  const tabs = Array.from(document.querySelectorAll('[role="tab"]'));
  const panels = Array.from(document.querySelectorAll('[role="tabpanel"]'));

  function selectTab(name, focus) {
    tabs.forEach((t) => {
      const on = t.dataset.tab === name;
      t.setAttribute("aria-selected", String(on));
      t.tabIndex = on ? 0 : -1;
      if (on && focus) t.focus();
    });
    panels.forEach((p) => {
      p.hidden = p.dataset.tab !== name;
    });
  }

  tabs.forEach((t, i) => {
    t.addEventListener("click", () => selectTab(t.dataset.tab, false));
    t.addEventListener("keydown", (e) => {
      if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
      const next = tabs[(i + (e.key === "ArrowRight" ? 1 : tabs.length - 1)) % tabs.length];
      selectTab(next.dataset.tab, true);
      e.preventDefault();
    });
  });
  selectTab(isQuest ? "quest" : "android", false);

  // ---- Latest release -------------------------------------------------------------------------
  let release = null;
  let releaseError = false;

  const dlBtn = document.getElementById("download-btn");
  const dlLabel = document.getElementById("download-label");
  const dlMeta = document.getElementById("download-meta");
  const otherBox = document.getElementById("other-files");
  const otherList = document.getElementById("other-list");
  const releaseBox = document.getElementById("release");

  function formatSize(bytes) {
    if (!bytes) return "";
    const mb = bytes / (1024 * 1024);
    return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;
  }

  function archLabel(name) {
    const t = DYN[lang];
    const preview = /githubPreview/i.test(name) ? ` · ${t.preview}` : "";
    let label = name;
    if (/universal/i.test(name)) label = t.arch.universal;
    else if (/arm64-v8a/i.test(name)) label = t.arch.arm64;
    else if (/armeabi-v7a/i.test(name)) label = t.arch.arm32;
    else if (/x86_64/i.test(name)) label = t.arch.x86_64;
    else if (/x86/i.test(name)) label = t.arch.x86;
    return label + preview;
  }

  /** The stable ("generic") build for this device; Quest and phones are ARM 64-bit. */
  function recommendedAsset(apks) {
    const generic = apks.filter((a) => /-generic-/i.test(a.name));
    const pool = generic.length ? generic : apks;
    const find = (re) => pool.find((a) => re.test(a.name));
    if (isQuest || isAndroid) return find(/arm64-v8a/i) || find(/universal/i) || pool[0];
    // On a computer we cannot know the target device: the universal APK always works.
    return find(/universal/i) || find(/arm64-v8a/i) || pool[0];
  }

  function link(href, text) {
    const a = document.createElement("a");
    a.href = href;
    a.rel = "noopener";
    a.textContent = text;
    return a;
  }

  function renderDownload() {
    const t = DYN[lang];
    dlMeta.textContent = "";
    if (releaseError || !release) {
      if (releaseError) {
        dlLabel.textContent = t.releasesPage;
        dlBtn.href = RELEASES_PAGE;
        dlMeta.append(t.fetchFailed);
      }
      return;
    }
    const apks = (release.assets || []).filter((a) => /\.apk$/i.test(a.name));
    const version = release.tag_name || "";
    if (!apks.length) {
      dlLabel.textContent = t.releasesPage;
      dlBtn.href = RELEASES_PAGE;
      dlMeta.append(t.noApk);
      return;
    }
    const best = recommendedAsset(apks);
    dlBtn.href = best.browser_download_url;
    dlLabel.textContent = isQuest ? t.downloadQuest(version) : t.download(version);
    dlMeta.append(`${archLabel(best.name)} · ${formatSize(best.size)} · `);
    dlMeta.append(link(best.browser_download_url, t.direct), " · ", link(RELEASES_PAGE, t.allReleases));

    const others = apks.filter((a) => a !== best).sort((a, b) => a.name.localeCompare(b.name));
    otherList.textContent = "";
    others.forEach((a) => {
      const li = document.createElement("li");
      const anchor = link(a.browser_download_url, archLabel(a.name));
      const size = document.createElement("span");
      size.className = "size";
      size.textContent = formatSize(a.size);
      li.append(anchor, size);
      otherList.append(li);
    });
    otherBox.hidden = others.length === 0;
  }

  dlBtn.addEventListener("click", () => {
    if (!release || releaseError) return;
    const label = dlLabel.textContent;
    dlBtn.classList.add("started");
    dlLabel.textContent = DYN[lang].started;
    window.setTimeout(() => {
      dlBtn.classList.remove("started");
      if (dlLabel.textContent === DYN[lang].started) dlLabel.textContent = label;
    }, 2500);
  });

  // ---- Release notes (GitHub markdown, rendered as text: nothing from the API becomes markup) --
  function inline(parent, text) {
    // **bold**, `code`, [label](https://url) and bare https links.
    const re = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\(https?:\/\/[^)\s]+\)|https?:\/\/[^\s)]+)/g;
    let last = 0;
    let m;
    while ((m = re.exec(text))) {
      if (m.index > last) parent.append(text.slice(last, m.index));
      const token = m[0];
      if (token.startsWith("**")) {
        const b = document.createElement("strong");
        b.textContent = token.slice(2, -2);
        parent.append(b);
      } else if (token.startsWith("`")) {
        const c = document.createElement("code");
        c.textContent = token.slice(1, -1);
        parent.append(c);
      } else if (token.startsWith("[")) {
        const label = token.slice(1, token.indexOf("]("));
        const url = token.slice(token.indexOf("](") + 2, -1);
        parent.append(link(url, label));
      } else {
        // GitHub's generated notes end lines with "by @user in https://.../pull/N".
        const pr = token.match(/\/pull\/(\d+)$/);
        parent.append(link(token, pr ? `#${pr[1]}` : token));
      }
      last = re.lastIndex;
    }
    if (last < text.length) parent.append(text.slice(last));
  }

  function renderMarkdown(markdown, container) {
    let list = null;
    markdown
      .replace(/\r/g, "")
      .split("\n")
      .forEach((raw) => {
        const line = raw.trimEnd();
        if (!line.trim()) {
          list = null;
          return;
        }
        const heading = line.match(/^(#{1,4})\s+(.*)$/);
        const item = line.match(/^\s*[-*]\s+(.*)$/);
        if (heading) {
          list = null;
          const h = document.createElement(heading[1].length <= 2 ? "h4" : "h5");
          inline(h, heading[2]);
          container.append(h);
        } else if (item) {
          if (!list) {
            list = document.createElement("ul");
            container.append(list);
          }
          const li = document.createElement("li");
          inline(li, item[1]);
          list.append(li);
        } else {
          list = null;
          const p = document.createElement("p");
          inline(p, line);
          container.append(p);
        }
      });
  }

  function renderRelease() {
    const t = DYN[lang];
    releaseBox.textContent = "";
    if (releaseError) {
      const p = document.createElement("p");
      p.className = "muted";
      p.append(t.fetchFailed, " ", link(RELEASES_PAGE, t.releasesPage));
      releaseBox.append(p);
      return;
    }
    if (!release) {
      const p = document.createElement("p");
      p.className = "muted";
      p.textContent = lang === "it" ? IT["news.loading"] : "Loading the latest release notes from GitHub…";
      releaseBox.append(p);
      return;
    }
    const head = document.createElement("div");
    head.className = "release-head";
    const title = document.createElement("h3");
    title.textContent = release.name || release.tag_name;
    const date = document.createElement("p");
    date.className = "muted";
    const published = release.published_at ? new Date(release.published_at) : null;
    if (published) {
      date.textContent = t.published(
        published.toLocaleDateString(lang === "it" ? "it-IT" : "en-US", { day: "numeric", month: "long", year: "numeric" })
      );
    }
    head.append(title, date);
    releaseBox.append(head);
    if (t.notesEnglish) {
      const note = document.createElement("p");
      note.className = "muted small";
      note.textContent = t.notesEnglish;
      releaseBox.append(note);
    }
    const body = document.createElement("div");
    body.className = "release-body";
    const text = (release.body || "").trim();
    if (text) renderMarkdown(text, body);
    else body.textContent = t.notesEmpty;
    releaseBox.append(body);
    const more = document.createElement("p");
    more.append(link(release.html_url || RELEASES_PAGE, t.seeOnGithub));
    releaseBox.append(more);
  }

  async function loadRelease() {
    try {
      const res = await fetch(API_LATEST, { headers: { Accept: "application/vnd.github+json" } });
      if (!res.ok) throw new Error(`GitHub ${res.status}`);
      release = await res.json();
    } catch (e) {
      releaseError = true;
    }
    renderDownload();
    renderRelease();
  }

  applyLanguage(lang, false);
  loadRelease();
})();
