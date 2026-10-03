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
    "nav.label": "Principale",
    "nav.menu": "Menu",
    "lang.label": "Lingua",
    "theme.dark": "Tema scuro",
    "theme.title": "Tema",
    "theme.system": "Sistema",
    "theme.light": "Chiaro",
    "theme.darkShort": "Scuro",
    "hero.eyebrow": "Android · Meta Quest 2, 3, 3S, Pro",
    "hero.title":
      '<span class="line">Scaricalo.</span> <span class="line">Guardalo in VR.</span> <span class="line grad">Trasformalo in 3D.</span>',
    "hero.lead":
      "VRClip salva video e audio da centinaia di siti e li riproduce come sono pensati per essere visti: 360° e 180° tutto intorno a te, 3D stereoscopico con la profondità vera, e i video piatti trasformati in 3D direttamente sul dispositivo.",
    "hero.guide": "Come installarlo",
    "dl.loading": "Scarica",
    "dl.other": "Altri file e architetture",
    "chip.free": "Gratuito e open source",
    "chip.noads": "Niente pubblicità né tracciamento",
    "chip.ondevice": "Conversione 3D sul dispositivo",
    "mq.1": "Centinaia di siti",
    "mq.2": "360° e 180°",
    "mq.3": "3D affiancato",
    "mq.4": "3D sopra e sotto",
    "mq.5": "Anaglifo rosso e ciano",
    "mq.6": "Visori tipo Cardboard",
    "mq.7": "Passthrough",
    "mq.8": "Picture-in-picture",
    "mq.9": "Sottotitoli e capitoli",
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
    "screens.lead": "VRClip su telefono, tablet e Meta Quest. Seleziona una schermata per vederla a tutto schermo.",
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
    "gal.prev": "Precedente",
    "gal.next": "Successiva",
    "gal.close": "Chiudi",
    "depth.title": "Come il 2D diventa 3D",
    "depth.p1":
      "VRClip stima quanto è lontano ogni punto dell'immagine con Depth Anything V2 Small, un modello di profondità che gira sulla GPU del telefono o del visore. Dall'immagine e dalla sua profondità disegna quello che vedrebbe ciascun occhio, riempiendo le zone nascoste dietro gli oggetti vicini con lo sfondo accanto.",
    "depth.p2":
      "Nel player la conversione avviene in tempo reale. Dalla libreria elabora ogni fotogramma alla massima qualità e, quando il nuovo file risulta completo, sostituisce il video piatto con uno 3D affiancato. I modelli si scaricano una volta (circa 100 MB) e vengono verificati prima dell'uso.",
    "depth.c1": "1. Il fotogramma piatto",
    "depth.c2": "2. La profondità stimata (chiaro è vicino)",
    "depth.c3": "3. Occhio sinistro e destro, affiancati",
    "depth.c4": "Lo stesso fotogramma come anaglifo rosso e ciano",
    "depth.c0": "Trascina sull'immagine, o guarda e basta: ogni punto si sposta in base alla sua profondità.",
    "depth.alt": "Il fotogramma di esempio che si muove in 3D secondo la sua profondità",
    "depth.modes": "Vista",
    "depth.m.live": "Vista 3D",
    "depth.m.source": "Originale",
    "depth.m.depth": "Profondità",
    "depth.m.sbs": "Affiancato",
    "depth.m.ana": "Anaglifo",
    "news.title": "Novità",
    "news.loading": "Caricamento delle note dell'ultima versione da GitHub…",
    "install.title": "Installa VRClip",
    "install.android": "Telefono o tablet Android",
    "install.quest": "Meta Quest",
    "install.device": "Dispositivo",
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
    "faq.lead": 'Altro? <a href="https://github.com/illuminazionetech/VRClip/issues">Apri una segnalazione su GitHub</a>.',
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
      "No. Si collega solo ai siti da cui scarichi, a GitHub per gli aggiornamenti e, se usi il 2D→3D, una volta a GitHub per scaricare i modelli di profondità. Leggi l'<a href=\"privacy.html\">informativa sulla privacy</a>.",
    "faq6.q": "Si può scaricare da qualsiasi sito?",
    "faq6.a": "VRClip è uno strumento: rispetta i termini dei siti che usi e il diritto d'autore di ciò che scarichi.",
    "cta.title": "Portalo nel visore",
    "cta.lead": "Gratuito, open source e senza pubblicità, per telefoni e tablet Android e per Meta Quest.",
    "foot.tag": "Scarica, guarda, passa al 3D.",
    "foot.label": "Collegamenti",
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
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");

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

  // English is the text already in the page; it is kept here to switch back.
  const original = new Map();
  document.querySelectorAll("[data-i18n],[data-i18n-html],[data-i18n-alt],[data-i18n-label]").forEach((el) => {
    original.set(el, {
      text: el.textContent,
      html: el.innerHTML,
      alt: el.getAttribute("alt"),
      label: el.getAttribute("aria-label"),
    });
  });

  let lang = detectLanguage();
  const languageListeners = [];

  function applyLanguage(next, remember) {
    lang = next;
    document.documentElement.lang = next;
    const tr = (key, fallback) => (next === "it" && IT[key] !== undefined ? IT[key] : fallback);
    original.forEach((value, el) => {
      const d = el.dataset;
      if (d.i18nHtml) el.innerHTML = tr(d.i18nHtml, value.html);
      else if (d.i18n) el.textContent = tr(d.i18n, value.text);
      if (d.i18nAlt) el.setAttribute("alt", tr(d.i18nAlt, value.alt));
      if (d.i18nLabel) el.setAttribute("aria-label", tr(d.i18nLabel, value.label));
    });
    document.querySelectorAll(".lang-switch button").forEach((b) => {
      b.setAttribute("aria-pressed", String(b.dataset.lang === next));
    });
    document.title =
      next === "it" ? "VRClip: scarica, guarda in VR, trasforma il 2D in 3D" : "VRClip: download, watch in VR, turn 2D into 3D";
    if (remember) storage.set("vrclip-lang", next);
    renderDownload();
    renderRelease();
    languageListeners.forEach((listener) => listener());
  }

  document.querySelectorAll(".lang-switch button").forEach((b) => {
    b.addEventListener("click", () => applyLanguage(b.dataset.lang, true));
  });

  // ---- Top bar: menu, scroll state, reading progress, current section -------------------------
  const bar = document.getElementById("top-bar");
  const menuBtn = bar.querySelector(".menu-btn");
  const navLinks = Array.from(bar.querySelectorAll(".nav a"));

  function setMenu(open) {
    bar.classList.toggle("open", open);
    menuBtn.setAttribute("aria-expanded", String(open));
  }
  menuBtn.addEventListener("click", () => setMenu(!bar.classList.contains("open")));
  navLinks.forEach((a) => a.addEventListener("click", () => setMenu(false)));
  document.addEventListener("click", (e) => {
    if (bar.classList.contains("open") && !bar.contains(e.target)) setMenu(false);
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && bar.classList.contains("open")) {
      setMenu(false);
      menuBtn.focus();
    }
  });

  const heroArt = document.querySelector(".hero-art");
  let scrollQueued = false;
  function onScroll() {
    if (scrollQueued) return;
    scrollQueued = true;
    window.requestAnimationFrame(() => {
      scrollQueued = false;
      const y = window.scrollY;
      const max = document.documentElement.scrollHeight - window.innerHeight;
      bar.classList.toggle("scrolled", y > 8);
      bar.style.setProperty("--progress", max > 0 ? Math.min(1, y / max).toFixed(4) : "0");
      if (heroArt && y < window.innerHeight * 1.5) heroArt.style.setProperty("--sy", String(Math.round(y)));
    });
  }
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onScroll, { passive: true });
  onScroll();

  if ("IntersectionObserver" in window) {
    const byId = new Map(navLinks.map((a) => [a.getAttribute("href").slice(1), a]));
    const spy = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const link = byId.get(entry.target.id);
          if (!link) return;
          if (entry.isIntersecting) {
            navLinks.forEach((a) => a.removeAttribute("aria-current"));
            link.setAttribute("aria-current", "true");
          } else if (link.getAttribute("aria-current")) {
            link.removeAttribute("aria-current");
          }
        });
      },
      { rootMargin: "-45% 0px -50% 0px" }
    );
    byId.forEach((_, id) => {
      const section = document.getElementById(id);
      if (section) spy.observe(section);
    });
  }

  // ---- Reveal on scroll -----------------------------------------------------------------------
  const reveals = Array.from(document.querySelectorAll(".reveal"));
  if ("IntersectionObserver" in window) {
    const revealer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("in");
          revealer.unobserve(entry.target);
        });
      },
      { rootMargin: "0px 0px -8% 0px" }
    );
    reveals.forEach((el) => revealer.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add("in"));
  }

  // ---- Hero: the devices follow the pointer -----------------------------------------------------
  const hero = document.querySelector(".hero");
  const rig = document.querySelector(".hero-art .rig");
  if (hero && rig && finePointer.matches && !reduceMotion.matches) {
    hero.addEventListener("pointermove", (e) => {
      const r = hero.getBoundingClientRect();
      rig.style.setProperty("--tx", ((e.clientX - r.left) / r.width - 0.5).toFixed(3));
      rig.style.setProperty("--ty", ((e.clientY - r.top) / r.height - 0.5).toFixed(3));
    });
    hero.addEventListener("pointerleave", () => {
      rig.style.setProperty("--tx", "0");
      rig.style.setProperty("--ty", "0");
    });
  }

  // ---- Features: a light that follows the pointer across the tiles ------------------------------
  const bento = document.getElementById("bento");
  if (bento && finePointer.matches) {
    const tiles = Array.from(bento.querySelectorAll(".tile"));
    bento.addEventListener("pointermove", (e) => {
      tiles.forEach((tile) => {
        const r = tile.getBoundingClientRect();
        tile.style.setProperty("--mx", `${Math.round(e.clientX - r.left)}px`);
        tile.style.setProperty("--my", `${Math.round(e.clientY - r.top)}px`);
      });
    });
  }

  // ---- Screenshots: carousel with arrows, mouse drag and a full-size view -----------------------
  const gallery = document.getElementById("gallery");
  const galleryPrev = document.getElementById("gallery-prev");
  const galleryNext = document.getElementById("gallery-next");
  const shots = Array.from(gallery.querySelectorAll(".shot"));

  function updateArrows() {
    const max = gallery.scrollWidth - gallery.clientWidth;
    galleryPrev.disabled = gallery.scrollLeft <= 4;
    galleryNext.disabled = gallery.scrollLeft >= max - 4;
  }
  function scrollGallery(direction) {
    const step = Math.max(gallery.clientWidth * 0.72, 260);
    gallery.scrollBy({ left: direction * step, behavior: reduceMotion.matches ? "auto" : "smooth" });
  }
  galleryPrev.addEventListener("click", () => scrollGallery(-1));
  galleryNext.addEventListener("click", () => scrollGallery(1));
  gallery.addEventListener("scroll", () => window.requestAnimationFrame(updateArrows), { passive: true });
  if ("ResizeObserver" in window) new ResizeObserver(updateArrows).observe(gallery);
  updateArrows();

  let drag = null;
  let swallowClick = false;
  gallery.addEventListener("pointerdown", (e) => {
    if (e.pointerType !== "mouse" || e.button !== 0) return;
    drag = { x: e.clientX, left: gallery.scrollLeft, moved: false };
  });
  window.addEventListener("pointermove", (e) => {
    if (!drag) return;
    const dx = e.clientX - drag.x;
    if (!drag.moved && Math.abs(dx) > 6) {
      drag.moved = true;
      gallery.classList.add("dragging");
    }
    if (drag.moved) gallery.scrollLeft = drag.left - dx;
  });
  window.addEventListener("pointerup", () => {
    if (!drag) return;
    if (drag.moved) {
      swallowClick = true;
      window.setTimeout(() => (swallowClick = false), 0);
    }
    drag = null;
    gallery.classList.remove("dragging");
  });
  gallery.addEventListener(
    "click",
    (e) => {
      if (!swallowClick) return;
      e.preventDefault();
      e.stopPropagation();
    },
    true
  );
  gallery.addEventListener("dragstart", (e) => e.preventDefault());

  const lightbox = document.getElementById("lightbox");
  const lightboxImg = document.getElementById("lightbox-img");
  const lightboxCap = document.getElementById("lightbox-cap");
  let shown = -1;

  function showShot(index) {
    shown = (index + shots.length) % shots.length;
    const shot = shots[shown];
    const img = shot.querySelector("img");
    lightboxImg.src = img.currentSrc || img.src;
    lightboxImg.alt = img.alt;
    lightboxImg.setAttribute("width", img.getAttribute("width"));
    lightboxImg.setAttribute("height", img.getAttribute("height"));
    lightboxCap.textContent = shot.querySelector("figcaption").textContent;
    lightbox.classList.toggle("tall", shot.classList.contains("tall"));
  }

  shots.forEach((shot, i) => {
    shot.querySelector(".shot-open").addEventListener("click", () => {
      const img = shot.querySelector("img");
      if (typeof lightbox.showModal !== "function") {
        window.open(img.currentSrc || img.src, "_blank", "noopener");
        return;
      }
      showShot(i);
      lightbox.showModal();
    });
  });
  lightbox.querySelector(".lb-close").addEventListener("click", () => lightbox.close());
  lightbox.querySelector(".lb-prev").addEventListener("click", () => showShot(shown - 1));
  lightbox.querySelector(".lb-next").addEventListener("click", () => showShot(shown + 1));
  lightbox.addEventListener("click", (e) => {
    if (e.target === lightbox) lightbox.close();
  });
  lightbox.addEventListener("keydown", (e) => {
    if (e.key === "ArrowLeft") showShot(shown - 1);
    else if (e.key === "ArrowRight") showShot(shown + 1);
  });
  lightbox.addEventListener("close", () => {
    const opener = shots[shown] && shots[shown].querySelector(".shot-open");
    if (opener) {
      opener.focus({ preventScroll: true });
      opener.scrollIntoView({ block: "nearest", inline: "nearest" });
    }
  });
  languageListeners.push(() => {
    if (lightbox.open) showShot(shown);
  });

  // ---- 2D to 3D demo: the sample frame displaced by its depth map, in WebGL ---------------------
  function createDepthRenderer(canvas, sourceUrl, depthUrl, onFail) {
    let gl = null;
    try {
      gl = canvas.getContext("webgl", { alpha: false, antialias: false, depth: false, stencil: false, powerPreference: "low-power" });
    } catch (e) {
      gl = null;
    }
    if (!gl) return null;

    const vertex = "attribute vec2 p;varying vec2 v;void main(){v=vec2(p.x*.5+.5,.5-p.y*.5);gl_Position=vec4(p,0.,1.);}";
    // Light is near in the depth map. Each pixel looks up where its color came from, refining the
    // guess a few times, so near objects slide over the background instead of smearing it.
    const fragment = [
      "precision mediump float;",
      "varying vec2 v;",
      "uniform sampler2D img;",
      "uniform sampler2D dep;",
      "uniform vec2 off;",
      "uniform vec2 cover;",
      "float depth(vec2 t){return smoothstep(.08,.85,dot(texture2D(dep,t).rgb,vec3(.299,.587,.114)));}",
      "void main(){",
      "  vec2 b=(v-.5)*cover+.5;",
      "  vec2 t=b;",
      "  for(int i=0;i<5;i++){t=b-off*(depth(t)-.35);}",
      "  gl_FragColor=texture2D(img,clamp(t,0.,1.));",
      "}",
    ].join("\n");

    function shader(type, source) {
      const s = gl.createShader(type);
      gl.shaderSource(s, source);
      gl.compileShader(s);
      return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null;
    }
    const vs = shader(gl.VERTEX_SHADER, vertex);
    const fs = shader(gl.FRAGMENT_SHADER, fragment);
    if (!vs || !fs) return null;
    const program = gl.createProgram();
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return null;
    gl.useProgram(program);

    const quad = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, quad);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const position = gl.getAttribLocation(program, "p");
    gl.enableVertexAttribArray(position);
    gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
    const uOff = gl.getUniformLocation(program, "off");
    const uCover = gl.getUniformLocation(program, "cover");
    gl.uniform1i(gl.getUniformLocation(program, "img"), 0);
    gl.uniform1i(gl.getUniformLocation(program, "dep"), 1);

    function upload(unit, image) {
      const texture = gl.createTexture();
      gl.activeTexture(gl.TEXTURE0 + unit);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
    }

    const load = (url) =>
      new Promise((resolve, reject) => {
        const image = new Image();
        image.decoding = "async";
        image.onload = () => resolve(image);
        image.onerror = reject;
        image.src = url;
      });

    let ready = false;
    let active = false;
    let raf = 0;
    let aspect = 1.6;
    let lastInput = -Infinity;
    const current = { x: 0, y: 0 };
    const target = { x: 0, y: 0 };

    function resize() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const w = Math.round(canvas.clientWidth * dpr);
      const h = Math.round(canvas.clientHeight * dpr);
      if (w > 0 && h > 0 && (canvas.width !== w || canvas.height !== h)) {
        canvas.width = w;
        canvas.height = h;
        gl.viewport(0, 0, w, h);
      }
    }

    function draw() {
      resize();
      // Fill the canvas like object-fit: cover, zoomed in a little so the moving edges never show.
      const view = canvas.width / Math.max(1, canvas.height);
      const zoom = 0.94;
      if (view > aspect) gl.uniform2f(uCover, zoom, (zoom * aspect) / view);
      else gl.uniform2f(uCover, (zoom * view) / aspect, zoom);
      gl.uniform2f(uOff, current.x * 0.05, current.y * 0.035);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }

    function frame(now) {
      raf = 0;
      if (!active || !ready) return;
      const idle = now - lastInput > 2600;
      if (idle && !reduceMotion.matches) {
        target.x = Math.sin(now / 1700);
        target.y = Math.sin(now / 2600) * 0.6;
      }
      current.x += (target.x - current.x) * 0.075;
      current.y += (target.y - current.y) * 0.075;
      draw();
      const settled = Math.abs(target.x - current.x) < 0.001 && Math.abs(target.y - current.y) < 0.001;
      if (!(idle && reduceMotion.matches && settled)) raf = window.requestAnimationFrame(frame);
    }

    function kick() {
      if (active && ready && !raf) raf = window.requestAnimationFrame(frame);
    }

    canvas.addEventListener("webglcontextlost", (e) => {
      e.preventDefault();
      ready = false;
      onFail();
    });

    Promise.all([load(sourceUrl), load(depthUrl)])
      .then(([source, depth]) => {
        aspect = source.naturalWidth / source.naturalHeight || 1.6;
        upload(0, source);
        upload(1, depth);
        ready = true;
        // Start a little off center so the depth reads even before anything moves.
        current.x = target.x = 0.6;
        current.y = target.y = 0.2;
        draw();
        canvas.classList.add("ready");
        kick();
      })
      .catch(onFail);

    return {
      setActive(on) {
        active = on;
        if (on) kick();
      },
      point(x, y) {
        target.x = Math.max(-1, Math.min(1, x));
        target.y = Math.max(-1, Math.min(1, y));
        lastInput = window.performance.now();
        kick();
      },
    };
  }

  (function initDepthDemo() {
    const stage = document.getElementById("depth-stage");
    const canvas = document.getElementById("depth-canvas");
    if (!stage || !canvas) return;
    const buttons = Array.from(document.querySelectorAll("#depth-modes [data-mode]"));
    const captions = Array.from(document.querySelectorAll("#depth-caption [data-for]"));
    let mode = "live";
    let onScreen = false;

    const fail = () => stage.classList.add("no-gl");
    const renderer = createDepthRenderer(canvas, "assets/screens/2d3d-source.webp", "assets/screens/2d3d-depth.webp", fail);
    if (!renderer) fail();
    const sync = () => renderer && renderer.setActive(mode === "live" && onScreen && !document.hidden);

    function setMode(next, focus) {
      mode = next;
      stage.dataset.mode = next;
      buttons.forEach((b) => {
        const on = b.dataset.mode === next;
        b.setAttribute("aria-pressed", String(on));
        if (on && focus) b.focus();
      });
      captions.forEach((c) => (c.hidden = c.dataset.for !== next));
      sync();
    }
    buttons.forEach((b, i) => {
      b.addEventListener("click", () => setMode(b.dataset.mode, false));
      b.addEventListener("keydown", (e) => {
        if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
        const next = buttons[(i + (e.key === "ArrowRight" ? 1 : buttons.length - 1)) % buttons.length];
        setMode(next.dataset.mode, true);
        e.preventDefault();
      });
    });

    stage.addEventListener("pointermove", (e) => {
      if (!renderer || mode !== "live") return;
      if (e.pointerType !== "mouse" && e.buttons === 0) return;
      const r = stage.getBoundingClientRect();
      renderer.point(((e.clientX - r.left) / r.width - 0.5) * 2, ((e.clientY - r.top) / r.height - 0.5) * 2);
    });

    if ("IntersectionObserver" in window) {
      new IntersectionObserver((entries) => {
        onScreen = entries[entries.length - 1].isIntersecting;
        sync();
      }).observe(stage);
    } else {
      onScreen = true;
    }
    document.addEventListener("visibilitychange", sync);
    sync();
  })();

  // ---- Install tabs -----------------------------------------------------------------------------
  const tabList = document.querySelector('[role="tablist"]');
  const tabs = Array.from(document.querySelectorAll('[role="tab"]'));
  const panels = Array.from(document.querySelectorAll('[role="tabpanel"]'));

  function selectTab(name, focus) {
    tabList.dataset.active = name;
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

  const dlButtons = Array.from(document.querySelectorAll("[data-download]"));
  const dlMeta = document.getElementById("download-meta");
  const otherBox = document.getElementById("other-files");
  const otherList = document.getElementById("other-list");
  const releaseBox = document.getElementById("release");
  const labelOf = (button) => button.querySelector("[data-download-label]");

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

  function setDownload(href, label) {
    dlButtons.forEach((b) => {
      b.href = href;
      if (!b.classList.contains("started")) labelOf(b).textContent = label;
    });
  }

  function renderDownload() {
    const t = DYN[lang];
    dlMeta.textContent = "";
    if (releaseError || !release) {
      if (releaseError) {
        setDownload(RELEASES_PAGE, t.releasesPage);
        dlMeta.append(t.fetchFailed);
      }
      return;
    }
    const apks = (release.assets || []).filter((a) => /\.apk$/i.test(a.name));
    const version = release.tag_name || "";
    if (!apks.length) {
      setDownload(RELEASES_PAGE, t.releasesPage);
      dlMeta.append(t.noApk);
      return;
    }
    const best = recommendedAsset(apks);
    setDownload(best.browser_download_url, isQuest ? t.downloadQuest(version) : t.download(version));
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

  dlButtons.forEach((b) => {
    b.addEventListener("click", () => {
      if (!release || releaseError || b.classList.contains("started")) return;
      const label = labelOf(b);
      b.classList.add("started");
      label.textContent = DYN[lang].started;
      window.setTimeout(() => {
        b.classList.remove("started");
        renderDownload();
      }, 2500);
    });
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
    // Headings, "-" lists and paragraphs; wrapped lines continue the item or paragraph above.
    let list = null;
    let block = null;
    markdown
      .replace(/\r/g, "")
      .split("\n")
      .forEach((raw) => {
        const line = raw.trimEnd();
        if (!line.trim()) {
          list = null;
          block = null;
          return;
        }
        const heading = line.match(/^(#{1,4})\s+(.*)$/);
        const item = line.match(/^\s*[-*]\s+(.*)$/);
        if (heading) {
          list = null;
          block = null;
          const h = document.createElement(heading[1].length <= 2 ? "h4" : "h5");
          inline(h, heading[2]);
          container.append(h);
        } else if (item) {
          if (!list) {
            list = document.createElement("ul");
            container.append(list);
          }
          block = document.createElement("li");
          inline(block, item[1]);
          list.append(block);
        } else if (block && (list ? /^\s/.test(line) : true)) {
          block.append(" ");
          inline(block, line.trim());
        } else {
          list = null;
          block = document.createElement("p");
          inline(block, line.trim());
          container.append(block);
        }
      });
  }

  function renderRelease() {
    const t = DYN[lang];
    releaseBox.textContent = "";
    releaseBox.classList.toggle("loading", !release && !releaseError);
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
    const version = document.createElement("span");
    version.className = "version";
    version.textContent = release.tag_name || "";
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
    if (version.textContent && version.textContent !== title.textContent) head.append(version);
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
    more.className = "release-more";
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
