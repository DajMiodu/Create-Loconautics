package com.lycoris.loconautics.content.schedule.map.client;

import com.lycoris.loconautics.network.packets.schedule.MapColorRequestPacket;
import com.lycoris.loconautics.network.packets.schedule.MapColorResponsePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapRenderer;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class FullscreenMapScreen extends Screen {

    private static final int GRID_CHUNKS = 96;
    private static final int REQUEST_CAP = 400;
    private static final int POLL_INTERVAL = 20;
    private static final int EDGE_INTERVAL = 20;
    private static final double EDGE_PICK_PX = 6.0;

    private static FullscreenMapScreen active;

    private static final Map<Long, byte[]> COLORS = new ConcurrentHashMap<>();

    private final Screen parent;
    private final BiConsumer<MapTrackOverlay.Edge, Double> onPick;
    private final MapCamera camera;
    private final MapRasterTexture raster;
    private final int viewOriginX, viewOriginZ;

    private volatile boolean colorsDirty;
    private int pollTimer;

    private List<MapTrackOverlay.Edge> edges = new ArrayList<>();
    private final Set<Long> selected = new HashSet<>();
    private final java.util.Map<Long, Double> pickPositions = new java.util.HashMap<>();
    private int edgeTimer;

    private int mapX, mapY, mapSize;

    private boolean panning, panMoved;
    private double panStartMx, panStartMy, panCamX, panCamZ;
    private boolean boxing;
    private double boxStartMx, boxStartMy, boxCurMx, boxCurMy;

    public FullscreenMapScreen(Screen parent, BiConsumer<MapTrackOverlay.Edge, Double> onPick) {
        super(Component.translatable("loconautics.schedule.map_title"));
        this.parent = parent;
        this.onPick = onPick;
        Player p = Minecraft.getInstance().player;
        int cx = p != null ? p.chunkPosition().x : 0;
        int cz = p != null ? p.chunkPosition().z : 0;
        this.viewOriginX = cx - GRID_CHUNKS / 2;
        this.viewOriginZ = cz - GRID_CHUNKS / 2;
        this.camera = new MapCamera(viewOriginX, viewOriginZ, GRID_CHUNKS, cx, cz);
        this.raster = new MapRasterTexture(GRID_CHUNKS, viewOriginX, viewOriginZ);
    }

    @Override
    protected void init() {
        active = this;
        int margin = 20;
        mapSize = Math.min(width, height) - margin * 2;
        mapX = (width - mapSize) / 2;
        mapY = (height - mapSize) / 2;
        colorsDirty = true;
        rebuildEdges();
    }

    private void rebuildEdges() {
        Player p = Minecraft.getInstance().player;
        if (p != null) edges = MapTrackOverlay.collect(p.level().dimension());
    }

    @Override
    public void tick() {
        if (pollTimer-- <= 0) {
            pollTimer = POLL_INTERVAL;
            requestVisibleChunks();
        }
        if (edgeTimer-- <= 0) {
            edgeTimer = EDGE_INTERVAL;
            rebuildEdges();
        }
        raster.tick();
        if (colorsDirty) {
            colorsDirty = false;
            raster.rebuildAsync(COLORS);
        }
    }

    private void forVisibleChunks(java.util.function.LongConsumer action) {
        double v = GRID_CHUNKS / camera.getZoom();
        int minX = Math.max(viewOriginX, (int) Math.floor(camera.getCamX() - v / 2));
        int maxX = Math.min(viewOriginX + GRID_CHUNKS - 1, (int) Math.ceil(camera.getCamX() + v / 2));
        int minZ = Math.max(viewOriginZ, (int) Math.floor(camera.getCamZ() - v / 2));
        int maxZ = Math.min(viewOriginZ + GRID_CHUNKS - 1, (int) Math.ceil(camera.getCamZ() + v / 2));
        for (int cz = minZ; cz <= maxZ; cz++)
            for (int cx = minX; cx <= maxX; cx++)
                action.accept(key(cx, cz));
    }

    private void requestVisibleChunks() {
        List<long[]> req = new ArrayList<>();
        forVisibleChunks(k -> {
            if (req.size() < REQUEST_CAP && !COLORS.containsKey(k)) req.add(new long[]{ (int) (k >> 32), (int) k });
        });
        if (!req.isEmpty()) CatnipServices.NETWORK.sendToServer(new MapColorRequestPacket(req));
    }

    public static void acceptColors(List<MapColorResponsePacket.Entry> entries) {
        for (MapColorResponsePacket.Entry e : entries) {
            COLORS.put(key(e.chunkX(), e.chunkZ()), e.colors());
        }
        if (active != null) active.colorsDirty = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        camera.tick();

        graphics.fill(0, 0, width, height, 0xC0101216);

        graphics.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF15171C);

        double zoom = camera.getZoom();
        double v = GRID_CHUNKS / zoom;
        double sx = camera.getCamX() - v / 2;
        double sz = camera.getCamZ() - v / 2;

        if (raster.isTextureValid()) {
            int ppc = raster.pxPerChunk();
            float u0 = (float) ((sx - viewOriginX) * ppc);
            float v0 = (float) ((sz - viewOriginZ) * ppc);
            int srcW = Math.max(1, (int) Math.round(v * ppc));
            graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
            graphics.blit(MapRasterTexture.TEXTURE_LOC, mapX, mapY, mapSize, mapSize,
                    u0, v0, srcW, srcW, GRID_CHUNKS * ppc, GRID_CHUNKS * ppc);
            graphics.disableScissor();
        }

        renderTracks(graphics, sx, sz, v);
        renderSelectedEdges(graphics, sx, sz, v);
        renderPlayerMarker(graphics, sx, sz, v);

        if (boxing) {
            int x0 = (int) Math.min(boxStartMx, boxCurMx), x1 = (int) Math.max(boxStartMx, boxCurMx);
            int y0 = (int) Math.min(boxStartMy, boxCurMy), y1 = (int) Math.max(boxStartMy, boxCurMy);
            graphics.fill(x0, y0, x1, y1, 0x3355AAFF);
            graphics.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF55AAFF);
        }

        graphics.renderOutline(mapX, mapY, mapSize, mapSize, 0xFF3A3F4A);
        graphics.drawCenteredString(font, title, width / 2, mapY - 12, 0xFFFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    private void renderTracks(GuiGraphics graphics, double sx, double sz, double v) {
        if (Minecraft.getInstance().level == null) return;
        TrainMapManager.tick(Minecraft.getInstance().level.dimension());

        double scale = mapSize / (16.0 * v);
        double offX = mapX - sx / v * mapSize;
        double offY = mapY - sz / v * mapSize;
        Rect2i bounds = new Rect2i((int) Math.floor(sx * 16), (int) Math.floor(sz * 16),
                (int) Math.ceil(v * 16), (int) Math.ceil(v * 16));

        graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        RenderSystem.enableBlend();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) offX, (float) offY, 0f);
        pose.scale((float) scale, (float) scale, 1f);
        TrainMapRenderer.INSTANCE.render(graphics, false, bounds);
        graphics.bufferSource().endBatch();
        pose.popPose();
        graphics.disableScissor();
    }

    private void renderSelectedEdges(GuiGraphics graphics, double sx, double sz, double v) {
        if (selected.isEmpty()) return;
        graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        for (MapTrackOverlay.Edge e : edges) {
            if (!selected.contains(e.key())) continue;
            double[] xs = e.xs(), zs = e.zs();
            int prevX = 0, prevY = 0;
            for (int i = 0; i < xs.length; i++) {
                int px = mapX + (int) ((xs[i] / 16.0 - sx) / v * mapSize);
                int py = mapY + (int) ((zs[i] / 16.0 - sz) / v * mapSize);
                if (i > 0) drawLine(graphics, prevX, prevY, px, py, 0xFFFFDD33);
                prevX = px;
                prevY = py;
            }
        }
        graphics.disableScissor();
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps == 0) { graphics.fill(x0, y0, x0 + 1, y0 + 1, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void renderPlayerMarker(GuiGraphics graphics, double sx, double sz, double v) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        double pcx = p.getX() / 16.0, pcz = p.getZ() / 16.0;
        int px = mapX + (int) ((pcx - sx) / v * mapSize);
        int py = mapY + (int) ((pcz - sz) / v * mapSize);
        if (px < mapX || px >= mapX + mapSize || py < mapY || py >= mapY + mapSize) return;
        graphics.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
        graphics.fill(px - 1, py - 1, px + 1, py + 1, 0xFFE03030);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inMap(mx, my)) return super.mouseClicked(mx, my, button);
        if (button == 1) {
            boxing = true;
            boxStartMx = boxCurMx = mx;
            boxStartMy = boxCurMy = my;
            return true;
        }
        if (button == 0) {
            panning = true;
            panMoved = false;
            panStartMx = mx;
            panStartMy = my;
            panCamX = camera.getCamX();
            panCamZ = camera.getCamZ();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning && button == 0) {
            if (Math.abs(mx - panStartMx) > 2 || Math.abs(my - panStartMy) > 2) panMoved = true;
            double v = GRID_CHUNKS / camera.getZoom();
            double ppc = mapSize / v;
            camera.setImmediate(panCamX - (mx - panStartMx) / ppc, panCamZ - (my - panStartMy) / ppc);
            return true;
        }
        if (boxing && button == 1) {
            boxCurMx = mx;
            boxCurMy = my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning && button == 0) {
            panning = false;
            if (!panMoved) onEdgePick(mx, my);
            return true;
        }
        if (boxing && button == 1) {
            boxing = false;
            onBoxSelect(boxStartMx, boxStartMy, boxCurMx, boxCurMy);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inMap(mx, my)) camera.zoomAt(mx, my, sy, mapX, mapY, mapSize);
        return true;
    }

    private void onEdgePick(double mx, double my) {
        double v = GRID_CHUNKS / camera.getZoom();
        double sx = camera.getCamX() - v / 2, sz = camera.getCamZ() - v / 2;

        Long best = null;
        double bestDist = EDGE_PICK_PX * EDGE_PICK_PX;
        double bestAlong = 0.0;
        for (MapTrackOverlay.Edge e : edges) {
            double[] xs = e.xs(), zs = e.zs();
            double px0 = 0, py0 = 0;
            double along = 0.0;
            for (int i = 0; i < xs.length; i++) {
                double px = mapX + (xs[i] / 16.0 - sx) / v * mapSize;
                double py = mapY + (zs[i] / 16.0 - sz) / v * mapSize;
                if (i > 0) {
                    double d = distToSegSq(mx, my, px0, py0, px, py);
                    double segWorld = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
                    if (d < bestDist) {
                        bestDist = d;
                        best = e.key();
                        bestAlong = along + segWorld * segT(mx, my, px0, py0, px, py);
                    }
                    along += segWorld;
                }
                px0 = px;
                py0 = py;
            }
        }
        if (best == null) return;
        selected.clear();
        selected.add(best);
        pickPositions.put(best, bestAlong);
        openCanvas();
    }

    private static double segT(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1.0e-9) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, ((px - x0) * dx + (py - y0) * dy) / lenSq));
    }

    private void onBoxSelect(double x0, double y0, double x1, double y1) {
        double minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        double minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        double v = GRID_CHUNKS / camera.getZoom();
        double sx = camera.getCamX() - v / 2, sz = camera.getCamZ() - v / 2;

        selected.clear();
        pickPositions.clear();
        for (MapTrackOverlay.Edge e : edges) {
            double[] xs = e.xs(), zs = e.zs();
            for (int i = 0; i < xs.length; i++) {
                double px = mapX + (xs[i] / 16.0 - sx) / v * mapSize;
                double py = mapY + (zs[i] / 16.0 - sz) / v * mapSize;
                if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
                    selected.add(e.key());
                    break;
                }
            }
        }
        if (!selected.isEmpty()) openCanvas();
    }

    private void openCanvas() {
        for (MapTrackOverlay.Edge e : edges) {
            if (!selected.contains(e.key())) continue;
            onPick.accept(e, pickPositions.getOrDefault(e.key(), e.arcLength() / 2.0));
            minecraft.setScreen(parent);
            return;
        }
    }

    private static double distToSegSq(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx, cy = ay + t * dy;
        double ex = px - cx, ey = py - cy;
        return ex * ex + ey * ey;
    }

    private boolean inMap(double mx, double my) {
        return mx >= mapX && mx < mapX + mapSize && my >= mapY && my < mapY + mapSize;
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    @Override
    public void removed() {
        camera.saveState();
        raster.free();
        if (active == this) active = null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
