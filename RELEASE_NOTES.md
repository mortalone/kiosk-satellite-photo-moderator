# Release Notes

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
