# Mirage

Turn your Minecraft screen into a living command center. Mirage is a client-side Fabric mod that layers images and video directly onto your HUD and drops one-click command widgets into your inventory. Watch a movie in the corner of your screen while you mine. Pin a map or reference image to the top of your display. Open your ender chest, the auction house, or a shop with a single click from inside your inventory. No more alt-tabbing, no more typing commands mid-combat.

---

## Features

### Media Overlays on Your HUD

Lay anything on top of your game world, exactly where you want it.

- **Multiple overlays at once.** Stack as many images and videos on your screen as you like, each with its own position, size, opacity, and anchor point.
- **Images in almost any format.** PNG, JPG, WEBP, TGA, BMP, GIF, and more — even downloaded web images just work, not just game screenshots.
- **Pin, scale, and fade.** Position every overlay by pixel-perfect X/Y coordinates, resize it freely, lock its aspect ratio, and dial the opacity from fully solid to a ghostly 1 percent.
- **Five anchor points.** Snap overlays to the top left, top right, bottom left, bottom right, or dead center, and they stay put across any resolution.
- **Drag Mode.** No number crunching — enter Drag Mode and just drag the overlay around the screen until it feels right. Right-click snaps it back to its anchor.
- **Live preview.** Every change you make is rendered live on screen before you even close the settings.

### A Real Video Player, Inside Minecraft

Mirage ships with a full video pipeline powered by FFmpeg, so video overlays play with their audio — in sync.

- **Plays local video files** including MP4, MKV, WEBM, AVI, MOV, FLV, and WMV.
- **Audio that actually syncs.** The audio track is the master clock; video frames are held back to their exact presentation timestamps, so lips and sound never drift apart.
- **Full playback controls.** Pause and resume with one key, skip forward or backward 10 seconds, or restart the video from the beginning.
- **Loop mode.** Set a video to loop and it restarts seamlessly, with no flicker and no GPU churn.
- **Per-overlay volume**, which also respects your Minecraft master volume slider.
- **Automatic FFmpeg setup.** If no usable FFmpeg is found, Mirage quietly downloads a portable build in the background — you keep playing while it fetches. Your own FFmpeg installation is never touched, and the mod even refuses ancient builds that would break playback.
- **Self-healing playback.** If a video pipeline crashes or a file fails to load, Mirage detects it, backs off, and retries automatically instead of freezing on a dead frame.

### One-Click Inventory Command Widgets

Tired of typing server commands by hand? Mirage puts them on your inventory screen.

- **Six ready-made widgets**: Ender Chest, Auction House, Sell, Trash, Shop, and Market — each a clickable button that fires the matching command instantly.
- **Smart layout.** Widgets line up neatly along your inventory and wrap to a second row automatically when they don't fit.
- **Toggle each widget individually.** Open the widget manager and switch exactly which ones you want to see.
- **A custom widget framework** for developers — buttons can render item icons or custom textures with their own click behavior.

### Configuration That Just Works

- **Everything persists.** Overlay positions, sizes, volumes, widget toggles — all saved to clean JSON files in `config/mirage/`, restored automatically on every launch.
- **Overlay presets.** Save any overlay's full setup under a name, then reapply it later or cycle through your presets with a click.
- **A native file picker.** Browse for files with your operating system's real dialog — running on a background thread so the game never freezes while you search.
- **Smart path resolution.** Typed a path wrong? Mirage searches your Videos, Downloads, Desktop, and Documents folders by file name and fixes it for you.

### Quick Controls

| Key | Action |
|-----|--------|
| O | Open the Overlay Config screen |
| K | Toggle all overlays on or off |
| I | Open the Inventory Widget manager |
| P | Pause or resume video playback |
| Right Arrow | Skip forward 10 seconds |
| Left Arrow | Skip backward 10 seconds |
| R | Restart the current video |

Every keybind is rebindable in the vanilla Controls menu.

---

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.19.3 or newer**
- Fabric API
- Java **21 or newer**

Mirage is fully **client-side** — install it on the client and it works on any server without the server needing anything. Video overlays will automatically download a portable FFmpeg build on first use (Windows); on other platforms you can install FFmpeg through your package manager.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11 and place the Fabric API jar in your `mods` folder.
2. Drop the Mirage jar into your `mods` folder.
3. Launch the game, press **O**, and start pinning your world together.

## Building from Source

```bash
./gradlew build
```

The built jar lands in `build/libs/`.

## License

Mirage is released under the [MIT License](LICENSE).
