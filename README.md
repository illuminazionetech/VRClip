# VRClip

[![Build](https://github.com/illuminazionetech/VRClip/actions/workflows/build.yml/badge.svg)](https://github.com/illuminazionetech/VRClip/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/illuminazionetech/VRClip)](https://github.com/illuminazionetech/VRClip/releases/latest)
[![License: GPL v3](https://img.shields.io/github/license/illuminazionetech/VRClip)](LICENSE)

**Downloader video/audio universale + player immersivo 3D/360°/XR, per Android e Meta Quest.**

VRClip scarica video e audio da centinaia di piattaforme (tramite yt-dlp) e li riproduce in-app con un player che riconosce automaticamente i contenuti flat, 360°, 180° e stereoscopici 3D — con un'esperienza completamente immersiva su Meta Quest tramite Meta Spatial SDK, e un player con rendering GL dedicato (pan touch/giroscopio, modalità split-screen per visori da telefono) su smartphone e tablet Android. L'interfaccia usa uno stile "Liquid Glass" con vetro sfocato reale, adattato automaticamente alla densità e alle interazioni a controller quando gira su Quest.

## ✨ Funzionalità principali

- **Download universale**: tutti i siti supportati da yt-dlp, con multi-thread e aria2c integrato per la massima velocità.
- **Player immersivo integrato**: riconoscimento automatico di video flat / 360° mono / 360° 3D (top-bottom o side-by-side) / 180° / 3D side-by-side / 3D top-bottom, con possibilità di forzare manualmente la proiezione per ogni video.
- **Rendering dedicato per piattaforma**:
  - **Android (telefono/tablet)**: rendering OpenGL per sfera equirettangolare (360°/180°) con pan touch/giroscopio, e modalità split-screen in stile Cardboard per i video 3D.
  - **Meta Quest (2, 3, Pro)**: scena immersiva reale tramite Meta Spatial SDK, con pannello video posizionato nello spazio e stereo corretto per occhio.
- **Gestione metadati**: incorpora automaticamente metadati, sottotitoli e miniature nei file scaricati.
- **UI Liquid Glass**: superfici in vetro con sfocatura reale (GPU, API 31+, con fallback elegante sotto), adattate automaticamente per l'uso a 10 piedi con controller su Meta Quest.
- **Privacy-first**: nessun tracciamento, nessuna pubblicità, nessuna sezione donazioni. Tutti i download avvengono localmente.

## 📱 Installazione

### Su Android (telefono/tablet)
1. Scarica l'APK dalla sezione [Release](https://github.com/illuminazionetech/VRClip/releases/latest) — vedi la tabella sotto per capire quale scegliere.
2. Abilita "Origini sconosciute" nelle impostazioni del tuo dispositivo per installare l'APK.

### Su Meta Quest (2 / 3 / Pro)
1. Scarica lo stesso APK (`generic` consigliato) dalla sezione Release — è un'unica app universale, non serve un file separato per Quest.
2. Installalo sul visore con [SideQuest](https://sidequestvr.com/) o con `adb install`.
3. L'app rileva automaticamente l'esecuzione su Quest e attiva l'interfaccia e il player dedicati.

## ⬇️ Quale file scaricare

Ogni build della CI produce un APK per architettura, molto più leggero di un pacchetto universale (vedi [Release](https://github.com/illuminazionetech/VRClip/releases/latest)), oppure puoi scaricare il file giusto in automatico dal [sito di download](https://illuminazionetech.github.io/VRClip/):

| File | Piattaforma | Quando usarlo |
|---|---|---|
| `app-generic-arm64-v8a-release.apk` | Android e Meta Quest | **Consigliato** — copre tutti i visori Meta Quest e la quasi totalità degli smartphone Android recenti. |
| `app-generic-armeabi-v7a-release.apk` | Android | Solo per dispositivi Android a 32 bit molto datati. |
| `app-generic-x86_64-release.apk` / `app-generic-x86-release.apk` | Android | Solo per emulatori o dispositivi Android con CPU Intel/AMD. |
| `app-generic-universal-release.apk` | Android e Meta Quest | Contiene tutte le architetture: usalo solo se non sei sicuro di quale scegliere (file più pesante). |
| `app-githubPreview-*-release.apk` | Android e Meta Quest | Canale beta/anteprima (ID app separato, si installa insieme alla build stabile senza sovrascriverla). |

Non esiste un file separato "per Quest": la stessa app gira su entrambe le piattaforme e adatta automaticamente interfaccia e player.

## 🕹️ Utilizzo

1. Copia il link del video/audio che vuoi scaricare.
2. Apri VRClip e incolla il link (o condividilo direttamente dall'app sorgente).
3. Seleziona il formato desiderato.
4. Al termine del download, tocca il video nella lista per aprirlo nel player integrato — la proiezione (flat/360°/3D) viene rilevata automaticamente; puoi forzarla manualmente dal menu ⋮ se la rilevazione sbaglia.

## 🥽 Preparazione per il Meta Quest Store

VRClip è tecnicamente pronto per Quest 2/3/Pro, ma essendo un downloader universale rientra in un'area grigia delle policy di contenuto di Meta (che restringono le app la cui funzione principale è scaricare contenuti di terzi). Il materiale di submission (manifest, privacy policy, checklist) è documentato in [`docs/META_QUEST_STORE_SUBMISSION.md`](docs/META_QUEST_STORE_SUBMISSION.md), incluso questo rischio — l'app resta comunque pienamente utilizzabile via sideload indipendentemente dall'esito di un'eventuale submission.

## 🔐 Sicurezza e Privacy

Tutti i download avvengono localmente sul dispositivo. Non vengono raccolti dati personali, non ci sono SDK di tracciamento/pubblicità, non ci sono account. L'unico traffico di rete è verso i siti sorgente che l'utente sceglie esplicitamente, più un controllo opzionale di aggiornamento di yt-dlp. Per segnalazioni di sicurezza vedi [`SECURITY.md`](SECURITY.md).

## 🤝 Contribuire

Vedi [`CONTRIBUTING.md`](CONTRIBUTING.md) per build locale, stile del codice e linee guida per le PR.

## 📜 Licenza e crediti

VRClip è **GPLv3** (vedi [`LICENSE`](LICENSE)) ed è un fork di [Seal](https://github.com/JunkFood02/Seal) di JunkFood02, a cui si aggiungono il supporto Meta Quest, il player immersivo 3D/360°/XR e il restyling Liquid Glass. Usa [yt-dlp](https://github.com/yt-dlp/yt-dlp), [ffmpeg](https://ffmpeg.org/) e [aria2c](https://aria2.github.io/) tramite [youtubedl-android](https://github.com/JunkFood02/youtubedl-android). Dettagli completi in [`NOTICE`](NOTICE).
