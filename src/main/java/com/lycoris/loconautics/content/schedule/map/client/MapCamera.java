package com.lycoris.loconautics.content.schedule.map.client;

public class MapCamera {

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 8.0;
    public static final double ZOOM_STEP = 1.25;

    private static final float CAM_LERP_SPEED = 0.25f;
    private static CameraState savedCameraState;

    public record CameraState(double camX, double camZ, double zoom) {}

    private double camX, camZ, zoom;
    private double targetCamX, targetCamZ, targetZoom;
    private final int viewOriginX, viewOriginZ, gridChunks;

    public MapCamera(int viewOriginX, int viewOriginZ, int gridChunks, int centerChunkX, int centerChunkZ) {
        this.viewOriginX = viewOriginX;
        this.viewOriginZ = viewOriginZ;
        this.gridChunks = gridChunks;

        CameraState saved = savedCameraState;
        if (saved != null) {
            this.camX = saved.camX;
            this.camZ = saved.camZ;
            this.zoom = saved.zoom;
        } else {
            this.camX = centerChunkX;
            this.camZ = centerChunkZ;
            this.zoom = MIN_ZOOM;
        }
        this.targetCamX = camX;
        this.targetCamZ = camZ;
        this.targetZoom = zoom;
    }

    public void tick() {
        double dx = targetCamX - camX, dz = targetCamZ - camZ, dZm = targetZoom - zoom;
        if (dx * dx + dz * dz > 1e-6) { camX += dx * CAM_LERP_SPEED; camZ += dz * CAM_LERP_SPEED; }
        else if (dx != 0 || dz != 0) { camX = targetCamX; camZ = targetCamZ; }

        if (Math.abs(dZm) > 1e-7) zoom += dZm * CAM_LERP_SPEED;
        else if (dZm != 0) zoom = targetZoom;

        clampCamera();
    }

    public void saveState() {
        savedCameraState = new CameraState(camX, camZ, zoom);
    }

    public void zoomAt(double mx, double my, double sy, int mapAreaX, int mapAreaY, int mapAreaSize) {
        double vb = gridChunks / zoom;
        double ccx = camX - vb / 2 + ((mx - mapAreaX) / (double) mapAreaSize) * vb;
        double ccz = camZ - vb / 2 + ((my - mapAreaY) / (double) mapAreaSize) * vb;
        double old = targetZoom;
        if (sy > 0) targetZoom = Math.min(MAX_ZOOM, targetZoom * ZOOM_STEP);
        else if (sy < 0) targetZoom = Math.max(MIN_ZOOM, targetZoom / ZOOM_STEP);
        if (targetZoom != old) {
            double va = gridChunks / targetZoom;
            targetCamX = ccx - ((mx - mapAreaX) / (double) mapAreaSize - 0.5) * va;
            targetCamZ = ccz - ((my - mapAreaY) / (double) mapAreaSize - 0.5) * va;
        }
    }

    public void setImmediate(double x, double z) {
        this.camX = x;
        this.camZ = z;
        this.targetCamX = x;
        this.targetCamZ = z;
        clampCamera();
    }

    private void clampCamera() {
        double hv = gridChunks / zoom / 2.0;
        double mnX = viewOriginX + hv, mxX = viewOriginX + gridChunks - hv;
        camX = mnX > mxX ? viewOriginX + gridChunks / 2.0 : Math.max(mnX, Math.min(mxX, camX));
        double mnZ = viewOriginZ + hv, mxZ = viewOriginZ + gridChunks - hv;
        camZ = mnZ > mxZ ? viewOriginZ + gridChunks / 2.0 : Math.max(mnZ, Math.min(mxZ, camZ));

        double th = gridChunks / targetZoom / 2.0;
        double tnX = viewOriginX + th, txX = viewOriginX + gridChunks - th;
        targetCamX = tnX > txX ? viewOriginX + gridChunks / 2.0 : Math.max(tnX, Math.min(txX, targetCamX));
        double tnZ = viewOriginZ + th, txZ = viewOriginZ + gridChunks - th;
        targetCamZ = tnZ > txZ ? viewOriginZ + gridChunks / 2.0 : Math.max(tnZ, Math.min(txZ, targetCamZ));
    }

    public double getCamX() { return camX; }
    public double getCamZ() { return camZ; }
    public double getZoom() { return zoom; }
}
