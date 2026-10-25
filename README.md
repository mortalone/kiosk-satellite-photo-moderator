# Kiosk Satellite Photo Moderator Plugin

A native Android overlay plugin for [Kiosk Satellite](https://github.com/cph-solaris/kiosk-satellite) designed for wall-mounted smart home tablets running family photo slideshow screensavers.

---

## 🌟 Key Features

1. **Discrete Floating Moderation Icon (`🛡️`)**:
   - Displays a clean floating button in any corner of the screen.
   - Configurable icon (`🛡️`, `⚠️`, `🚩`, `👁️`), size (36 dp, 48 dp, 60 dp), and idle opacity.
   - No distracting text on the screensaver.

2. **Native Screensaver Touch Isolation**:
   - **Tapping the Shield**: Consumes the touch event natively, prevents the screensaver from exiting, and opens the moderation dialog.
   - **Tapping Anywhere Else**: Passes through to Kiosk Satellite's native window, waking the tablet and returning to the Home Assistant dashboard as normal.

3. **Interactive Moderation Dialog**:
   - **Recent Photo Browsing**: Uses `GET /api/photos/recently_shown` to browse the photos recently shown on screen with thumbnail previews, filenames, capture dates, and identified persons.
   - **`🔞 Markér som nøgenhed`**: Instantly calls `POST /api/photos/{id}/report_nsfw` to flag and remove inappropriate photos.
   - **`👤 Fremmed / Ikke denne person`**: Calls `POST /api/photos/{id}/remove_subject` (`{"action": "stranger"}`) to clean invalid person assignments and AI training embeddings.
   - **`🚫 Vis aldrig mere (Blacklist)`**: Calls `POST /api/photos/{id}/status` (`{"action": "blacklist"}`) to permanently exclude the photo.
   - **Safety Auto-Close**: Closes automatically after 45 seconds of inactivity so the tablet never gets stuck.

---

## ⚙️ Settings (Manifest v1)

| Setting | Type | Default | Description |
|---|---|---|---|
| `curatorUrl` | string | `http://192.168.0.96:8095` | Base URL of Family Photo Curator server |
| `overlayTarget` | select | `Screensaver only` | Display on Kiosk screensaver, Fotoo, or always |
| `buttonPosition` | select | `Top right` | `Top right`, `Top left`, `Bottom right`, `Bottom left` |
| `buttonSize` | select | `Medium (48 dp)` | `Small (36 dp)`, `Medium (48 dp)`, `Large (60 dp)` |
| `buttonIcon` | select | `Shield (🛡️)` | `Shield (🛡️)`, `Warning (⚠️)`, `Flag (🚩)`, `Eye (👁️)` |
| `buttonOpacity` | number | `70 %` | Opacity of the floating icon while idle (20% - 100%) |
| `buttonOffsetX` | number | `16 dp` | Horizontal margin from the screen corner |
| `buttonOffsetY` | number | `16 dp` | Vertical margin from the screen corner |
| `autoDismissSeconds` | number | `45 s` | Auto-closes the dialog if left untouched |

---

## 📦 Installation in Kiosk Satellite

1. In Kiosk Satellite on your tablet, go to **Settings** -> **Plugins**.
2. Tap **Add Plugin** or enter repository: `https://github.com/mortalone/kiosk-satellite-photo-moderator`.
3. Install the latest release (`v0.1.0`).
4. Enter your Family Photo Curator server URL (e.g. `http://192.168.0.96:8095`).
5. Choose your preferred corner for the shield button and save.

---

## 🛠️ Build & CI/CD

Automated releases are built via GitHub Actions (`.github/workflows/build.yml`):
- Compiles Java sources against Android API 35 with `--release 8`.
- DEX-compiles classes using Android D8.
- Bundles `classes.dex`, `kiosk-satellite-plugin.json`, and `LICENSE` into a signed release `.zip`.

---

## 📄 License

MIT License. Copyright (c) 2026 mortalone.
