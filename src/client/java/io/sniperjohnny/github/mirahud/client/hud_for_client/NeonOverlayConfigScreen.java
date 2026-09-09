package io.sniperjohnny.github.mirahud.client.hud_for_client;

import com.mojang.blaze3d.platform.InputConstants;
import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.overlay.ImageTextureManager;
import io.sniperjohnny.github.mirahud.client.overlay.MediaProvider;
import io.sniperjohnny.github.mirahud.client.overlay.config.FilePathUtil;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfig;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfigManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayPreset;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayPresetManager;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import io.sniperjohnny.github.mirahud.client.util.McScreens;
import io.sniperjohnny.github.mirahud.client.widgets.FilteredEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class NeonOverlayConfigScreen extends NeonScreen {

    private static final Component TITLE = Component.translatable(TranslationsKeys.CONFIG_TITLE);
    private static final Component ENABLED_LABEL = Component.translatable(TranslationsKeys.CONFIG_ENABLED);
    private static final Component PATH_LABEL = Component.translatable(TranslationsKeys.CONFIG_SOURCE_PATH);
    private static final Component OPACITY_LABEL = Component.translatable(TranslationsKeys.CONFIG_OPACITY);
    private static final Component VOLUME_LABEL = Component.translatable(TranslationsKeys.CONFIG_VOLUME);
    private static final Component POS_X_LABEL = Component.translatable(TranslationsKeys.CONFIG_POS_X);
    private static final Component POS_Y_LABEL = Component.translatable(TranslationsKeys.CONFIG_POS_Y);
    private static final Component WIDTH_LABEL = Component.translatable(TranslationsKeys.CONFIG_WIDTH);
    private static final Component HEIGHT_LABEL = Component.translatable(TranslationsKeys.CONFIG_HEIGHT);
    private static final Component ANCHOR_LABEL = Component.translatable(TranslationsKeys.CONFIG_ANCHOR);
    private static final Component LOCK_ASPECT_LABEL = Component.translatable(TranslationsKeys.CONFIG_LOCK_ASPECT_RATIO);
    private static final Component PREVIEW_LABEL = Component.translatable(TranslationsKeys.CONFIG_PREVIEW);
    private static final Component PRESET_NAME_LABEL = Component.translatable(TranslationsKeys.CONFIG_PRESET_NAME_LABEL);
    private static final Component ADD_OVERLAY_LABEL = Component.translatable(TranslationsKeys.CONFIG_ADD_OVERLAY);
    private static final Component REMOVE_OVERLAY_LABEL = Component.translatable(TranslationsKeys.CONFIG_REMOVE_OVERLAY);
    private static final Component MEDIA_TYPE_LABEL = Component.translatable(TranslationsKeys.CONFIG_MEDIA_TYPE);

    private static final int DRAG_HITBOX_SLACK = 10;
    private static final int DRAG_MIN_VISIBLE_PX = 24;
    private static final float IMAGE_IMPORT_MAX_SCREEN_FRACTION = 0.35f;
    private static final int IMAGE_IMPORT_MIN_SIZE = 16;
    private static final int OVERLAY_LIST_WIDTH = 130;
    private static final int TREE_TOP = 66;
    private static final int ROW_SPACING = 24;

    // Horizontal center of the tree (trunk + branches + stump), set in init().
    private int trunkCenterX;

    private final Screen parent;
    private final List<OverlayConfig> overlayConfigs;
    private final List<OverlayConfig> initialConfigs;
    private int selectedIndex;
    private ImageTextureManager textureManager;

    private EditBox pathField;
    private EditBox posXField;
    private EditBox posYField;
    private EditBox widthField;
    private EditBox heightField;
    private NeonSlider opacitySlider;
    private NeonSlider volumeSlider;
    private SeekSlider videoSeekSlider;
    private EditBox presetNameField;

    private final Map<EditBox, Integer> fieldColors = new HashMap<>();
    private long fieldLerpLastNanos = System.nanoTime();
    private boolean updatingFields;
    private boolean dragMode;
    private boolean dragging;
    private double dragLastMouseX, dragLastMouseY, dragFractionalX, dragFractionalY;
    private final Set<String> autoScaledPaths = new HashSet<>();
    private final AtomicBoolean pickerOpen = new AtomicBoolean(false);

    private int cachedPreviewWidth = -1, cachedPreviewHeight = -1, cachedPreviewX, cachedPreviewY;

    // Settings panel scrolling: on short screens the panel is longer than the
    // window, so it scrolls with the mouse wheel. Widgets are repositioned in
    // place (rendering and hit-testing both follow), which keeps every control
    // reachable at any GUI scale.
    private double settingsScroll;
    private int settingsMaxScroll;
    private final List<WidgetSlot> scrollSlots = new ArrayList<>();
    private NeonButton mediaTypeButton;
    private NeonButton enabledButton;

    private record WidgetSlot(AbstractWidget widget, int naturalY) {}

    private <T extends AbstractWidget> T track(T widget) {
        scrollSlots.add(new WidgetSlot(widget, widget.getY()));
        this.addRenderableWidget(widget);
        return widget;
    }

    private void applySettingsScroll() {
        int off = (int) Math.round(settingsScroll);
        for (WidgetSlot slot : scrollSlots) {
            slot.widget().setY(slot.naturalY() - off);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (selectedIndex < 0 || settingsMaxScroll <= 0) return;
        int trackX = this.width - 6;
        int trackY = 42;
        int trackH = this.height - 54;
        if (trackH < 24) return;
        graphics.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x33C9A05B);
        int thumbH = Math.max(16, (int) (trackH * (trackH / (double) (trackH + settingsMaxScroll))));
        int thumbY = trackY + (int) ((trackH - thumbH) * (settingsScroll / Math.max(1, settingsMaxScroll)));
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, NeonScreen.FOLIAGE);
    }

    public NeonOverlayConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        List<OverlayConfig> list = OverlayConfigManager.getRootConfig().overlays;
        this.overlayConfigs = new ArrayList<>(list);
        this.initialConfigs = new ArrayList<>();
        for (OverlayConfig c : list) this.initialConfigs.add(OverlayConfigManager.copyConfig(c));
        // Nothing selected until a branch is clicked (click-to-open).
        this.selectedIndex = -1;
        OverlayPresetManager.load();
    }

    private void refreshTextureManager() {
        OverlayConfig cfg = selectedConfig();
        if (cfg != null) this.textureManager = ImageTextureManager.forOverlay(cfg.id);
    }

    protected void rebuildWidgets() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawTree(graphics);
    }

    private void drawTree(GuiGraphicsExtractor graphics) {
        // The tree only exists in the stump view: a clean trunk running down
        // the middle of the screen into the big clickable stump. When a branch
        // is selected the settings panel replaces the tree.
        if (selectedIndex >= 0) return;
        int trunkBottom = TREE_TOP + overlayConfigs.size() * ROW_SPACING + 8;
        int tx = trunkCenterX - TreeBranchWidget.TRUNK_W / 2;
        graphics.fill(tx, TREE_TOP - 2, tx + 3, trunkBottom, NeonScreen.WOOD);
        graphics.fill(tx + 3, TREE_TOP - 2, tx + 4, trunkBottom, NeonScreen.WOOD_DARK);
    }

    @Override
    protected void init() {
        super.init();
        trunkCenterX = this.width / 2;
        fieldColors.clear();
        invalidatePreviewCache();
        if (selectedIndex >= overlayConfigs.size()) selectedIndex = overlayConfigs.size() - 1;
        refreshTextureManager();

        int editStartX = OVERLAY_LIST_WIDTH + 20;
        int startY = 42;
        int rowHeight = 22;

        // Top bar: when a branch is selected show Back + Remove, otherwise the
        // stump-view help text is drawn in render().
        if (selectedIndex >= 0) {
            this.addRenderableWidget(new NeonButton(5, 44, 60, 18, Component.literal("< Back"), this::backToStump));
            this.addRenderableWidget(new NeonButton(70, 44, 55, 18,
                    Component.literal("- ").append(REMOVE_OVERLAY_LABEL), this::removeOverlay));
        }

        // The tree (branches + stump) only shows in the stump view. It grows
        // from the middle of the screen: trunk down the center, branches
        // alternating left/right, big stump at the base.
        if (selectedIndex < 0) {
            int branchY = TREE_TOP;
            for (int i = 0; i < overlayConfigs.size(); i++) {
                final int idx = i;
                OverlayConfig cfg = overlayConfigs.get(i);
                String prefix = cfg.enabled ? "§a" : "§7";
                Component label = Component.literal(prefix).append(cfg.sourcePath.isBlank()
                        ? Component.translatable(TranslationsKeys.CONFIG_EMPTY)
                        : Component.literal(truncate(cfg.sourcePath, 18)));
                boolean branchLeft = (i % 2 == 0);
                int inner = TreeBranchWidget.TRUNK_W / 2;
                int tipX = branchLeft ? trunkCenterX - inner - TreeBranchWidget.BRANCH_LEN
                                      : trunkCenterX + inner + TreeBranchWidget.BRANCH_LEN;
                int wX = branchLeft ? tipX - 8 : trunkCenterX - inner;
                int wW = branchLeft ? (trunkCenterX - inner) - (tipX - 8)
                                    : (tipX + 8) - (trunkCenterX - inner);
                int wY = branchY - TreeBranchWidget.BRANCH_Y_OFFSET;
                this.addRenderableWidget(new TreeBranchWidget(wX, wY, wW, 22, trunkCenterX, branchLeft,
                        label, cfg.sourcePath, cfg.enabled, cfg.isVideo(), idx == selectedIndex,
                        () -> selectOverlay(idx)));
                branchY += ROW_SPACING;
            }

            // The big clickable stump is the base of the tree - clicking it grows a branch.
            int stumpTop = TREE_TOP + overlayConfigs.size() * ROW_SPACING + 8;
            int sx = (this.width - StumpWidget.STUMP_W) / 2;
            this.addRenderableWidget(new StumpWidget(sx, stumpTop, ADD_OVERLAY_LABEL, this::addOverlay));
        }

        if (overlayConfigs.isEmpty()) return;

        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;

        scrollSlots.clear();
        mediaTypeButton = track(new NeonButton(editStartX, startY, 140, 18, mediaTypeLabel(cfg), () -> openTypeDropdown(cfg))
                .setChevron(true));
        enabledButton = track(new NeonButton(editStartX + 145, startY, 155, 18, enabledLabel(cfg), () -> {
            cfg.enabled = !cfg.enabled;
            rebuildWidgets();
        }).setActiveState(cfg.enabled));
        startY += rowHeight + 6;

        int browseButtonWidth = 70;
        int pathFieldWidth = Math.max(220, this.width - editStartX - browseButtonWidth - 20);
        this.pathField = track(new EditBox(this.font, editStartX, startY, pathFieldWidth, 18, PATH_LABEL));
        this.pathField.setValue(cfg.sourcePath);
        this.pathField.setMaxLength(32767);
        if (!cfg.sourcePath.isBlank()) this.pathField.setTooltip(Tooltip.create(Component.literal(cfg.sourcePath)));
        this.pathField.setResponder(this::onPathFieldChanged);
        track(new NeonButton(editStartX + pathFieldWidth + 5, startY, browseButtonWidth, 18,
                Component.translatable(TranslationsKeys.CONFIG_BROWSE), this::openFilePicker));
        startY += rowHeight + 6;

        this.posXField = makeIntField(editStartX, startY, 60, cfg.posX, NeonOverlayConfigScreen::isInteger);
        this.posXField.setResponder(v -> { if (!updatingFields) cfg.posX = parseInt(v, cfg.posX); });
        this.posYField = makeIntField(editStartX + 65, startY, 60, cfg.posY, NeonOverlayConfigScreen::isInteger);
        this.posYField.setResponder(v -> { if (!updatingFields) cfg.posY = parseInt(v, cfg.posY); });
        this.widthField = makeIntField(editStartX + 130, startY, 60, cfg.width, NeonOverlayConfigScreen::isPositiveInteger);
        this.widthField.setResponder(this::onWidthFieldChanged);
        this.heightField = makeIntField(editStartX + 195, startY, 60, cfg.height, NeonOverlayConfigScreen::isPositiveInteger);
        this.heightField.setResponder(this::onHeightFieldChanged);
        startY += rowHeight + 6;

        this.opacitySlider = track(new NeonSlider(editStartX, startY, 295, 18, OPACITY_LABEL, cfg.opacity) {
            @Override protected void updateMessage() { setMessage(Component.translatable(TranslationsKeys.CONFIG_OPACITY_VALUE, (int) (this.value * 100))); }
            @Override protected void applyValue() { selectedConfig().opacity = (float) this.value; }
        });
        startY += rowHeight + 6;

        if (cfg.isVideo()) {
            this.volumeSlider = track(new NeonSlider(editStartX, startY, 295, 18, VOLUME_LABEL, cfg.volume) {
                @Override protected void updateMessage() { setMessage(Component.translatable(TranslationsKeys.CONFIG_VOLUME_VALUE, (int) (this.value * 100))); }
                @Override protected void applyValue() { selectedConfig().volume = (float) this.value; }
            });
            startY += rowHeight + 6;
        }

        if (cfg.isVideo()) {
            track(new NeonButton(editStartX, startY, 145, 18, playPauseLabel(cfg), () -> {
                cfg.playing = !cfg.playing;
                rebuildWidgets();
            }).setActiveState(cfg.playing));
            track(new NeonButton(editStartX + 150, startY, 145, 18, loopLabel(cfg), () -> {
                cfg.loop = !cfg.loop;
                rebuildWidgets();
            }).setActiveState(cfg.loop));
            startY += rowHeight + 6;

            // VLC-style seek bar: scrub while dragging, seek once on release.
            this.videoSeekSlider = track(new SeekSlider(editStartX, startY, 295, 18, 0, value -> {
                OverlayConfig cur = selectedConfig();
                if (cur == null) return;
                MediaProvider p = HudRenderingEntrypoint.getProvider(cur.id);
                if (p != null && p.getDurationSeconds() > 0) {
                    p.seek(value * p.getDurationSeconds());
                }
            }));
            startY += rowHeight + 6;
        }

        track(new NeonButton(editStartX, startY, 145, 18, anchorLabel(cfg), () -> {
            cfg.anchor = nextAnchor(cfg.anchor);
            rebuildWidgets();
        }));
        track(new NeonButton(editStartX + 150, startY, 145, 18, lockAspectLabel(cfg), () -> {
            cfg.lockAspectRatio = !cfg.lockAspectRatio;
            if (cfg.lockAspectRatio) syncHeightToWidth();
            rebuildWidgets();
        }).setActiveState(cfg.lockAspectRatio));
        startY += rowHeight + 6;

        NeonButton dragButton = new NeonButton(editStartX, startY, 295, 18,
                Component.translatable(TranslationsKeys.CONFIG_DRAG_MODE), this::enterDragMode);
        dragButton.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.CONFIG_DRAG_MODE_TOOLTIP)));
        track(dragButton);
        startY += rowHeight + 10;

        this.presetNameField = track(new EditBox(this.font, editStartX, startY, 140, 18, Component.translatable(TranslationsKeys.CONFIG_PRESET_NAME)));
        this.presetNameField.setMaxLength(32);
        track(new NeonButton(editStartX + 145, startY, 60, 18,
                Component.translatable(TranslationsKeys.CONFIG_SAVE_PRESET), this::savePreset));
        track(new NeonButton(editStartX + 210, startY, 60, 18,
                Component.translatable(TranslationsKeys.CONFIG_DELETE_PRESET), this::deletePreset));
        startY += rowHeight + 6;
        track(new NeonButton(editStartX, startY, 100, 18,
                Component.translatable(TranslationsKeys.CONFIG_LOAD_PREV), () -> loadPreset(-1)));
        track(new NeonButton(editStartX + 105, startY, 100, 18,
                Component.translatable(TranslationsKeys.CONFIG_LOAD_NEXT), () -> loadPreset(1)));
        startY += rowHeight + 10;

        track(new NeonButton(editStartX, startY, 145, 18, CommonComponents.GUI_DONE, this::onClose));
        track(new NeonButton(editStartX + 150, startY, 145, 18, CommonComponents.GUI_CANCEL, this::onCancel));

        // The whole panel scrolls as one when the window is too short for it.
        settingsMaxScroll = Math.max(0, (startY + 18) - (this.height - 12));
        settingsScroll = Math.max(0, Math.min(settingsScroll, settingsMaxScroll));
        applySettingsScroll();
    }

    private void selectOverlay(int idx) {
        if (idx < 0 || idx >= overlayConfigs.size()) return;
        this.selectedIndex = idx;
        settingsScroll = 0;
        rebuildWidgets();
    }

    private void addOverlay() {
        if (overlayConfigs.isEmpty()) {
            McScreens.setScreen(Minecraft.getInstance(), new MediaTypeSelectionScreen(
                    this,
                    Component.translatable(TranslationsKeys.CONFIG_MEDIA_TYPE_PROMPT),
                    null,
                    typeId -> {
                        OverlayConfig cfg = new OverlayConfig();
                        cfg.mediaType = typeId;
                        overlayConfigs.add(cfg);
                        selectedIndex = overlayConfigs.size() - 1;
                    }));
            return;
        }
        overlayConfigs.add(new OverlayConfig());
        selectedIndex = overlayConfigs.size() - 1;
        rebuildWidgets();
    }

    private void openTypeDropdown(OverlayConfig cfg) {
        McScreens.setScreen(Minecraft.getInstance(), new MediaTypeSelectionScreen(
                this,
                Component.translatable(TranslationsKeys.CONFIG_MEDIA_TYPE_SELECT),
                cfg.mediaType,
                typeId -> cfg.mediaType = typeId));
    }

    private void backToStump() {
        selectedIndex = -1;
        rebuildWidgets();
    }

    private void removeOverlay() {
        if (selectedIndex < 0 || selectedIndex >= overlayConfigs.size()) return;
        ImageTextureManager.removeOverlay(overlayConfigs.get(selectedIndex).id);
        overlayConfigs.remove(selectedIndex);
        selectedIndex = -1;
        rebuildWidgets();
    }

    private OverlayConfig selectedConfig() {
        if (selectedIndex < 0 || selectedIndex >= overlayConfigs.size()) return null;
        return overlayConfigs.get(selectedIndex);
    }

    private void onPathFieldChanged(String value) {
        if (updatingFields) return;
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        cfg.sourcePath = value;
        if (!value.isBlank() && !autoScaledPaths.contains(value) && !cfg.isVideo()) {
            ImageTextureManager mgr = ImageTextureManager.forOverlay(cfg.id);
            if (mgr.updateSource(value) && mgr.hasTexture()) {
                autoScaleImage(cfg, mgr);
                autoScaledPaths.add(value);
            }
        }
    }

    private void onWidthFieldChanged(String value) {
        if (updatingFields) return;
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        cfg.width = Math.max(1, parseInt(value, cfg.width));
        invalidatePreviewCache();
        if (cfg.lockAspectRatio) syncHeightToWidth();
    }

    private void onHeightFieldChanged(String value) {
        if (updatingFields) return;
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        cfg.height = Math.max(1, parseInt(value, cfg.height));
        invalidatePreviewCache();
        if (cfg.lockAspectRatio) syncWidthToHeight();
    }

    private void autoScaleImage(OverlayConfig cfg, ImageTextureManager mgr) {
        if (mgr == null || cfg == null) return;
        int ow = mgr.getOriginalWidth(), oh = mgr.getOriginalHeight();
        if (ow <= 0 || oh <= 0) return;
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

    private void invalidatePreviewCache() {
        cachedPreviewWidth = -1;
    }

    private void enterDragMode() {
        syncAllFieldsToConfig();
        dragMode = true;
        dragging = false;
    }

    private void exitDragMode() {
        dragMode = false;
        dragging = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (dragMode) {
            renderDragMode(graphics);
            return;
        }
        renderPreview(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 8, 0xFFFFFFFF, true);

        OverlayConfig cfg = selectedConfig();
        if (cfg == null) {
            return;
        }
        int ex = OVERLAY_LIST_WIDTH + 20;
        int labelDy = this.font.lineHeight + 2;
        if (mediaTypeButton != null) graphics.text(this.font, MEDIA_TYPE_LABEL, ex, mediaTypeButton.getY() - labelDy, 0xFFFFFFFF, true);
        if (enabledButton != null) graphics.text(this.font, ENABLED_LABEL, ex + 150, enabledButton.getY() - labelDy, 0xFFFFFFFF, true);
        if (pathField != null) graphics.text(this.font, PATH_LABEL, ex, pathField.getY() - labelDy, 0xFFFFFFFF, true);
        if (posXField != null) graphics.text(this.font, POS_X_LABEL, ex, posXField.getY() - labelDy, 0xFFFFFFFF, true);
        if (posYField != null) graphics.text(this.font, POS_Y_LABEL, ex + 65, posYField.getY() - labelDy, 0xFFFFFFFF, true);
        if (widthField != null) graphics.text(this.font, WIDTH_LABEL, ex + 130, widthField.getY() - labelDy, 0xFFFFFFFF, true);
        if (heightField != null) graphics.text(this.font, HEIGHT_LABEL, ex + 195, heightField.getY() - labelDy, 0xFFFFFFFF, true);
        if (presetNameField != null) graphics.text(this.font, PRESET_NAME_LABEL, presetNameField.getX(), presetNameField.getY() - labelDy, 0xFFFFFFFF, true);
        updateSeekDisplay(graphics, cfg);
        drawFieldOutlines(graphics);
        drawScrollbar(graphics);
    }

    private void updateSeekDisplay(GuiGraphicsExtractor graphics, OverlayConfig cfg) {
        if (videoSeekSlider == null || !cfg.isVideo()) return;
        MediaProvider prov = HudRenderingEntrypoint.getProvider(cfg.id);
        double dur = prov != null ? prov.getDurationSeconds() : 0;
        double pos = prov != null ? prov.getPlaybackPositionSeconds() : 0;
        if (dur > 0 && !videoSeekSlider.isUserDragging()) {
            videoSeekSlider.setSeekValue(Math.max(0, Math.min(1, pos / dur)));
        }
        String time = formatTime(pos) + " / " + formatTime(dur);
        graphics.text(this.font, time, videoSeekSlider.getX() + videoSeekSlider.getWidth() + 6,
                videoSeekSlider.getY() + 5, 0xFFFFFFFF, true);
    }

    private static String formatTime(double seconds) {
        int total = (int) Math.max(0, seconds);
        return String.format("%d:%02d", total / 60, total % 60);
    }

    private void drawFieldOutlines(GuiGraphicsExtractor graphics) {
        float f = NeonScreen.transitionFactor(fieldLerpLastNanos, NeonScreen.TRANSITION_SPEED);
        fieldLerpLastNanos = System.nanoTime();
        for (EditBox field : List.of(pathField, posXField, posYField, widthField, heightField, presetNameField)) {
            if (field == null) continue;
            int target = !field.active ? NeonScreen.DISABLED
                    : field.isFocused() ? NeonScreen.ACTIVE
                    : field.isHovered() ? NeonScreen.HOVER : NeonScreen.IDLE;
            // Snap on first draw so rebuilds don't blink; lerp afterwards.
            int current = fieldColors.containsKey(field) ? fieldColors.get(field) : target;
            if (fieldColors.containsKey(field)) current = NeonScreen.lerpColor(current, target, f);
            fieldColors.put(field, current);
            int fx = field.getX(), fy = field.getY(), fw = field.getWidth(), fh = field.getHeight();
            graphics.outline(fx - 1, fy - 1, fw + 2, fh + 2, current);
            // pixel corner blocks, matching the buttons
            graphics.fill(fx - 1, fy - 1, fx + 1, fy + 1, current);
            graphics.fill(fx + fw - 1, fy - 1, fx + fw + 1, fy + 1, current);
            graphics.fill(fx - 1, fy + fh - 1, fx + 1, fy + fh + 1, current);
            graphics.fill(fx + fw - 1, fy + fh - 1, fx + fw + 1, fy + fh + 1, current);
        }
    }

    private void renderDragMode(GuiGraphicsExtractor graphics) {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) {
            exitDragMode();
            return;
        }
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        int ax = HudRenderingEntrypoint.calculateX(cfg, this.width), ay = HudRenderingEntrypoint.calculateY(cfg, this.height);
        graphics.outline(ax - 2, ay - 2, cfg.width + 4, cfg.height + 4, NeonScreen.GLOW_HOVER);
        graphics.outline(ax - 1, ay - 1, cfg.width + 2, cfg.height + 2, NeonScreen.FOLIAGE);
        if (textureManager != null && textureManager.hasTexture())
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureManager.getTextureId(), ax, ay, 0, 0, cfg.width, cfg.height, cfg.width, cfg.height, cfg.getColor());
        int cx = ax + cfg.width / 2, cy = ay + cfg.height / 2;
        graphics.outline(cx - 1, cy - 6, 2, 12, 0xFFFFFFFF);
        graphics.outline(cx - 6, cy - 1, 12, 2, 0xFFFFFFFF);
        int lg = font.lineHeight + 4, bh = lg * 3, ty = ay + cfg.height + 8;
        if (ty + bh > this.height - 4) ty = Math.max(4, ay - bh - 8);
        Component hint = Component.translatable(TranslationsKeys.CONFIG_DRAG_HINT);
        Component rch = Component.translatable(TranslationsKeys.CONFIG_DRAG_RECENTER_HINT);
        Component info = Component.translatable(TranslationsKeys.CONFIG_DRAG_POSITION_INFO, cfg.posX, cfg.posY, cfg.width, cfg.height, Component.translatable(cfg.anchor.getTranslationKey()));
        graphics.text(font, hint, (width - font.width(hint)) / 2, ty, 0xFFFFFFFF, true);
        graphics.text(font, rch, (width - font.width(rch)) / 2, ty + lg, 0xFFFFFFFF, true);
        graphics.text(font, info, (width - font.width(info)) / 2, ty + lg * 2, 0xFFFFFFFF, true);
    }

    private void renderPreview(GuiGraphicsExtractor graphics) {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        int dw = cfg.width, dh = cfg.height;
        if (dw <= 0 || dh <= 0) return;
        int maxP = 150;
        if (cachedPreviewWidth < 0) {
            float s = Math.min(1f, Math.min((float) maxP / dw, (float) maxP / dh));
            cachedPreviewWidth = Math.max(1, Math.round(dw * s));
            cachedPreviewHeight = Math.max(1, Math.round(dh * s));
            cachedPreviewX = this.width - cachedPreviewWidth - 20;
            cachedPreviewY = 50;
        }
        graphics.text(font, PREVIEW_LABEL, cachedPreviewX, cachedPreviewY - 38, 0xFFFFFFFF, true);
        if (textureManager != null && textureManager.hasTexture()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureManager.getTextureId(), cachedPreviewX, cachedPreviewY, 0, 0, cachedPreviewWidth, cachedPreviewHeight, cachedPreviewWidth, cachedPreviewHeight, cfg.getColor());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dragMode) {
            int k = event.key();
            if (k == InputConstants.KEY_ESCAPE || k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
                exitDragMode();
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dc) {
        if (dragMode) {
            if (event.button() == 1) {
                recenterOverlay();
                return true;
            }
            if (event.button() == 0 && isMouseOverOverlay(event.x(), event.y())) {
                startDrag(event.x(), event.y());
                return true;
            }
            return true;
        }
        return super.mouseClicked(event, dc);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragMode && dragging && event.button() == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!dragMode && selectedIndex >= 0 && settingsMaxScroll > 0) {
            double before = settingsScroll;
            settingsScroll = Math.max(0, Math.min(settingsMaxScroll, settingsScroll - verticalAmount * 14.0));
            if (settingsScroll != before) {
                applySettingsScroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragMode && dragging && event.button() == 0) {
            double rdx = event.x() - dragLastMouseX + dragFractionalX, rdy = event.y() - dragLastMouseY + dragFractionalY;
            dragLastMouseX = event.x();
            dragLastMouseY = event.y();
            int ix = (int) rdx, iy = (int) rdy;
            dragFractionalX = rdx - ix;
            dragFractionalY = rdy - iy;
            applyDragDelta(ix, iy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private void startDrag(double mx, double my) {
        dragging = true;
        dragLastMouseX = mx;
        dragLastMouseY = my;
        dragFractionalX = dragFractionalY = 0;
    }

    private boolean isMouseOverOverlay(double mx, double my) {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return false;
        int ax = HudRenderingEntrypoint.calculateX(cfg, width), ay = HudRenderingEntrypoint.calculateY(cfg, height);
        return mx >= ax - DRAG_HITBOX_SLACK && mx < ax + cfg.width + DRAG_HITBOX_SLACK && my >= ay - DRAG_HITBOX_SLACK && my < ay + cfg.height + DRAG_HITBOX_SLACK;
    }

    private void applyDragDelta(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        int sx = HudRenderingEntrypoint.calculateX(cfg, width) + dx, sy = HudRenderingEntrypoint.calculateY(cfg, height) + dy;
        int minX = DRAG_MIN_VISIBLE_PX - cfg.width, minY = DRAG_MIN_VISIBLE_PX - cfg.height;
        sx = clamp(Math.max(minX, width - DRAG_MIN_VISIBLE_PX), minX, sx);
        sy = clamp(Math.max(minY, height - DRAG_MIN_VISIBLE_PX), minY, sy);
        setPositionFromScreen(sx, sy);
        syncPosFields();
    }

    private void setPositionFromScreen(int sx, int sy) {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        int ow = cfg.width, oh = cfg.height;
        cfg.posX = switch (cfg.anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> width - ow - sx;
            case CENTER -> sx - (width - ow) / 2;
            default -> sx;
        };
        cfg.posY = switch (cfg.anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> height - oh - sy;
            case CENTER -> sy - (height - oh) / 2;
            default -> sy;
        };
    }

    private void clampPositionToScreen() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        int sx = HudRenderingEntrypoint.calculateX(cfg, width), sy = HudRenderingEntrypoint.calculateY(cfg, height);
        sx = clamp(Math.max(DRAG_MIN_VISIBLE_PX - cfg.width, width - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - cfg.width, sx);
        sy = clamp(Math.max(DRAG_MIN_VISIBLE_PX - cfg.height, height - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - cfg.height, sy);
        setPositionFromScreen(sx, sy);
        syncPosFields();
    }

    private void recenterOverlay() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        int ow = cfg.width, oh = cfg.height, sx, sy;
        sx = switch (cfg.anchor) {
            case TOP_RIGHT -> width - ow;
            case CENTER -> (width - ow) / 2;
            default -> 0;
        };
        sy = switch (cfg.anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> height - oh;
            case CENTER -> (height - oh) / 2;
            default -> 0;
        };
        sx = clamp(Math.max(DRAG_MIN_VISIBLE_PX - ow, width - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - ow, sx);
        sy = clamp(Math.max(DRAG_MIN_VISIBLE_PX - oh, height - DRAG_MIN_VISIBLE_PX), DRAG_MIN_VISIBLE_PX - oh, sy);
        setPositionFromScreen(sx, sy);
        syncPosFields();
    }

    private void syncPosFields() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        updatingFields = true;
        if (posXField != null) posXField.setValue(String.valueOf(cfg.posX));
        if (posYField != null) posYField.setValue(String.valueOf(cfg.posY));
        updatingFields = false;
    }

    private void syncAllFieldsToConfig() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        updatingFields = true;
        if (posXField != null) posXField.setValue(String.valueOf(cfg.posX));
        if (posYField != null) posYField.setValue(String.valueOf(cfg.posY));
        if (widthField != null) widthField.setValue(String.valueOf(cfg.width));
        if (heightField != null) heightField.setValue(String.valueOf(cfg.height));
        updatingFields = false;
    }

    private static int clamp(int max, int min, int v) {
        return Math.max(min, Math.min(v, max));
    }

    private static final List<String> IMAGE_EXTS = List.of("png", "jpg", "jpeg", "webp", "tga", "bmp", "gif");
    private static final List<String> VIDEO_EXTS = List.of("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv");

    private void openFilePicker() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null) return;
        if (!pickerOpen.compareAndSet(false, true)) {
            SystemToast.add(McScreens.getToastManager(Minecraft.getInstance()), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable(TranslationsKeys.CONFIG_PICKER_ALREADY_OPEN_TITLE),
                    Component.translatable(TranslationsKeys.CONFIG_PICKER_ALREADY_OPEN_MESSAGE));
            return;
        }
        Thread pickerThread = new Thread(() -> {
            try {
                runFilePicker(cfg);
            } catch (Throwable t) {
                MiraHUD.LOGGER.error("Unexpected error in file picker", t);
                pickerOpen.set(false);
            }
        }, "Overlay-FilePicker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private void runFilePicker(OverlayConfig cfg) {
        boolean isVideo = cfg.isVideo();
        String pickedPath = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<String> wildcardExts = new ArrayList<>();
            for (String ext : (isVideo ? VIDEO_EXTS : IMAGE_EXTS)) wildcardExts.add("*." + ext);
            PointerBuffer filters = stack.mallocPointer(wildcardExts.size());
            for (String ext : wildcardExts) filters.put(stack.UTF8(ext));
            filters.flip();
            Language language = Language.getInstance();
            pickedPath = TinyFileDialogs.tinyfd_openFileDialog(
                    language.getOrDefault(isVideo ? TranslationsKeys.CONFIG_FILE_DIALOG_SELECT_VIDEO : TranslationsKeys.CONFIG_FILE_DIALOG_SELECT_IMAGE),
                    System.getProperty("user.home"),
                    filters,
                    language.getOrDefault(isVideo ? TranslationsKeys.CONFIG_FILE_DIALOG_FILTER_MEDIA : TranslationsKeys.CONFIG_FILE_DIALOG_FILTER_IMAGE),
                    false
            );
            MiraHUD.LOGGER.info("TinyFileDialogs picked: {}", pickedPath);
        } catch (Throwable t) {
            MiraHUD.LOGGER.error("TinyFileDialogs file picker failed", t);
        }
        final String finalPath = pickedPath;
        Minecraft.getInstance().execute(() -> handlePickedPath(cfg, finalPath));
    }

    private void handlePickedPath(OverlayConfig cfg, String path) {
        try {
            if (isValidPickedPath(path, cfg.isVideo())) {
                applyPickedFile(cfg, path);
            } else {
                MiraHUD.LOGGER.warn("No valid file was picked");
                SystemToast.add(McScreens.getToastManager(Minecraft.getInstance()), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable(TranslationsKeys.CONFIG_PASTE_PATH_HINT_TITLE),
                        Component.translatable(TranslationsKeys.CONFIG_PASTE_PATH_HINT_MESSAGE));
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
        MiraHUD.LOGGER.info("Applying picked file path: {}", path);
        updatingFields = true;
        pathField.setValue(path);
        updatingFields = false;
        cfg.sourcePath = path;
        if (!cfg.isVideo() && !autoScaledPaths.contains(path)) {
            ImageTextureManager mgr = ImageTextureManager.forOverlay(cfg.id);
            if (mgr.updateSource(path) && mgr.hasTexture()) {
                autoScaleImage(cfg, mgr);
                autoScaledPaths.add(path);
            }
        }
    }

    private void syncHeightToWidth() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null || widthField == null || heightField == null) return;
        int w = parseInt(widthField.getValue(), cfg.width), h = computeHeightForWidth(w);
        if (h > 0) {
            updatingFields = true;
            heightField.setValue(String.valueOf(h));
            updatingFields = false;
        }
    }

    private void syncWidthToHeight() {
        OverlayConfig cfg = selectedConfig();
        if (cfg == null || widthField == null || heightField == null) return;
        int h = parseInt(heightField.getValue(), cfg.height), w = computeWidthForHeight(h);
        if (w > 0) {
            updatingFields = true;
            widthField.setValue(String.valueOf(w));
            updatingFields = false;
        }
    }

    private int computeHeightForWidth(int w) {
        if (textureManager == null) return -1;
        int ow = textureManager.getOriginalWidth(), oh = textureManager.getOriginalHeight();
        return (ow <= 0 || oh <= 0) ? -1 : Math.max(1, Math.round(w * ((float) oh / ow)));
    }

    private int computeWidthForHeight(int h) {
        if (textureManager == null) return -1;
        int ow = textureManager.getOriginalWidth(), oh = textureManager.getOriginalHeight();
        return (ow <= 0 || oh <= 0) ? -1 : Math.max(1, Math.round(h * ((float) ow / oh)));
    }

    private void savePreset() {
        if (presetNameField == null) return;
        String name = presetNameField.getValue();
        OverlayConfig cfg = selectedConfig();
        if (name.isBlank() || cfg == null) return;
        if (OverlayPresetManager.savePreset(name, cfg)) {
            presetNameField.setValue("");
            SystemToast.add(McScreens.getToastManager(Minecraft.getInstance()), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable(TranslationsKeys.CONFIG_PRESET_SAVED_TITLE), Component.literal(name));
        }
    }

    private void deletePreset() {
        if (presetNameField == null) return;
        String name = presetNameField.getValue();
        if (name.isBlank()) return;
        OverlayPresetManager.deletePreset(name);
        presetNameField.setValue("");
        SystemToast.add(McScreens.getToastManager(Minecraft.getInstance()), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable(TranslationsKeys.CONFIG_PRESET_DELETED_TITLE), Component.literal(name));
    }

    private void loadPreset(int dir) {
        java.util.List<String> names = OverlayPresetManager.getPresetNames();
        if (names.isEmpty()) return;
        String cur = presetNameField != null ? presetNameField.getValue() : "";
        int idx = names.indexOf(cur);
        idx = idx < 0 ? 0 : (idx + dir) % names.size();
        if (idx < 0) idx = names.size() - 1;
        String sel = names.get(idx);
        OverlayPreset p = OverlayPresetManager.getPreset(sel);
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
            updatingFields = false;
            invalidatePreviewCache();
        }
    }

    @Override
    public void onClose() {
        syncAllFieldsToConfig();
        for (OverlayConfig cfg : overlayConfigs) {
            if (!cfg.sourcePath.isBlank()) {
                cfg.sourcePath = FilePathUtil.resolve(cfg.sourcePath);
            }
        }
        OverlayConfigManager.getRootConfig().overlays.clear();
        OverlayConfigManager.getRootConfig().overlays.addAll(overlayConfigs);
        OverlayConfigManager.save();
        HudRenderingEntrypoint.syncProviders();
        McScreens.setScreen(Minecraft.getInstance(), parent);
    }

    private void onCancel() {
        overlayConfigs.clear();
        for (OverlayConfig c : initialConfigs) overlayConfigs.add(OverlayConfigManager.copyConfig(c));
        OverlayConfigManager.getRootConfig().overlays.clear();
        OverlayConfigManager.getRootConfig().overlays.addAll(overlayConfigs);
        OverlayConfigManager.save();
        HudRenderingEntrypoint.syncProviders();
        McScreens.setScreen(Minecraft.getInstance(), parent);
    }

    private void restoreConfig(OverlayConfig to, OverlayConfig from) {
        to.mediaType = from.mediaType;
        to.enabled = from.enabled;
        to.sourcePath = from.sourcePath;
        to.posX = from.posX;
        to.posY = from.posY;
        to.width = from.width;
        to.height = from.height;
        to.opacity = from.opacity;
        to.volume = from.volume;
        to.anchor = from.anchor;
        to.lockAspectRatio = from.lockAspectRatio;
        to.playing = from.playing;
        to.loop = from.loop;
        to.options = new HashMap<>(from.options);
    }

    private EditBox makeIntField(int x, int y, int w, int val, Predicate<String> filter) {
        EditBox eb = new FilteredEditBox(font, x, y, w, 18, Component.empty(), filter);
        eb.setValue(String.valueOf(val));
        track(eb);
        return eb;
    }

    private Component enabledLabel(OverlayConfig c) {
        return Component.translatable(c.enabled ? TranslationsKeys.CONFIG_ENABLED_ON : TranslationsKeys.CONFIG_ENABLED_OFF);
    }

    private Component anchorLabel(OverlayConfig c) {
        return Component.translatable(TranslationsKeys.CONFIG_ANCHOR_VALUE, Component.translatable(c.anchor.getTranslationKey()));
    }

    private Component lockAspectLabel(OverlayConfig c) {
        return Component.translatable(c.lockAspectRatio ? TranslationsKeys.CONFIG_LOCK_ASPECT_ON : TranslationsKeys.CONFIG_LOCK_ASPECT_OFF);
    }

    private Component mediaTypeLabel(OverlayConfig c) {
        return Component.translatable(TranslationsKeys.CONFIG_MEDIA_TYPE_VALUE, Component.translatable(MediaTypes.byId(c.mediaType).translationKey()));
    }

    private Component playPauseLabel(OverlayConfig c) {
        return Component.translatable(c.playing ? TranslationsKeys.CONFIG_PAUSE : TranslationsKeys.CONFIG_PLAY);
    }

    private Component loopLabel(OverlayConfig c) {
        return Component.translatable(c.loop ? TranslationsKeys.CONFIG_LOOP_ON : TranslationsKeys.CONFIG_LOOP_OFF);
    }

    private static OverlayConfig.Anchor nextAnchor(OverlayConfig.Anchor cur) {
        OverlayConfig.Anchor[] v = OverlayConfig.Anchor.values();
        return v[(cur.ordinal() + 1) % v.length];
    }

    private static String truncate(String s, int max) {
        return s.isBlank() ? "(empty)" : (s.length() > max ? "..." + s.substring(s.length() - max + 3) : s);
    }

    private static boolean isInteger(String v) {
        if (v.isEmpty() || v.equals("-") || v.equals("+")) return true;
        try {
            Integer.parseInt(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isPositiveInteger(String v) {
        if (v.isEmpty()) return true;
        try {
            return Integer.parseInt(v) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseInt(String v, int fb) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fb;
        }
    }
}
