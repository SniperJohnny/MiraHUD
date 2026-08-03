package io.sniperjohnny.github.mirage.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.sniperjohnny.github.mirage.Mirage;
import io.sniperjohnny.github.mirage.client.hud_for_client.HudRenderingEntrypoint;
import io.sniperjohnny.github.mirage.client.overlay.ImageTextureManager;
import io.sniperjohnny.github.mirage.client.overlay.config.FilePathUtil;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayConfig;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayConfigManager;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayPreset;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayPresetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class OverlayConfigScreen extends Screen {
    // ---- Constants ----
    private static final Component TITLE = Component.translatable("config.mirage.title");
    private static final Component ENABLED_LABEL = Component.translatable("config.mirage.enabled");
    private static final Component PATH_LABEL = Component.translatable("config.mirage.source_path");
    private static final Component OPACITY_LABEL = Component.translatable("config.mirage.opacity");
    private static final Component VOLUME_LABEL = Component.translatable("config.mirage.volume");
    private static final Component POS_X_LABEL = Component.translatable("config.mirage.pos_x");
    private static final Component POS_Y_LABEL = Component.translatable("config.mirage.pos_y");
    private static final Component WIDTH_LABEL = Component.translatable("config.mirage.width");
    private static final Component HEIGHT_LABEL = Component.translatable("config.mirage.height");
    private static final Component ANCHOR_LABEL = Component.translatable("config.mirage.anchor");
    private static final Component LOCK_ASPECT_LABEL = Component.translatable("config.mirage.lock_aspect_ratio");
    private static final Component PREVIEW_LABEL = Component.translatable("config.mirage.preview");
    private static final Component PRESET_NAME_LABEL = Component.translatable("config.mirage.preset_name_label");
    private static final Component ADD_OVERLAY_LABEL = Component.translatable("config.mirage.add_overlay");
    private static final Component REMOVE_OVERLAY_LABEL = Component.translatable("config.mirage.remove_overlay");
    private static final Component MEDIA_TYPE_LABEL = Component.translatable("config.mirage.media_type");
    private static final Component PLAY_PAUSE_LABEL = Component.translatable("config.mirage.play_pause");

    private static final int DRAG_HITBOX_SLACK = 10;
    private static final int DRAG_MIN_VISIBLE_PX = 24;
    private static final float IMAGE_IMPORT_MAX_SCREEN_FRACTION = 0.35f;
    private static final int IMAGE_IMPORT_MIN_SIZE = 16;
    private static final int OVERLAY_LIST_WIDTH = 130;

    // ---- State ----
    private final Screen parent;
    private final List<OverlayConfig> overlayConfigs;
    private final List<OverlayConfig> initialConfigs;
    private int selectedIndex;
    private ImageTextureManager textureManager; // per-selected-overlay instance

    private EditBox pathField;
    private EditBox posXField;
    private EditBox posYField;
    private EditBox widthField;
    private EditBox heightField;
    private AbstractSliderButton opacitySlider;
    private AbstractSliderButton volumeSlider;
    private EditBox presetNameField;

    private boolean updatingFields;
    private boolean dragMode;
    private boolean dragging;
    private double dragLastMouseX, dragLastMouseY, dragFractionalX, dragFractionalY;
    private final Set<String> autoScaledPaths = new HashSet<>();
    private final AtomicBoolean pickerOpen = new AtomicBoolean(false);

    private int cachedPreviewWidth = -1, cachedPreviewHeight = -1, cachedPreviewX, cachedPreviewY;

    public OverlayConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        List<OverlayConfig> list = OverlayConfigManager.getRootConfig().overlays;
        this.overlayConfigs = new ArrayList<>(list);
        this.initialConfigs = new ArrayList<>();
        for (OverlayConfig c : list) this.initialConfigs.add(OverlayConfigManager.copyConfig(c));
        this.selectedIndex = list.isEmpty() ? -1 : 0;
        OverlayPresetManager.load();
    }

    private void refreshTextureManager() {
        OverlayConfig cfg = selectedConfig();
        if (cfg != null) this.textureManager = ImageTextureManager.forOverlay(cfg.id);
    }

    // ==================== Widget rebuild ====================

    protected void rebuildWidgets() {
        this.clearWidgets();
        this.init();
    }

    // ==================== init ====================

    @Override
    protected void init() {
        super.init();
        invalidatePreviewCache();
        if (selectedIndex < 0 && !overlayConfigs.isEmpty()) selectedIndex = 0;
        if (selectedIndex >= overlayConfigs.size()) selectedIndex = overlayConfigs.size() - 1;
        refreshTextureManager();

        int editStartX = OVERLAY_LIST_WIDTH + 20;
        int startY = 30;
        int rowHeight = 22;

        // ---- Left: overlay list buttons ----
        int listY = startY;
        for (int i = 0; i < overlayConfigs.size(); i++) {
            final int idx = i;
            OverlayConfig cfg = overlayConfigs.get(i);
            String label = cfg.enabled ? "§a" : "§7";
            label += cfg.sourcePath.isBlank() ? "(empty)" : truncate(cfg.sourcePath, 14);
            Button btn = Button.builder(Component.literal(label), b -> selectOverlay(idx))
                    .bounds(5, listY, OVERLAY_LIST_WIDTH - 10, 18).build();
            this.addRenderableWidget(btn);
            listY += 20;
        }
        this.addRenderableWidget(Button.builder(Component.literal("+ ").append(ADD_OVERLAY_LABEL), b -> addOverlay())
                .bounds(5, listY + 4, OVERLAY_LIST_WIDTH - 10, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("- ").append(REMOVE_OVERLAY_LABEL), b -> removeOverlay())
                .bounds(5, listY + 24, OVERLAY_LIST_WIDTH - 10, 18).build());

        if (overlayConfigs.isEmpty()) return;

        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;

        // ---- Media type + Enabled ----
        Button typeBtn = Button.builder(mediaTypeLabel(cfg), btn -> { cfg.mediaType = nextMediaType(cfg.mediaType); btn.setMessage(mediaTypeLabel(cfg)); rebuildWidgets(); })
                .bounds(editStartX, startY, 140, 18).build();
        this.addRenderableWidget(typeBtn);
        Button enabledBtn = Button.builder(enabledLabel(cfg), btn -> { cfg.enabled = !cfg.enabled; btn.setMessage(enabledLabel(cfg)); rebuildWidgets(); })
                .bounds(editStartX + 145, startY, 155, 18).build();
        this.addRenderableWidget(enabledBtn);
        startY += rowHeight + 6;

        // ---- Source path + Browse (side by side so they never overlap) ----
        int browseButtonWidth = 70;
        int pathFieldWidth = Math.max(220, this.width - editStartX - browseButtonWidth - 20);
        this.pathField = new EditBox(this.font, editStartX, startY, pathFieldWidth, 18, PATH_LABEL);
        this.pathField.setValue(cfg.sourcePath);
        this.pathField.setMaxLength(32767); // effectively unlimited for any realistic file path/URL
        if (!cfg.sourcePath.isBlank()) this.pathField.setTooltip(Tooltip.create(Component.literal(cfg.sourcePath)));
        this.pathField.setResponder(this::onPathFieldChanged);
        this.addRenderableWidget(this.pathField);
        this.addRenderableWidget(Button.builder(Component.translatable("config.mirage.browse"), b -> openFilePicker())
                .bounds(editStartX + pathFieldWidth + 5, startY, browseButtonWidth, 18).build());
        startY += rowHeight + 6;

        // ---- Position & Size ----
        this.posXField = makeIntField(editStartX, startY, 60, cfg.posX, OverlayConfigScreen::isInteger);
        this.posXField.setResponder(v -> { if (!updatingFields) cfg.posX = parseInt(v, cfg.posX); });
        this.posYField = makeIntField(editStartX + 65, startY, 60, cfg.posY, OverlayConfigScreen::isInteger);
        this.posYField.setResponder(v -> { if (!updatingFields) cfg.posY = parseInt(v, cfg.posY); });
        this.widthField = makeIntField(editStartX + 130, startY, 60, cfg.width, OverlayConfigScreen::isPositiveInteger);
        this.widthField.setResponder(this::onWidthFieldChanged);
        this.heightField = makeIntField(editStartX + 195, startY, 60, cfg.height, OverlayConfigScreen::isPositiveInteger);
        this.heightField.setResponder(this::onHeightFieldChanged);
        startY += rowHeight + 6;

        // ---- Opacity ----
        this.opacitySlider = new AbstractSliderButton(editStartX, startY, 295, 18, OPACITY_LABEL, cfg.opacity) {
            @Override protected void updateMessage() { setMessage(Component.translatable("config.mirage.opacity_value", (int)(this.value * 100))); }
            @Override protected void applyValue() { selectedConfig().opacity = (float) this.value; }
        };
        this.addRenderableWidget(this.opacitySlider);
        startY += rowHeight + 6;

        // ---- Volume (video only) ----
        if (cfg.isVideo()) {
            this.volumeSlider = new AbstractSliderButton(editStartX, startY, 295, 18, VOLUME_LABEL, cfg.volume) {
                @Override protected void updateMessage() { setMessage(Component.translatable("config.mirage.volume_value", (int)(this.value * 100))); }
                @Override protected void applyValue() { selectedConfig().volume = (float) this.value; }
            };
            this.addRenderableWidget(this.volumeSlider);
            startY += rowHeight + 6;
        }

        // ---- Play/Pause + Loop (video only) ----
        if (cfg.isVideo()) {
            Button ppBtn = Button.builder(playPauseLabel(cfg), btn -> { cfg.playing = !cfg.playing; btn.setMessage(playPauseLabel(cfg)); })
                    .bounds(editStartX, startY, 145, 18).build();
            this.addRenderableWidget(ppBtn);
            Button loopBtn = Button.builder(loopLabel(cfg), btn -> { cfg.loop = !cfg.loop; btn.setMessage(loopLabel(cfg)); })
                    .bounds(editStartX + 150, startY, 145, 18).build();
            this.addRenderableWidget(loopBtn);
            startY += rowHeight + 6;
        }

        // ---- Anchor + Lock aspect ----
        Button anchorBtn = Button.builder(anchorLabel(cfg), btn -> { cfg.anchor = nextAnchor(cfg.anchor); btn.setMessage(anchorLabel(cfg)); })
                .bounds(editStartX, startY, 145, 18).build();
        this.addRenderableWidget(anchorBtn);
        Button lockBtn = Button.builder(lockAspectLabel(cfg), btn -> { cfg.lockAspectRatio = !cfg.lockAspectRatio; btn.setMessage(lockAspectLabel(cfg)); if (cfg.lockAspectRatio) syncHeightToWidth(); })
                .bounds(editStartX + 150, startY, 145, 18).build();
        this.addRenderableWidget(lockBtn);
        startY += rowHeight + 6;

        // ---- Drag mode ----
        Button dragBtn = Button.builder(Component.translatable("config.mirage.drag_mode"), b -> enterDragMode())
                .bounds(editStartX, startY, 295, 18)
                .tooltip(Tooltip.create(Component.translatable("config.mirage.drag_mode_tooltip"))).build();
        this.addRenderableWidget(dragBtn);
        startY += rowHeight + 10;

        // ---- Preset controls ----
        this.presetNameField = new EditBox(this.font, editStartX, startY, 140, 18, Component.translatable("config.mirage.preset_name"));
        this.presetNameField.setMaxLength(32);
        this.addRenderableWidget(this.presetNameField);
        this.addRenderableWidget(Button.builder(Component.translatable("config.mirage.save_preset"), b -> savePreset()).bounds(editStartX + 145, startY, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.mirage.delete_preset"), b -> deletePreset()).bounds(editStartX + 210, startY, 60, 18).build());
        startY += rowHeight + 6;
        this.addRenderableWidget(Button.builder(Component.translatable("config.mirage.load_prev"), b -> loadPreset(-1)).bounds(editStartX, startY, 100, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("config.mirage.load_next"), b -> loadPreset(1)).bounds(editStartX + 105, startY, 100, 18).build());
        startY += rowHeight + 10;

        // Browse button is now placed next to the source path field above

        // ---- Done / Cancel ----
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(editStartX, startY, 145, 18).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onCancel()).bounds(editStartX + 150, startY, 145, 18).build());
    }

    // ==================== Overlay management ====================

    private void selectOverlay(int idx) {
        if (idx < 0 || idx >= overlayConfigs.size()) return;
        this.selectedIndex = idx;
        rebuildWidgets();
    }
    private void addOverlay() {
        overlayConfigs.add(new OverlayConfig());
        selectedIndex = overlayConfigs.size() - 1;
        rebuildWidgets();
    }
    private void removeOverlay() {
        if (overlayConfigs.size() <= 1) return;
        if (selectedIndex < 0 || selectedIndex >= overlayConfigs.size()) return;
        ImageTextureManager.removeOverlay(overlayConfigs.get(selectedIndex).id);
        overlayConfigs.remove(selectedIndex);
        if (selectedIndex >= overlayConfigs.size()) selectedIndex = overlayConfigs.size() - 1;
        rebuildWidgets();
    }
    private OverlayConfig selectedConfig() {
        if (selectedIndex < 0 || selectedIndex >= overlayConfigs.size()) return null;
        return overlayConfigs.get(selectedIndex);
    }

    // ==================== Responders ====================

    private void onPathFieldChanged(String value) {
        if (updatingFields) return;
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        cfg.sourcePath = value;
        if (!value.isBlank() && !autoScaledPaths.contains(value) && !cfg.isVideo()) {
            ImageTextureManager mgr = ImageTextureManager.forOverlay(cfg.id);
            if (mgr.updateSource(value) && mgr.hasTexture()) {
                autoScaleImage(cfg, mgr); autoScaledPaths.add(value);
            }
        }
    }
    private void onWidthFieldChanged(String value) {
        if (updatingFields) return;
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        cfg.width = Math.max(1, parseInt(value, cfg.width));
        invalidatePreviewCache();
        if (cfg.lockAspectRatio) syncHeightToWidth();
    }
    private void onHeightFieldChanged(String value) {
        if (updatingFields) return;
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        cfg.height = Math.max(1, parseInt(value, cfg.height));
        invalidatePreviewCache();
        if (cfg.lockAspectRatio) syncWidthToHeight();
    }

    private void autoScaleImage(OverlayConfig cfg, ImageTextureManager mgr) {
        if (mgr == null) return;
        int ow = mgr.getOriginalWidth(), oh = mgr.getOriginalHeight();
        if (ow <= 0 || oh <= 0) return;
        if (cfg == null) return;
        int sw = Math.max(1, this.width), sh = Math.max(1, this.height);
        float s = Math.min(1f, Math.min(sw * IMAGE_IMPORT_MAX_SCREEN_FRACTION / ow, sh * IMAGE_IMPORT_MAX_SCREEN_FRACTION / oh));
        cfg.width = Math.max(IMAGE_IMPORT_MIN_SIZE, Math.round(ow * s));
        cfg.height = Math.max(IMAGE_IMPORT_MIN_SIZE, Math.round(oh * s));
        invalidatePreviewCache();
        updatingFields = true;
        if (widthField != null) widthField.setValue(String.valueOf(cfg.width));
        if (heightField != null) heightField.setValue(String.valueOf(cfg.height));
        updatingFields = false;
        clampPositionToScreen();
    }
    private void invalidatePreviewCache() { cachedPreviewWidth = -1; }

    // ==================== Drag mode ====================
    private void enterDragMode() { syncAllFieldsToConfig(); dragMode = true; dragging = false; }
    private void exitDragMode() { dragMode = false; dragging = false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (dragMode) { renderDragMode(graphics); return; }
        // Render preview BEFORE widgets so it sits under everything (buttons, fields, labels)
        renderPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 8, 0xFFFFFFFF, true);
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        int ex = OVERLAY_LIST_WIDTH + 20;
        graphics.drawString(this.font, MEDIA_TYPE_LABEL, ex, 32, 0xFFFFFFFF, true);
        graphics.drawString(this.font, ENABLED_LABEL, ex + 150, 32, 0xFFFFFFFF, true);
        graphics.drawString(this.font, PATH_LABEL, ex, 56, 0xFFFFFFFF, true);
        graphics.drawString(this.font, POS_X_LABEL, ex, 80, 0xFFFFFFFF, true);
        graphics.drawString(this.font, POS_Y_LABEL, ex + 65, 80, 0xFFFFFFFF, true);
        graphics.drawString(this.font, WIDTH_LABEL, ex + 130, 80, 0xFFFFFFFF, true);
        graphics.drawString(this.font, HEIGHT_LABEL, ex + 195, 80, 0xFFFFFFFF, true);
        if (presetNameField != null) graphics.drawString(this.font, PRESET_NAME_LABEL, presetNameField.getX(), presetNameField.getY() - font.lineHeight - 2, 0xFFFFFFFF, true);
    }

    private void renderDragMode(GuiGraphics graphics) {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) { exitDragMode(); return; }
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        int ax = HudRenderingEntrypoint.calculateX(cfg, this.width), ay = HudRenderingEntrypoint.calculateY(cfg, this.height);
        graphics.renderOutline(ax - 2, ay - 2, cfg.width + 4, cfg.height + 4, 0x6600FF00);
        graphics.renderOutline(ax - 1, ay - 1, cfg.width + 2, cfg.height + 2, 0xFF00FF00);
        if (textureManager != null && textureManager.hasTexture())
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureManager.getTextureId(), ax, ay, 0, 0, cfg.width, cfg.height, cfg.width, cfg.height, cfg.getColor());
        int cx = ax + cfg.width / 2, cy = ay + cfg.height / 2;
        graphics.renderOutline(cx - 1, cy - 6, 2, 12, 0xFFFFFFFF);
        graphics.renderOutline(cx - 6, cy - 1, 12, 2, 0xFFFFFFFF);
        int lg = font.lineHeight + 4, bh = lg * 3, ty = ay + cfg.height + 8;
        if (ty + bh > this.height - 4) ty = Math.max(4, ay - bh - 8);
        Component hint = Component.translatable("config.mirage.drag_hint");
        Component rch = Component.translatable("config.mirage.drag_recenter_hint");
        Component info = Component.translatable("config.mirage.drag_position_info", cfg.posX, cfg.posY, cfg.width, cfg.height, Component.translatable(cfg.anchor.getTranslationKey()));
        graphics.drawString(font, hint, (width - font.width(hint)) / 2, ty, 0xFFFFFFFF, true);
        graphics.drawString(font, rch, (width - font.width(rch)) / 2, ty + lg, 0xFFFFFFFF, true);
        graphics.drawString(font, info, (width - font.width(info)) / 2, ty + lg * 2, 0xFFFFFFFF, true);
    }

    private void renderPreview(GuiGraphics graphics) {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        int dw = cfg.width, dh = cfg.height; if (dw <= 0 || dh <= 0) return;
        int maxP = 150;
        if (cachedPreviewWidth < 0) {
            float s = Math.min(1f, Math.min((float)maxP / dw, (float)maxP / dh));
            cachedPreviewWidth = Math.max(1, Math.round(dw * s));
            cachedPreviewHeight = Math.max(1, Math.round(dh * s));
            cachedPreviewX = this.width - cachedPreviewWidth - 20;
            cachedPreviewY = 50;
        }
        graphics.drawString(font, PREVIEW_LABEL, cachedPreviewX, cachedPreviewY - 38, 0xFFFFFFFF, true);
        if (textureManager != null && textureManager.hasTexture()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureManager.getTextureId(), cachedPreviewX, cachedPreviewY, 0, 0, cachedPreviewWidth, cachedPreviewHeight, cachedPreviewWidth, cachedPreviewHeight, cfg.getColor());
        }
    }

    // ---- Input ----
    @Override public boolean keyPressed(KeyEvent event) {
        if (dragMode) { int k = event.key(); if (k == InputConstants.KEY_ESCAPE || k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) { exitDragMode(); return true; } return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dc) {
        if (dragMode) { if (event.button() == 1) { recenterOverlay(); return true; } if (event.button() == 0 && isMouseOverOverlay(event.x(), event.y())) { startDrag(event.x(), event.y()); return true; } return true; }
        return super.mouseClicked(event, dc);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { if (dragMode && dragging && event.button() == 0) { dragging = false; return true; } return super.mouseReleased(event); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragMode && dragging && event.button() == 0) {
            double rdx = event.x() - dragLastMouseX + dragFractionalX, rdy = event.y() - dragLastMouseY + dragFractionalY;
            dragLastMouseX = event.x(); dragLastMouseY = event.y();
            int ix = (int)rdx, iy = (int)rdy; dragFractionalX = rdx - ix; dragFractionalY = rdy - iy;
            applyDragDelta(ix, iy); return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    private void startDrag(double mx, double my) { dragging = true; dragLastMouseX = mx; dragLastMouseY = my; dragFractionalX = dragFractionalY = 0; }
    private boolean isMouseOverOverlay(double mx, double my) {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return false;
        int ax = HudRenderingEntrypoint.calculateX(cfg, width), ay = HudRenderingEntrypoint.calculateY(cfg, height);
        return mx >= ax - DRAG_HITBOX_SLACK && mx < ax + cfg.width + DRAG_HITBOX_SLACK && my >= ay - DRAG_HITBOX_SLACK && my < ay + cfg.height + DRAG_HITBOX_SLACK;
    }
    private void applyDragDelta(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        int sx = HudRenderingEntrypoint.calculateX(cfg, width) + dx, sy = HudRenderingEntrypoint.calculateY(cfg, height) + dy;
        int minX = DRAG_MIN_VISIBLE_PX - cfg.width, minY = DRAG_MIN_VISIBLE_PX - cfg.height;
        sx = clamp(Math.max(minX, width - DRAG_MIN_VISIBLE_PX), minX, sx); sy = clamp(Math.max(minY, height - DRAG_MIN_VISIBLE_PX), minY, sy);
        setPositionFromScreen(sx, sy); syncPosFields();
    }
    private void setPositionFromScreen(int sx, int sy) {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        int ow = cfg.width, oh = cfg.height;
        cfg.posX = switch (cfg.anchor) { case TOP_RIGHT, BOTTOM_RIGHT -> width - ow - sx; case CENTER -> sx - (width - ow) / 2; default -> sx; };
        cfg.posY = switch (cfg.anchor) { case BOTTOM_LEFT, BOTTOM_RIGHT -> height - oh - sy; case CENTER -> sy - (height - oh) / 2; default -> sy; };
    }
    private void clampPositionToScreen() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        int sx = HudRenderingEntrypoint.calculateX(cfg, width), sy = HudRenderingEntrypoint.calculateY(cfg, height);
        sx = clamp(Math.max(DRAG_MIN_VISIBLE_PX - cfg.width, width - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - cfg.width, sx);
        sy = clamp(Math.max(DRAG_MIN_VISIBLE_PX - cfg.height, height - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - cfg.height, sy);
        setPositionFromScreen(sx, sy); syncPosFields();
    }
    private void recenterOverlay() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        int ow = cfg.width, oh = cfg.height, sx, sy;
        sx = switch (cfg.anchor) { case TOP_RIGHT -> width - ow; case CENTER -> (width - ow) / 2; default -> 0; };
        sy = switch (cfg.anchor) { case BOTTOM_LEFT, BOTTOM_RIGHT -> height - oh; case CENTER -> (height - oh) / 2; default -> 0; };
        sx = clamp(Math.max(DRAG_MIN_VISIBLE_PX - ow, width - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - ow, sx);
        sy = clamp(Math.max(DRAG_MIN_VISIBLE_PX - oh, height - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - oh, sy);
        setPositionFromScreen(sx, sy); syncPosFields();
    }
    private void syncPosFields() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        updatingFields = true;
        if (posXField != null) posXField.setValue(String.valueOf(cfg.posX));
        if (posYField != null) posYField.setValue(String.valueOf(cfg.posY));
        updatingFields = false;
    }
    private void syncAllFieldsToConfig() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        updatingFields = true;
        if (posXField != null) posXField.setValue(String.valueOf(cfg.posX));
        if (posYField != null) posYField.setValue(String.valueOf(cfg.posY));
        if (widthField != null) widthField.setValue(String.valueOf(cfg.width));
        if (heightField != null) heightField.setValue(String.valueOf(cfg.height));
        updatingFields = false;
    }
    private static int clamp(int max, int min, int v) { return Math.max(min, Math.min(v, max)); }

    // ---- File picker ----
    private static final List<String> IMAGE_EXTS = List.of("png", "jpg", "jpeg", "webp", "tga", "bmp", "gif");
    private static final List<String> VIDEO_EXTS = List.of("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv");

    private void openFilePicker() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null) return;
        if (!pickerOpen.compareAndSet(false, true)) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("config.mirage.picker_already_open_title"),
                    Component.translatable("config.mirage.picker_already_open_message"));
            return;
        }

        // Run the native dialog on a background thread so the render thread
        // (and thus the whole game) doesn't freeze while the dialog is open.
        Thread pickerThread = new Thread(() -> {
            try {
                runFilePicker(cfg);
            } catch (Throwable t) {
                Mirage.LOGGER.error("Unexpected error in file picker", t);
                pickerOpen.set(false);
            }
        }, "Overlay-FilePicker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private void runFilePicker(OverlayConfig cfg) {
        boolean isVideo = cfg.isVideo();
        String path = null;

        // Try LWJGL TinyFileDialogs
        String pickedPath = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<String> wildcardExts = new ArrayList<>();
            for (String ext : (isVideo ? VIDEO_EXTS : IMAGE_EXTS)) wildcardExts.add("*." + ext);
            PointerBuffer filters = stack.mallocPointer(wildcardExts.size());
            for (String ext : wildcardExts) filters.put(stack.UTF8(ext));
            filters.flip();
            pickedPath = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select " + (isVideo ? "Video" : "Image"),
                    System.getProperty("user.home"),
                    filters,
                    isVideo ? "Media Files" : "Image Files",
                    false
            );
            Mirage.LOGGER.info("TinyFileDialogs picked: {}", pickedPath);
        } catch (Throwable t) {
            Mirage.LOGGER.error("TinyFileDialogs file picker failed", t);
        }

        // Validate result on the render thread
        final String finalPath = pickedPath;
        Minecraft.getInstance().execute(() -> handlePickedPath(cfg, finalPath));
    }

    private void handlePickedPath(OverlayConfig cfg, String path) {
        try {
            if (isValidPickedPath(path, cfg.isVideo())) {
                applyPickedFile(cfg, path);
            } else {
                Mirage.LOGGER.warn("No valid file was picked");
                SystemToast.add(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("config.mirage.paste_path_hint_title"),
                        Component.translatable("config.mirage.paste_path_hint_message"));
            }
        } finally {
            pickerOpen.set(false);
        }
    }

    private boolean isValidPickedPath(String path, boolean isVideo) {
        if (path == null || path.isBlank()) return false;
        File f = new File(path);
        if (!f.exists() || f.isDirectory()) return false;
        String name = f.getName().toLowerCase();
        for (String ext : (isVideo ? VIDEO_EXTS : IMAGE_EXTS)) {
            if (name.endsWith("." + ext)) return true;
        }
        return false;
    }

    private void applyPickedFile(OverlayConfig cfg, String path) {
        if (cfg == null) return;
        path = FilePathUtil.resolve(path);
        Mirage.LOGGER.info("Applying picked file path: {}", path);
        updatingFields = true; pathField.setValue(path); updatingFields = false;
        cfg.sourcePath = path;
        if (!cfg.isVideo() && !autoScaledPaths.contains(path)) {
            ImageTextureManager mgr = ImageTextureManager.forOverlay(cfg.id);
            if (mgr.updateSource(path) && mgr.hasTexture()) {
                autoScaleImage(cfg, mgr); autoScaledPaths.add(path);
            }
        }
    }

    // ---- Aspect ratio ----
    private void syncHeightToWidth() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null || widthField == null || heightField == null) return;
        int w = parseInt(widthField.getValue(), cfg.width), h = computeHeightForWidth(w);
        if (h > 0) { updatingFields = true; heightField.setValue(String.valueOf(h)); updatingFields = false; }
    }
    private void syncWidthToHeight() {
        OverlayConfig cfg = selectedConfig(); if (cfg == null || widthField == null || heightField == null) return;
        int h = parseInt(heightField.getValue(), cfg.height), w = computeWidthForHeight(h);
        if (w > 0) { updatingFields = true; widthField.setValue(String.valueOf(w)); updatingFields = false; }
    }
    private int computeHeightForWidth(int w) {
        if (textureManager == null) return -1;
        int ow = textureManager.getOriginalWidth(), oh = textureManager.getOriginalHeight();
        return (ow <= 0 || oh <= 0) ? -1 : Math.max(1, Math.round(w * ((float)oh / ow)));
    }
    private int computeWidthForHeight(int h) {
        if (textureManager == null) return -1;
        int ow = textureManager.getOriginalWidth(), oh = textureManager.getOriginalHeight();
        return (ow <= 0 || oh <= 0) ? -1 : Math.max(1, Math.round(h * ((float)ow / oh)));
    }

    // ---- Presets ----
    private void savePreset() {
        if (presetNameField == null) return;
        String name = presetNameField.getValue(); OverlayConfig cfg = selectedConfig();
        if (name.isBlank() || cfg == null) return;
        if (OverlayPresetManager.savePreset(name, cfg)) {
            presetNameField.setValue("");
            SystemToast.add(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("config.mirage.preset_saved_title"), Component.literal(name));
        }
    }
    private void deletePreset() {
        if (presetNameField == null) return;
        String name = presetNameField.getValue(); if (name.isBlank()) return;
        OverlayPresetManager.deletePreset(name); presetNameField.setValue("");
        SystemToast.add(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("config.mirage.preset_deleted_title"), Component.literal(name));
    }
    private void loadPreset(int dir) {
        java.util.List<String> names = OverlayPresetManager.getPresetNames(); if (names.isEmpty()) return;
        String cur = presetNameField != null ? presetNameField.getValue() : "";
        int idx = names.indexOf(cur); idx = idx < 0 ? 0 : (idx + dir) % names.size(); if (idx < 0) idx = names.size() - 1;
        String sel = names.get(idx); OverlayPreset p = OverlayPresetManager.getPreset(sel);
        OverlayConfig cfg = selectedConfig();
        if (p != null && p.getConfig() != null && cfg != null) {
            restoreConfig(cfg, p.getConfig());
            if (presetNameField != null) presetNameField.setValue(sel);
            if (!cfg.isVideo() && textureManager != null) textureManager.updateSource(cfg.sourcePath);
            autoScaledPaths.add(cfg.sourcePath);
            updatingFields = true;
            if (pathField != null) pathField.setValue(cfg.sourcePath);
            if (posXField != null) posXField.setValue(String.valueOf(cfg.posX));
            if (posYField != null) posYField.setValue(String.valueOf(cfg.posY));
            if (widthField != null) widthField.setValue(String.valueOf(cfg.width));
            if (heightField != null) heightField.setValue(String.valueOf(cfg.height));
            updatingFields = false; invalidatePreviewCache();
        }
    }

    @Override public void onClose() {
        syncAllFieldsToConfig();
        // Normalize all source paths before saving
        for (OverlayConfig cfg : overlayConfigs) {
            if (!cfg.sourcePath.isBlank()) {
                cfg.sourcePath = FilePathUtil.resolve(cfg.sourcePath);
            }
        }
        OverlayConfigManager.getRootConfig().overlays.clear();
        OverlayConfigManager.getRootConfig().overlays.addAll(overlayConfigs);
        OverlayConfigManager.save();
        HudRenderingEntrypoint.syncProviders();
        Minecraft.getInstance().setScreen(parent);
    }
    private void onCancel() {
        overlayConfigs.clear();
        for (OverlayConfig c : initialConfigs) overlayConfigs.add(OverlayConfigManager.copyConfig(c));
        OverlayConfigManager.getRootConfig().overlays.clear();
        OverlayConfigManager.getRootConfig().overlays.addAll(overlayConfigs);
        OverlayConfigManager.save();
        HudRenderingEntrypoint.syncProviders();
        Minecraft.getInstance().setScreen(parent);
    }
    private void restoreConfig(OverlayConfig to, OverlayConfig from) {
        to.mediaType = from.mediaType; to.enabled = from.enabled; to.sourcePath = from.sourcePath;
        to.posX = from.posX; to.posY = from.posY; to.width = from.width; to.height = from.height;
        to.opacity = from.opacity; to.volume = from.volume; to.anchor = from.anchor;
        to.lockAspectRatio = from.lockAspectRatio; to.playing = from.playing;
        to.loop = from.loop;
        to.options = new HashMap<>(from.options);
    }

    private EditBox makeIntField(int x, int y, int w, int val, Predicate<String> filter) {
        EditBox eb = new EditBox(font, x, y, w, 18, Component.empty());
        eb.setValue(String.valueOf(val)); eb.setFilter(filter); this.addRenderableWidget(eb); return eb;
    }

    // ---- Labels ----
    private Component enabledLabel(OverlayConfig c) { return Component.translatable(c.enabled ? "config.mirage.enabled_on" : "config.mirage.enabled_off"); }
    private Component anchorLabel(OverlayConfig c) { return Component.translatable("config.mirage.anchor_value", Component.translatable(c.anchor.getTranslationKey())); }
    private Component lockAspectLabel(OverlayConfig c) { return Component.translatable(c.lockAspectRatio ? "config.mirage.lock_aspect_on" : "config.mirage.lock_aspect_off"); }
    private Component mediaTypeLabel(OverlayConfig c) { return Component.translatable("config.mirage.media_type_value", Component.translatable("config.mirage.media_type_" + c.mediaType)); }
    private Component playPauseLabel(OverlayConfig c) { return Component.translatable(c.playing ? "config.mirage.pause" : "config.mirage.play"); }
    private Component loopLabel(OverlayConfig c) { return Component.translatable(c.loop ? "config.mirage.loop_on" : "config.mirage.loop_off"); }

    private static OverlayConfig.Anchor nextAnchor(OverlayConfig.Anchor cur) { OverlayConfig.Anchor[] v = OverlayConfig.Anchor.values(); return v[(cur.ordinal() + 1) % v.length]; }
    private static String nextMediaType(String cur) { return switch (cur) { case "image" -> "video"; default -> "image"; }; }
    private static String truncate(String s, int max) { return s.isBlank() ? "(empty)" : (s.length() > max ? "..." + s.substring(s.length() - max + 3) : s); }
    private static boolean isInteger(String v) { if (v.isEmpty() || v.equals("-") || v.equals("+")) return true; try { Integer.parseInt(v); return true; } catch (NumberFormatException e) { return false; } }
    private static boolean isPositiveInteger(String v) { if (v.isEmpty()) return true; try { return Integer.parseInt(v) >= 0; } catch (NumberFormatException e) { return false; } }
    private static int parseInt(String v, int fb) { try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fb; } }
}
