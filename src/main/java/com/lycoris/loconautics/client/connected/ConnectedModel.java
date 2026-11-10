package com.lycoris.loconautics.client.connected;

import com.lycoris.loconautics.client.QuadVertices;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ConnectedModel implements IDynamicBakedModel {

    public static final ModelProperty<Shape> SHAPE = new ModelProperty<>();

    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.cutout());

    private static final int STRIP_SINGLE = 0;
    private static final int STRIP_START = 1;
    private static final int STRIP_MIDDLE = 2;
    private static final int STRIP_END = 3;
    private static final int STRIP_TILES = 4;

    private static final int CAP_COLUMNS = 2;
    private static final int CAP_ROWS = 4;
    private static final int CORNER_OUTER = 0;
    private static final int CORNER_INNER = 1;
    private static final int EDGE_HORIZONTAL = 2;
    private static final int EDGE_VERTICAL = 3;
    private static final int BACKGROUND = 4;
    private static final int FANCY_BACKGROUND = 5;
    private static final int FANCY_CORNER = 6;

    private static final float INNER_INSET = 0.01f / 16f;

    private static final float OVERSHOOT = 0.01f;

    private final @Nullable TextureAtlasSprite strip;
    private final @Nullable TextureAtlasSprite cap;
    private final @Nullable TextureAtlasSprite inner;
    private final @Nullable TextureAtlasSprite innerCap;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;

    public ConnectedModel(@Nullable TextureAtlasSprite strip, @Nullable TextureAtlasSprite cap,
                          @Nullable TextureAtlasSprite inner, @Nullable TextureAtlasSprite innerCap,
                          TextureAtlasSprite particle, ItemTransforms transforms) {
        this.strip = strip;
        this.cap = cap;
        this.inner = inner;
        this.innerCap = innerCap;
        this.particle = particle;
        this.transforms = transforms;
    }

    public record Shape(int[] up, int[] down, boolean[] neighbors) {

        public boolean neighbor(Direction dir) {
            return neighbors[dir.get2DDataValue()];
        }
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        if (!(state.getBlock() instanceof ConnectedBlock block)) return modelData;

        Set<BlockPos> group = block.connectionBounds()
                .group(block, level, state, block.groupAnchor(level, state, pos));
        if (!group.contains(pos)) group = Set.of(pos.immutable());

        boolean[] neighbors = new boolean[4];
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            neighbors[dir.get2DDataValue()] = group.contains(pos.relative(dir));
        }
        return modelData.derive()
                .with(SHAPE, new Shape(capChunks(group, pos, Direction.UP),
                        capChunks(group, pos, Direction.DOWN), neighbors))
                .build();
    }

    private static int[] capChunks(Set<BlockPos> group, BlockPos pos, Direction face) {
        Direction up = face == Direction.UP ? Direction.NORTH : Direction.SOUTH;
        Direction down = up.getOpposite();
        Direction right = Direction.EAST;
        Direction left = Direction.WEST;

        boolean hasUp = group.contains(pos.relative(up));
        boolean hasDown = group.contains(pos.relative(down));
        boolean hasLeft = group.contains(pos.relative(left));
        boolean hasRight = group.contains(pos.relative(right));

        return applyFancy(new int[]{
                resolveChunk(hasUp, hasLeft, group.contains(pos.relative(up).relative(left))),
                resolveChunk(hasUp, hasRight, group.contains(pos.relative(up).relative(right))),
                resolveChunk(hasDown, hasLeft, group.contains(pos.relative(down).relative(left))),
                resolveChunk(hasDown, hasRight, group.contains(pos.relative(down).relative(right)))
        });
    }

    private static int resolveChunk(boolean cardinalA, boolean cardinalB, boolean diagonal) {
        if (!cardinalA && !cardinalB) return CORNER_OUTER;
        if (cardinalA && !cardinalB) return EDGE_VERTICAL;
        if (!cardinalA) return EDGE_HORIZONTAL;
        return diagonal ? BACKGROUND : CORNER_INNER;
    }

    private static int[] applyFancy(int[] chunks) {
        int outerCount = 0;
        int backgroundCount = 0;
        for (int chunk : chunks) {
            if (chunk == CORNER_OUTER) outerCount++;
            if (chunk == BACKGROUND) backgroundCount++;
        }
        if (backgroundCount == chunks.length) {
            return new int[]{FANCY_BACKGROUND, FANCY_BACKGROUND, FANCY_BACKGROUND, FANCY_BACKGROUND};
        }
        if (outerCount == 1) {
            for (int i = 0; i < chunks.length; i++) {
                if (chunks[i] == CORNER_OUTER) chunks[i] = FANCY_CORNER;
            }
        }
        return chunks;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand, @NotNull ModelData modelData,
                                             @Nullable RenderType renderType) {
        if (state == null) return Collections.emptyList();
        Shape shape = modelData.get(SHAPE);

        if (face == null) return innerQuads(state, shape);
        if (face.getAxis() == Direction.Axis.Y) {
            return capQuads(face, face == Direction.UP ? 1f : 0f, chunksOf(shape, face), cap, false);
        }
        if (strip == null || isOpen(state, shape, face)) return Collections.emptyList();

        float u0 = (float) stripTile(shape, face) / STRIP_TILES;
        return List.of(quad(face, nearPlane(face) ? 0f : 1f, 0f, 0f, 1f, 1f,
                strip, u0, 0f, u0 + 1f / STRIP_TILES, 1f));
    }

    private static int[] chunksOf(@Nullable Shape shape, Direction face) {
        if (shape == null) return new int[]{CORNER_OUTER, CORNER_OUTER, CORNER_OUTER, CORNER_OUTER};
        return face == Direction.UP ? shape.up() : shape.down();
    }

    private static List<BakedQuad> capQuads(Direction face, float plane, int[] chunks,
                                            @Nullable TextureAtlasSprite sprite, boolean mirror) {
        if (sprite == null) return Collections.emptyList();

        List<BakedQuad> quads = new ArrayList<>(4);
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int xOffset = quadrant % 2;
            int yOffset = quadrant / 2;
            float u0 = ((chunks[quadrant] % CAP_COLUMNS) * 2f + xOffset) / (CAP_COLUMNS * 2f);
            float v0 = ((chunks[quadrant] / CAP_COLUMNS) * 2f + yOffset) / (CAP_ROWS * 2f);
            float u1 = u0 + 1f / (CAP_COLUMNS * 2f);
            float minX = xOffset * 0.5f;
            float minY = (1 - yOffset) * 0.5f;

            quads.add(quad(face, plane, minX, minY, minX + 0.5f, minY + 0.5f, sprite,
                    mirror ? u1 : u0, v0, mirror ? u0 : u1, v0 + 1f / (CAP_ROWS * 2f)));
        }
        return quads;
    }

    private List<BakedQuad> innerQuads(BlockState state, @Nullable Shape shape) {
        List<BakedQuad> quads = new ArrayList<>(12);

        if (inner != null) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (isOpen(state, shape, dir)) continue;
                float plane = nearPlane(dir) ? INNER_INSET : 1f - INNER_INSET;
                float u0 = (float) stripTile(shape, dir) / STRIP_TILES;
                quads.add(quad(dir.getOpposite(), plane, -OVERSHOOT, -OVERSHOOT,
                        1f + OVERSHOOT, 1f + OVERSHOOT,
                        inner, u0 + 1f / STRIP_TILES, 0f, u0, 1f));
            }
        }

        quads.addAll(capQuads(Direction.DOWN, 1f - INNER_INSET, chunksOf(shape, Direction.UP), innerCap, true));
        quads.addAll(capQuads(Direction.UP, INNER_INSET, chunksOf(shape, Direction.DOWN), innerCap, true));
        return quads;
    }

    private static boolean nearPlane(Direction dir) {
        return dir == Direction.NORTH || dir == Direction.WEST;
    }

    private static boolean isOpen(BlockState state, @Nullable Shape shape, Direction face) {
        if (state.getBlock() instanceof ConnectedBlock block && block.isOpenFace(state, face)) return true;
        return shape != null && shape.neighbor(face);
    }

    private static int stripTile(@Nullable Shape shape, Direction face) {
        if (shape == null) return STRIP_SINGLE;
        boolean towardU0 = shape.neighbor(face.getClockWise());
        boolean towardU1 = shape.neighbor(face.getCounterClockWise());
        if (!towardU0 && !towardU1) return STRIP_SINGLE;
        if (!towardU0) return STRIP_START;
        return towardU1 ? STRIP_MIDDLE : STRIP_END;
    }

    private static BakedQuad quad(Direction face, float plane, float minX, float minY, float maxX, float maxY,
                                  TextureAtlasSprite sprite, float u0, float v0, float u1, float v1) {
        Vector3f[] vertices = faceVertices(face, plane, minX, minY, maxX, maxY);
        float su0 = sprite.getU0() + u0 * (sprite.getU1() - sprite.getU0());
        float su1 = sprite.getU0() + u1 * (sprite.getU1() - sprite.getU0());
        float sv0 = sprite.getV0() + v0 * (sprite.getV1() - sprite.getV0());
        float sv1 = sprite.getV0() + v1 * (sprite.getV1() - sprite.getV0());

        int[] data = new int[32];
        putVertex(data, 0, vertices[0], su0, sv0, face);
        putVertex(data, 1, vertices[1], su0, sv1, face);
        putVertex(data, 2, vertices[2], su1, sv1, face);
        putVertex(data, 3, vertices[3], su1, sv0, face);
        return new BakedQuad(data, -1, face, sprite, true);
    }

    private static Vector3f[] faceVertices(Direction face, float p, float minX, float minY,
                                           float maxX, float maxY) {
        return switch (face) {
            case UP -> new Vector3f[]{
                    new Vector3f(minX, p, 1 - maxY),
                    new Vector3f(minX, p, 1 - minY),
                    new Vector3f(maxX, p, 1 - minY),
                    new Vector3f(maxX, p, 1 - maxY)
            };
            case DOWN -> new Vector3f[]{
                    new Vector3f(minX, p, maxY),
                    new Vector3f(minX, p, minY),
                    new Vector3f(maxX, p, minY),
                    new Vector3f(maxX, p, maxY)
            };
            case NORTH -> new Vector3f[]{
                    new Vector3f(1 - minX, maxY, p),
                    new Vector3f(1 - minX, minY, p),
                    new Vector3f(1 - maxX, minY, p),
                    new Vector3f(1 - maxX, maxY, p)
            };
            case SOUTH -> new Vector3f[]{
                    new Vector3f(minX, maxY, p),
                    new Vector3f(minX, minY, p),
                    new Vector3f(maxX, minY, p),
                    new Vector3f(maxX, maxY, p)
            };
            case WEST -> new Vector3f[]{
                    new Vector3f(p, maxY, minX),
                    new Vector3f(p, minY, minX),
                    new Vector3f(p, minY, maxX),
                    new Vector3f(p, maxY, maxX)
            };
            case EAST -> new Vector3f[]{
                    new Vector3f(p, maxY, 1 - minX),
                    new Vector3f(p, minY, 1 - minX),
                    new Vector3f(p, minY, 1 - maxX),
                    new Vector3f(p, maxY, 1 - maxX)
            };
        };
    }

    private static void putVertex(int[] data, int index, Vector3f pos, float u, float v, Direction face) {
        QuadVertices.put(data, index, pos.x(), pos.y(), pos.z(), u, v, face);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        return RENDER_TYPES;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face,
                                             @NotNull RandomSource rand) {
        return getQuads(state, face, rand, ModelData.EMPTY, null);
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull ItemTransforms getTransforms() { return transforms; }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
}
