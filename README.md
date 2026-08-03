# MiraHUD

**Your Minecraft screen, your rules.** MiraHUD is a client-side Fabric mod that turns your HUD into a full-blown media command center. Throw images and videos anywhere on your screen. Drop one-click command buttons right into your inventory. Watch a movie while you mine. Pin a map while you explore. Open your ender chest, the auction house, or the shop mid-combat without typing a single slash command. Zero server-side setup required — just drop it in your mods folder and you're off.

---

## What MiraHUD Can Do

### 🖼️ Image & Video Overlays — On Your HUD, Your Way

You can layer *anything* on top of your game. An image, a looping video, a reference sheet, a map — wherever you want it, however big you want it.

- **Stack as many overlays as you want.** Images, videos, mix and match. Each one gets its own position, size, opacity, and anchor point. No limits.
- **Almost any image format works.** PNG, JPG, WEBP, TGA, BMP, GIF — even random web images you downloaded. It's not picky.
- **Pixel-perfect positioning.** Dial in exact X/Y coordinates, resize freely, lock the aspect ratio so nothing gets stretched weird. Opacity goes from fully solid all the way down to a ghostly 1% — perfect for subtle reference overlays.
- **Five anchor points.** Snap overlays to any corner or the center. They stay put no matter what resolution you're running.
- **Drag Mode.** Don't feel like punching in numbers? Just drag it where it looks good. Right-click to snap it back to an anchor. It's that easy.
- **Live preview.** Every slider, every nudge, every toggle — you see it happen on screen before you even close settings. No guessing.

### 🎬 A Real, Working Video Player — Inside Minecraft

This isn't a gimmick. MiraHUD ships a full FFmpeg-powered video pipeline. Your videos play with their actual audio, in perfect sync.

- **Plays pretty much everything.** MP4, MKV, WEBM, AVI, MOV, FLV, WMV — if it's a video file, it probably works.
- **Audio that stays in sync.** The audio track calls the shots. Video frames wait for their exact moment, so lips and sound never drift. It's not "close enough" — it's frame-accurate.
- **Full playback controls at your fingertips.** Pause and resume with **P**. Skip forward or back 10 seconds with the **arrow keys**. Restart from the beginning with **R**. Loop it with a toggle.
- **Loop mode is seamless.** No flicker. No reloading. No GPU spikes. It just restarts clean.
- **Per-overlay volume control.** Each overlay has its own volume slider that also respects your Minecraft master volume. Crank your music overlay, whisper your reference video, whatever works.
- **FFmpeg? It handles itself.** If you don't have FFmpeg, MiraHUD quietly downloads a portable build in the background. Your own FFmpeg installation (if you have one) is *never* touched, moved, or modified. On Windows it's fully automatic; on other platforms you can install FFmpeg through your package manager.
- **Self-healing.** Video pipeline crashes? File fails to load? MiraHUD doesn't freeze on a dead frame — it detects the problem, backs off, and retries automatically. It just figures it out.

### 🧰 One-Click Inventory Widgets — Stop Typing Commands

Tired of typing `/ec` or `/ah` every few minutes? MiraHUD puts those commands on your inventory screen as clickable buttons.

- **Six built-in widgets ready to go:** Ender Chest, Auction House, Sell, Trash, Shop, and Market. Click once, command fires. That's it.
- **Smart auto-layout.** Widgets line up neatly alongside your inventory. If they don't all fit in one row, they wrap to a second row automatically. No configuring positions by hand.
- **Toggle each widget on or off individually.** Don't need the Trash widget? Turn it off. Want all six? Turn them all on. **Press I to open the widget manager** and flip exactly the ones you want — the inventory HUD is fully customizable to your liking. Each widget even has a tooltip so you know what command it fires.
- **The entire inventory HUD itself can be toggled.** If you want a clean, vanilla inventory look for a session, just flip the master switch in the widget manager. Turn it back on whenever you need your command buttons again. You're always in control.
- **Custom widget framework.** Developers can build their own widgets with custom icons, item textures, and click behavior.

### ⚙️ Configuration That Actually Makes Sense

Nothing's hidden. Nothing's complicated. Everything just works.

- **Everything saves automatically.** Overlay positions, sizes, volumes, which widgets are enabled — all written to clean JSON files in `config/mirahud/`. It all comes back exactly how you left it next time you launch.
- **Overlay presets.** Got an overlay configured just right? Save it as a preset with a name. Reapply it later with one click. Cycle through your saved presets for different games or different moods.
- **Native file picker.** Click Browse and you get your operating system's *real* file dialog. It runs on a background thread so the game never stutters or freezes while you're hunting for a file.
- **Smart path resolution.** Typed a path wrong? Pasted something weird? MiraHUD automatically searches your Videos, Downloads, Desktop, and Documents folders by filename and fixes it for you. It's surprisingly good at finding things.

### ⌨️ Quick Controls — All Rebindeable

| Key | What It Does |
|-----|-------------|
| **O** | Open the Overlay Config screen — add, tweak, and position your overlays |
| **K** | Toggle ALL overlays on or off in one keystroke (great for screenshots or cutscenes) |
| **I** | Open the Inventory Widget manager — toggle individual widgets or the whole HUD |
| **P** | Pause or resume whatever video is currently playing |
| **→** | Skip forward 10 seconds |
| **←** | Skip backward 10 seconds |
| **R** | Restart the current video from the beginning |

Every single keybind is rebindeable in the vanilla Minecraft Controls menu. Make it yours.

---

## What You'll Need

- Minecraft **1.21.11**
- Fabric Loader **0.19.3** or newer
- Fabric API
- Java **21** or newer

MiraHUD is **100% client-side.** Install it on your machine and it works on *any* server — vanilla, modded, minigames, whatever. The server doesn't need a thing. On Windows, video overlays automatically download a portable FFmpeg build on first use. On other platforms, install FFmpeg through your package manager (`apt`, `brew`, `pacman`, etc.).

---

## Installation

1. Install Fabric Loader for Minecraft 1.21.11. Drop the Fabric API jar into your `mods` folder.
2. Drop the MiraHUD jar into your `mods` folder.
3. Launch the game. Press **O** to open the overlay config and start building your HUD.

---

## Building from Source

```bash
./gradlew build
```

The compiled jar lands in `build/libs/`.

---

## License

MiraHUD is released under the [MIT License](LICENSE). Go nuts.
