# UI Plan — Overlay Config Screen Restyle (Iris/Sodium-style with neon states)

> Status: **PLAN ONLY — not implemented yet.** This document is the reference for the
> upcoming implementation. It describes exactly how the overlay config screen will look
> and behave, so it can be reviewed and adjusted before any code is written.

---

## 1. Goal

Restyle `OverlayConfigScreen` (the video/image overlay settings screen) so it looks and
feels like the settings screens of **Iris** and **Sodium**: a translucent dark panel with
clean rows, inline controls, section headers, and hover feedback — while staying true to
the vanilla font and layout the user already knows.

The visual identity is a **calm neon accent system**:

| State                  | Accent color          |
|------------------------|-----------------------|
| Default (idle)         | **Light blue**        |
| Hovered                | **Cyan**              |
| Activated (ON/selected)| **Light green**       |

Color transitions between these states are **animated** (smooth per-frame blending, not
instant swaps).

This plan also **re-adds the media-type selection flow** that was previously removed:
a direct question screen for creating your first overlay, and a dropdown-style screen for
changing an existing overlay's type — both styled identically to the new config screen.

---

## 2. Reference: what "Iris/Sodium-like" means here

We borrow the *structure and rhythm* of Iris/Sodium settings screens, not their exact
colors:

- **Translucent dark panel** as the screen backdrop (slightly lighter than the vanilla
  blurred background, so widgets pop but the world is still visible).
- **Row-based layout**: each setting is a row with a label on the left and its control
  (button / slider / text field) on the right.
- **Section headers** (e.g. "General", "Size & Position", "Media", "Presets") with a
  subtle divider line.
- **Inline controls**: no floating buttons — every control sits in its row.
- **Hover feedback**: the whole row's control highlights on hover (our twist: cyan).
- **Left sidebar** for the overlay list (keeps the current two-column arrangement).

What we **keep vanilla**:
- The **font color stays the same**: white (`0xFFFFFFFF`) with shadow on every label —
  never recolored.
- The existing screen sections, order of options, drag-mode, live preview, presets,
  file picker, and the Done/Cancel behavior.
- The vanilla sound, narration, and keyboard behavior of widgets.

---

## 3. Color system

### 3.1 Palette (ARGB hex)

| Role                | Color           | Hex          | Used for                              |
|---------------------|-----------------|--------------|---------------------------------------|
| Panel background    | Dark navy       | `0xCC0B1020`  | Screen backdrop                       |
| Row background      | Darker panel    | `0x66131A2E`  | Rows + sidebar entries                |
| Border (idle)       | Light blue      | `0xFF5FB2FF`  | Default outline of every control      |
| Border (hovered)    | Cyan            | `0xFF37E0FF`  | Outline while mouse is over a control |
| Border (activated)  | Light green     | `0xFF6BFF8F`  | Outline when a toggle is ON / row selected / focused |
| Glow (hovered)      | Cyan, 40%       | `0x6637E0FF`  | Soft outer ring on hover              |
| Glow (activated)    | Green, 30%      | `0x4D6BFF8F`  | Soft outer ring on active controls    |
| Text                | White (unchanged)| `0xFFFFFFFF`  | All labels, shadowed                  |

### 3.2 State machine (per widget)

```
                    mouse enters
   IDLE (light blue) ───────────────► HOVER (cyan)
        ▲                                  │
        │          hover ends              │  widget becomes
        │◄─────────────────────────────────┘  active (toggled ON,
        │                                     selected in list,
        │                                     focused)
        │          active cleared ◄───────────┘
        └─────────────────────────────────────► ACTIVATED (light green)
```

Rules:

1. **Activated wins** over Hovered. If a control is ON and hovered, it shows light
   green (activation is the strongest signal). The hover glow still appears around it.
2. **Focused** (keyboard) counts as activated for outline color.
3. **Disabled / empty** controls (e.g. "- Remove" with one overlay, or an empty list)
   use a dim gray outline (`0xFF3A3F4E`) and no glow — clearly inert.
4. The overlay **list selection** and the **enabled toggle** both drive the green state.

### 3.3 Dynamic transitions (the "switch from color to color")

Every widget keeps a **current color** that it lerps toward a **target color** every
frame, using exponential smoothing so motion feels natural and framerate-independent:

```
current += (target - current) * (1 - exp(-TRANSITION_SPEED * deltaTime))
TRANSITION_SPEED ≈ 12  (1/s)  → a full color change takes ~150–200ms
```

Implementation detail:

- A small helper class `ColorLerp` (holds `float[] currentRgb`, `targetRgb`, plus a
  `glowAlpha`) with `setTarget(State)` and `getColor()`.
- Each `NeonButton` / `NeonSlider` / `NeonEditBox` owns one `ColorLerp`.
- State is re-evaluated in `renderWidget(...)`: `isHovered()` / `active` / `isFocused()`.
- The **glow alpha** lerps with the same formula — so the outer ring fades in/out
  smoothly instead of popping.
- Optional flourish (cheap, tasteful): the activated border gently "breathes" by
  oscillating glow alpha ±10% around its target with a slow sine wave while active.

---

## 4. Widget architecture

Vanilla constraint (already verified against 1.21.11 mappings):
`AbstractButton.renderWidget` is `final`, and `render`/`renderWidget` in the
`AbstractWidget` chain are final too — so buttons must be built on `AbstractWidget`
directly. Sliders and text boxes can be subclassed.

New classes in `client/screen/`:

### 4.1 `NeonScreen` (base class for all three screens)
- Extends `Screen`.
- `renderBackground(...)`: draws the vanilla blurred background (super), then a
  translucent dark navy gradient panel covering the whole screen, then a thin
  light-blue accent line under the title.
- Helpers: `drawRow(g, x, y, w, label, controlX)` and `drawSectionHeader(...)` so all
  screens share the same row rhythm.
- Same title rendering as vanilla (centered, white, shadowed).

### 4.2 `NeonButton extends AbstractWidget`
- Custom `renderWidget`:
  1. Fill: dark panel color (`0x66131A2E`), slightly lighter when hovered.
  2. Outline: current lerped state color, 1px; **activated** gets a 1.5–2px feel via
     an extra inner line.
  3. Glow: expanded semi-transparent outline when hovered/activated (lerped alpha).
  4. Label: white with shadow (unchanged), drawn centered.
- `onClick` → supplied `Runnable`.
- Mouse sound handled by `AbstractWidget.mouseClicked` (no double sound);
  keyboard activation plays the click sound manually.
- `updateWidgetNarration` + `createNarrationMessage` → default button narration.
- Tooltip support comes free from `AbstractWidget` (`setTooltip`).

### 4.3 `NeonSlider extends AbstractSliderButton`
- Subclasses `AbstractSliderButton` (its `renderWidget` is overridable).
- Track: thin line in the current lerped color (light blue idle → cyan hover → green
  when the value is "engaged" i.e. being dragged or value > 0).
- Thumb: 4×8px rounded rect in the state color, with glow while dragging.
- Keeps the vanilla `updateMessage()`/`applyValue()` hooks used by the config screen.

### 4.4 `NeonEditBox` (thin wrapper around vanilla `EditBox`)
- Vanilla `EditBox` cannot change its border color, so we wrap it:
  - Delegate all input/rendering to the vanilla box (fonts, caret, selection).
  - After `super.render`, draw a 1px outline in the lerped state color
    (blue idle / cyan hover / green focused), matching the other controls.
- Used for: path field, X/Y/W/H fields, preset name field.

### 4.5 `NeonTypeButton` (small extension of `NeonButton`)
- A button with a small "▾" chevron drawn on the right edge (indicates dropdown).
- Used for the media-type control.

### 4.6 `NeonCheckbox` (for the "Enabled" toggle)
- A small square with a checkmark, drawn in the state color (green when ON).
- Label keeps white font. Row-layout friendly.

---

## 5. Screen layout (OverlayConfigScreen)

### 5.1 Structure (ASCII mock)

```
┌────────────────────────────────────────────────────────────────────┐
│                    Overlay Settings                                 │
│ ────────────────────────────────────────────────────────────────    │
│ ┌ Overlays ───────────────┐   GENERAL                                │
│ │ ▸ overlay1        [ON]  │   Type      [ Video ▾        ]          │
│ │   overlay2              │   Enabled   [ ✓ Enabled       ]          │
│ │   overlay3              │                                          │
│ │                        │   MEDIA                                  │
│ │  [+ Add Overlay]       │   Source    [ C:\videos\clip.mp4 ] [Browse│
│ │  [- Remove]            │   Opacity   [ ──────●────        ]       │
│ │                        │   Volume    [ ────●─────        ]        │
│ │                        │   Playback  [ ▶ Play ] [ ⟲ Loop: ON ]    │
│ │                        │                                          │
│ │                        │   SIZE & POSITION                         │
│ │                        │   X [  100 ]  Y [  40 ]  W [ 640 ]  H [360]│
│ │                        │   Anchor   [ Center ▸ ]  [ ⬒ Lock AR ]   │
│ │                        │                                          │
│ │                        │   POSITIONING                            │
│ │                        │   [ Drag to position ]                   │
│ │                        │                                          │
│ │                        │   PRESETS                                │
│ │                        │   [name____] [Save] [Delete]             │
│ │                        │   [< Prev] [Next >]                      │
│ │                        │                                          │
│ │                        │   [ Done ]      [ Cancel ]               │
└────────────────────────────────────────────────────────────────────┘
```

### 5.2 Rules
- **Sidebar** (left, ~130px): one row per overlay. The **selected** row shows a
  light-green outline + green "▸" marker; hovered rows get the cyan outline.
  The row label = existing `§a`/`§7` colored path preview (unchanged).
  If the list outgrows the screen, the sidebar becomes scrollable (mouse-wheel).
- **Settings panel** (right): rows grouped by section header. Each row =
  label (white, left, at control's vertical center) + control (right-aligned block).
- Controls keep their **exact current behavior** — only the rendering changes:
  - Type → `NeonTypeButton` (opens the type dropdown — see §7) instead of cycling.
  - Enabled → `NeonCheckbox`.
  - Source path → `NeonEditBox` + `NeonButton` "Browse…".
  - Opacity/Volume → `NeonSlider`.
  - Play/Pause, Loop, Anchor, Lock aspect, Drag mode, presets, Done/Cancel →
    `NeonButton`s.
- **Live preview** (top-right thumbnail) keeps its white label and vanilla drawing;
  only the panel behind it is restyled.
- **Drag mode** keeps its full-screen dark overlay + green `renderOutline` box
  (already matches the neon language); the info text stays white.
- The **Done/Cancel** row sticks to the bottom of the panel.

---

## 6. First-overlay creation flow (question screen)

Re-adds the "choose the type for your very first overlay" flow, in the new style.

### 6.1 `MediaTypeSelectionScreen extends NeonScreen`
- Title: "What should this overlay be?" (key `config.mirahud.media_type_prompt`).
- **Mode A — CREATE_FIRST** (opened from "+ Add Overlay" when the overlay list is empty):
  - Shows one large `NeonButton` per media variant (currently **Image**, **Video**),
    stacked vertically, centered.
  - Each button: icon-less, label = localized variant name, hover → cyan, click →
    flashes green, then creates the overlay with that type and opens the config
    screen already editing it (same `addOverlay()` → `setMediaType` behavior as
    before).
- **Mode B — CHANGE_TYPE** (opened from the Type control in the config screen):
  - Same screen, different title: "Select Overlay Type"
    (`config.mirahud.media_type_select`).
  - Lists **all** variants from the `MediaTypes` registry (scrollable if many).
  - The **currently selected** type is marked with a light-green outline + "✓".
  - Hover → cyan, click → applies the new type and returns to the config screen.
  - Esc / click outside → returns without changing.

### 6.2 `MediaTypes` registry (re-created)
- `client/overlay/config/MediaTypes.java` — a single ordered list:
  `("image", key), ("video", key), ...`
- Every UI that lists types iterates this registry, so **future media types appear
  automatically** in the question, the dropdown, and the file-picker extension logic.
- `byId(id)` fallback = first entry; `displayName(id)` = translation key.

---

## 7. Type dropdown (change-type flow)

- The Type control is a `NeonTypeButton` ("Video ▾"); clicking it opens
  `MediaTypeSelectionScreen` in CHANGE_TYPE mode for that overlay.
- Visual continuity: the dropdown screen uses the exact same panel, rows, and
  state colors as the config screen — it reads as one UI.
- Behavior identical to the previously-built (then removed) flow:
  `OverlayConfigScreen` exposes `setOverlayMediaType(index, type)` /
  `createOverlayWithType(type)` / `mediaTypeAt(index)` used by the selection screen.

---

## 8. Translation keys

Re-add the two keys that were removed with the old flow (plus the registry keys):

| Key                                   | en_us                |
|---------------------------------------|----------------------|
| `config.mirahud.media_type_prompt`    | "What should this overlay be?" |
| `config.mirahud.media_type_select`    | "Select Overlay Type" |

- Add constants `CONFIG_MEDIA_TYPE_PROMPT`, `CONFIG_MEDIA_TYPE_SELECT` to
  `TranslationsKeys`.
- Add to `en_us.json` + `de_de.json` (seed files) **and** to
  `tools/translations/entries_notice.py` (the module injected into all 126 lang
  files by `gen_langs.py`) so every language gets them — same pattern as before.
- `check_java_keys.py` already validates Java↔JSON parity; it will catch orphans.
- The 3 `ai_translation_notice*` keys and the first-launch notice screen are
  **untouched** by this work.

---

## 9. Explicitly unchanged

- White font everywhere (no recoloring of any text).
- All settings logic: media loading, presets, drag positioning, auto-scale,
  aspect-lock sync, file picker (TinyFileDialogs), toasts.
- `TranslationNoticeScreen` (first-launch popup) — stays as-is.
- Vanilla widget behaviors: sound, narration, Tab/arrow focus, tooltips.
- The `InventoryWidgetManagerScreen` / `CustomScreen` — out of scope.

---

## 10. Files

| Action | File |
|--------|------|
| new    | `client/screen/NeonScreen.java` |
| new    | `client/screen/NeonButton.java` |
| new    | `client/screen/NeonTypeButton.java` |
| new    | `client/screen/NeonSlider.java` |
| new    | `client/screen/NeonEditBox.java` |
| new    | `client/screen/NeonCheckbox.java` |
| new    | `client/screen/MediaTypeSelectionScreen.java` |
| new    | `client/screen/ColorLerp.java` (or nested in a `ui` helper package) |
| new    | `client/overlay/config/MediaTypes.java` |
| edit   | `client/screen/OverlayConfigScreen.java` (swap widgets + dropdown wiring) |
| edit   | `client/translationskeys/TranslationsKeys.java` (+2 keys) |
| edit   | `src/main/resources/assets/mirahud/lang/en_us.json`, `de_de.json` |
| edit   | `tools/translations/entries_notice.py`, `tools/gen_langs.py` (if key list is centralized) |
| delete | nothing (previous cyberpunk classes are already gone) |

---

## 11. Implementation order & validation

1. `ColorLerp` + `NeonScreen` (background, panel, row helpers).
2. `NeonButton` + `NeonCheckbox` + `NeonSlider` + `NeonEditBox` (pure rendering,
   behavior delegated to vanilla).
3. Restyle `OverlayConfigScreen` row by row; verify against the mock in §5.1.
4. Re-add `MediaTypes` registry + `MediaTypeSelectionScreen` (both modes).
5. Wire dropdown into the config screen; re-add the 2 translation keys.
6. Regenerate all 126 lang files (`tools/gen_langs.py`), run
   `tools/validate_langs.py` + `tools/check_java_keys.py`.
7. `./gradlew compileJava compileClientJava` must pass with 0 errors.
8. Code review pass (focus: final-method constraints, no double click-sounds,
   no color-state dead-ends, scroll + keyboard edge cases).

---

## 12. Open questions (for review)

1. **Scroll** the left sidebar when there are many overlays — in scope or defer?
   (Plan assumes in scope, mouse-wheel.)
2. **Breathe effect** on activated borders (§3.3) — keep, or keep transitions
   strictly linear for calmness?
3. Should the **question screen** (first overlay) show small media-type icons, or
   stay text-only like Iris/Sodium rows? (Plan assumes text-only.)
