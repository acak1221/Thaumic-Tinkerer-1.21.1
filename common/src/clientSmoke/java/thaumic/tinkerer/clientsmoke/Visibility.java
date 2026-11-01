package thaumic.tinkerer.clientsmoke;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Tells whether a block that was put on the stage can be seen in a picture: the cell's cube is projected through the camera
 * of the picture, and the pixels of the picture inside it are compared with the picture of the same empty stage. Test-only code
 * of the port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on, not distributed.
 *
 * <p>A block that renders nothing (a broken or missing model, a camouflage block with nothing to copy, a gas) leaves the
 * pixels as they were. The result is a number, not a verdict: whether a block ought to be invisible is for the reader to
 * judge.</p>
 */
final class Visibility {
    /** Share of pixels of a cell's box that differ from the empty stage for the cell to count as seen. */
    static final double SEEN = 0.02;

    private Visibility() {}

    /** The camera of a picture, read from the client player at the moment the picture was requested. */
    record Camera(Vec3 eye, float pitch, float yaw, double fovDegrees, int width, int height) {
        static Camera of(Minecraft client) {
            return new Camera(client.player.getEyePosition(), client.player.getXRot(), client.player.getYRot(),
                    client.options.fov().get(), client.getWindow().getWidth(), client.getWindow().getHeight());
        }
    }

    /** The share of the pixels inside the projected unit cube at {@code cell} that differ between the two pictures; -1 when unreadable. */
    static double changed(Path picture, Path reference, Camera camera, BlockPos cell) {
        try (InputStream a = Files.newInputStream(picture); InputStream b = Files.newInputStream(reference);
                NativeImage image = NativeImage.read(a); NativeImage empty = NativeImage.read(b)) {
            int[] box = box(camera, cell, image.getWidth(), image.getHeight());
            if (box == null) return -1;
            long different = 0;
            long total = 0;
            for (int y = box[1]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[2]; x++) {
                    total++;
                    int p = image.getPixelRGBA(x, y);
                    int q = empty.getPixelRGBA(x, y);
                    int delta = Math.abs((p & 0xFF) - (q & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                            + Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
                    if (delta > 36) different++;
                }
            }
            return total == 0 ? -1 : (double) different / total;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Pixel bounding box {minX, minY, maxX, maxY} of the unit cube whose lower corner is {@code cell}, clipped to the picture. */
    static int[] box(Camera camera, BlockPos cell, int width, int height) {
        double[][] corners = corners(camera, cell, width, height);
        if (corners == null) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] corner : corners) {
            minX = Math.min(minX, corner[0]);
            maxX = Math.max(maxX, corner[0]);
            minY = Math.min(minY, corner[1]);
            maxY = Math.max(maxY, corner[1]);
        }
        int x0 = (int) Math.max(0, Math.floor(minX));
        int y0 = (int) Math.max(0, Math.floor(minY));
        int x1 = (int) Math.min(width - 1, Math.ceil(maxX));
        int y1 = (int) Math.min(height - 1, Math.ceil(maxY));
        return x1 < x0 || y1 < y0 ? null : new int[] { x0, y0, x1, y1 };
    }

    /** The eight corners of the unit cube at {@code cell} projected into the picture ({x, y} in pixels); null when one is behind the camera. */
    static double[][] corners(Camera camera, BlockPos cell, int width, int height) {
        double yaw = Math.toRadians(camera.yaw());
        double pitch = Math.toRadians(camera.pitch());
        Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(forward);
        double focal = (height / 2.0) / Math.tan(Math.toRadians(camera.fovDegrees()) / 2.0);
        double[][] corners = new double[8][];
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(cell.getX() + (i & 1), cell.getY() + ((i >> 1) & 1), cell.getZ() + ((i >> 2) & 1)).subtract(camera.eye());
            double depth = corner.dot(forward);
            if (depth < 0.1) return null;
            corners[i] = new double[] { width / 2.0 + focal * corner.dot(right) / depth, height / 2.0 - focal * corner.dot(up) / depth };
        }
        return corners;
    }

    /**
     * Whether the cube at {@code cell} looks the same in two pictures: the share of the pixels of its silhouette (the convex hull of its
     * projected corners, from 1.5 pixels inside its edges) where {@code reference} shows something (differs from {@code empty}) and
     * {@code picture} differs from {@code reference}; -1 when a picture is unreadable or the reference shows nothing there. Unlike
     * {@link #changed}, the floor around the cube does not count, only what is seen through a transparent part of it.
     */
    static double unlikeOnSilhouette(Path picture, Path reference, Path empty, Camera camera, BlockPos cell) {
        try (InputStream a = Files.newInputStream(picture); InputStream b = Files.newInputStream(reference); InputStream c = Files.newInputStream(empty);
                NativeImage image = NativeImage.read(a); NativeImage real = NativeImage.read(b); NativeImage stage = NativeImage.read(c)) {
            double[][] hull = hull(corners(camera, cell, image.getWidth(), image.getHeight()));
            if (hull == null) return -1;
            int[] box = box(camera, cell, image.getWidth(), image.getHeight());
            long shown = 0;
            long different = 0;
            for (int y = box[1]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[2]; x++) {
                    if (!inside(hull, x + 0.5, y + 0.5, 1.5) || delta(real.getPixelRGBA(x, y), stage.getPixelRGBA(x, y)) <= 36) continue;
                    shown++;
                    if (delta(image.getPixelRGBA(x, y), real.getPixelRGBA(x, y)) > 36) different++;
                }
            }
            return shown == 0 ? -1 : (double) different / shown;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private static int delta(int p, int q) {
        return Math.abs((p & 0xFF) - (q & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF)) + Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
    }

    /** The convex hull of the points, counter-clockwise on the screen's y-down axes (Andrew's monotone chain); null for no points. */
    private static double[][] hull(double[][] points) {
        if (points == null) return null;
        double[][] sorted = points.clone();
        java.util.Arrays.sort(sorted, (p, q) -> p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(p[1], q[1]));
        double[][] chain = new double[2 * sorted.length][];
        int k = 0;
        for (double[] p : sorted) {
            while (k >= 2 && cross(chain[k - 2], chain[k - 1], p) <= 0) k--;
            chain[k++] = p;
        }
        for (int i = sorted.length - 2, lower = k + 1; i >= 0; i--) {
            while (k >= lower && cross(chain[k - 2], chain[k - 1], sorted[i]) <= 0) k--;
            chain[k++] = sorted[i];
        }
        return java.util.Arrays.copyOf(chain, Math.max(0, k - 1));
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** Whether the point lies inside the hull at least {@code margin} pixels from every edge. */
    private static boolean inside(double[][] hull, double x, double y, double margin) {
        for (int i = 0; i < hull.length; i++) {
            double[] a = hull[i];
            double[] b = hull[(i + 1) % hull.length];
            double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (length == 0) continue;
            if (((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0])) / length < margin) return false;
        }
        return true;
    }
}
