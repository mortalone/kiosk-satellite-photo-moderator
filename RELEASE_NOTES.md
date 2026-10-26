# Release Notes

## 0.2.0 - Active Photo Focus & Natural History Navigation

- **Instant Active Photo Focus**: When opening moderation, the dialog immediately presents the photo currently showing on the screensaver with a green badge (`🟢 Aktuelt (1 af N)`) and timestamp (`⏱️ Vises lige nu`).
- **Natural History Navigation ("◀ Forrige" / "Næste ▶")**:
  - `◀ Forrige` is now enabled right away and steps back in time to photos shown before this one.
  - `Næste ▶` steps forward in time towards the active photo and is disabled on index 0.
  - Clear descriptive counters: `🟢 Aktuelt (1 af 60)`, `◀ 1 forrige (2 af 60)`, `◀ 2 forrige (3 af 60)`.
- **Thumbnail Auto-Reload**: When stepping between photos in history, thumbnails and details update immediately and are cached in memory for fluid browsing.

## 0.1.1 - Event Subscription Fix

- **Fix "Unknown KS event"**: Updated screensaver event subscription from legacy `screensaver` to standard Kiosk Satellite events `screensaver.state` and `screensaver.view`.
- **Event Dispatch Handling**: Added prefix matching for `ks.screensaver.state` and `screensaver.state`.
- **Clean Unsubscribe**: Added unsubscription of events on plugin stop.

## 0.1.0 - Initial Release

- **Floating Moderation Button**: Renders a discrete, customizable icon (`🛡️`, `⚠️`, `🚩`, `👁️`) on top of the screensaver.
- **Smart Touch Handling**: Tapping the moderation button consumes the touch event without dismissing the screensaver. Tapping anywhere else on the screen passes through to Kiosk Satellite, waking the tablet and returning to the dashboard.
- **Recent Photo History**: Browse recently displayed photos with thumbnail previews, timestamps, and recognized family members (`◀ Forrige` / `Næste ▶`).
- **One-Tap Actions**:
  - `🔞 Markér som nøgenhed`: Rejects photo and strips facial embeddings.
  - `👤 Fremmed / ikke denne person`: Disassociates incorrect person match and cleans training embeddings.
  - `🚫 Vis aldrig mere (Blacklist)`: Permanently blacklists the photo from the slideshow.
- **Customizable Appearance**: Select screen corner (Top-Right, Top-Left, Bottom-Right, Bottom-Left), button size (36dp, 48dp, 60dp), idle opacity (20%–100%), and offsets.
- **Auto-Dismiss Safety**: Configurable auto-close timer (default 45s) automatically closes the dialog if untouched so the tablet never gets stuck in moderation mode.
