package com.lycoris.loconautics.content.industrial;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.QuadVertices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IndustrialTankModel implements IDynamicBakedModel {

    private static final ModelProperty<Neighbors> NEIGHBORS = new ModelProperty<>();

    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.cutout());

    private static final float WALL = 1f / 16f;
    private static final float CAP = 4f / 16f;
    private static final int SHEET = 4;
    private static final int BEND_SHEET = 8;
    private static final float LIGHT_NUDGE = 2e-4f;

    private final BakedModel original;
    private Sprites sprites;

    public IndustrialTankModel(BakedModel original) {
        this.original = original;
    }

    private record Sprites(TextureAtlasSprite side, TextureAtlasSprite top, TextureAtlasSprite inner,
                           TextureAtlasSprite innerPlain, TextureAtlasSprite window, TextureAtlasSprite sideBend,
                           TextureAtlasSprite topBend, TextureAtlasSprite innerBend, TextureAtlasSprite windowBend) {}

    private record Neighbors(boolean[] cells) {

        static final Neighbors NONE = new Neighbors(new boolean[27]);

        boolean at(Vec3i offset) {
            return cells[index(offset.getX(), offset.getY(), offset.getZ())];
        }

        boolean at(Direction first, Direction second) {
            return at(first.getNormal().offset(second.getNormal()));
        }

        static int index(int x, int y, int z) {
            return (x + 1) + 3 * ((y + 1) + 3 * (z + 1));
        }
    }

    private Sprites sprites() {
        if (sprites == null) {
            var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            sprites = new Sprites(atlas.apply(texture("fluid_tank_connected")),
                    atlas.apply(texture("fluid_tank_top_connected")),
                    atlas.apply(texture("fluid_tank_inner_connected")),
                    atlas.apply(texture("fluid_tank_inner")),
                    atlas.apply(texture("fluid_tank_window_connected")),
                    atlas.apply(texture("fluid_tank_ct")),
                    atlas.apply(texture("fluid_tank_top_ct")),
                    atlas.apply(texture("fluid_tank_inner_ct")),
                    atlas.apply(texture("fluid_tank_window_ct")));
        }
        return sprites;
    }

    private static ResourceLocation texture(String name) {
        return LoconauticsConstants.id("block/industrial/" + name);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        boolean[] cells = new boolean[27];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    cells[Neighbors.index(x, y, z)] =
                            level.getBlockState(cursor.setWithOffset(pos, x, y, z)).is(IndustrialBlocks.TANK.get());
                }
            }
        }
        return modelData.derive().with(NEIGHBORS, new Neighbors(cells)).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                             @NotNull RandomSource rand, @NotNull ModelData modelData,
                                             @Nullable RenderType renderType) {
        if (state == null || side != null) return Collections.emptyList();
        Neighbors neighbors = modelData.get(NEIGHBORS);
        if (neighbors == null) neighbors = Neighbors.NONE;

        Axis axis = state.getValue(IndustrialTankBlock.AXIS);
        boolean window = state.getValue(IndustrialTankBlock.WINDOW);
        Sprites s = sprites();
        List<BakedQuad> quads = new ArrayList<>();

        for (Direction face : Direction.values()) {
            if (neighbors.at(face.getNormal())) continue;
            if (face.getAxis() == axis) {
                capQuads(quads, neighbors, face, s);
            } else {
                wallQuads(quads, neighbors, face, axis, window, s);
            }
        }

        for (Direction end : Direction.values()) {
            if (end.getAxis() != axis || neighbors.at(end.getNormal())) continue;
            for (Direction around : Direction.values()) {
                if (around.getAxis() == axis) continue;
                if (neighbors.at(around.getNormal()) && neighbors.at(around, end)) {
                    lidSideQuad(quads, end, around, s.innerPlain());
                }
            }
        }

        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a.getAxis() == axis || b.getAxis() == axis || a.getAxis().ordinal() >= b.getAxis().ordinal()) {
                    continue;
                }
                if (neighbors.at(a.getNormal()) && neighbors.at(b.getNormal()) && !neighbors.at(a, b)) {
                    cornerPostQuad(quads, a, b, axis, s.innerPlain());
                    cornerPostQuad(quads, b, a, axis, s.innerPlain());
                }
            }
        }
        return quads;
    }

    private static void cornerPostQuad(List<BakedQuad> quads, Direction toward, Direction across, Axis axis,
                                       TextureAtlasSprite sprite) {
        Direction up = Direction.get(AxisDirection.POSITIVE, axis);
        Direction normal = toward.getOpposite();
        float plane = toward.getAxisDirection() == AxisDirection.POSITIVE ? 1f - WALL : WALL;
        float lo = across.getAxisDirection() == AxisDirection.POSITIVE ? 1f - WALL : 0f;
        float hi = lo + WALL;
        boolean flip = rightOf(up, normal).getAxisDirection() == AxisDirection.NEGATIVE;
        quads.add(ownLight(quad(normal, up, plane, flip ? 1f - hi : lo, 0f, flip ? 1f - lo : hi, 1f, sprite, 0f, 0f,
                WALL, 1f)));
    }

    private static void wallQuads(List<BakedQuad> quads, Neighbors neighbors, Direction face, Axis axis,
                                  boolean window, Sprites s) {
        TextureAtlasSprite sheet = window ? s.window() : s.side();
        TextureAtlasSprite bend = window ? s.windowBend() : s.sideBend();
        Direction up = Direction.get(AxisDirection.POSITIVE, axis);
        float outerPlane = face.getAxisDirection() == AxisDirection.POSITIVE ? 1f : 0f;
        float innerPlane = face.getAxisDirection() == AxisDirection.POSITIVE ? 1f - WALL : WALL;

        quads.add(tileQuad(neighbors, face, up, rightOf(up, face), outerPlane, sheet, bend, face, false));
        Direction inward = face.getOpposite();
        quads.add(tileQuad(neighbors, face, up, rightOf(up, inward), innerPlane, sheet, bend, inward, true));
    }

    private static void capQuads(List<BakedQuad> quads, Neighbors neighbors, Direction face, Sprites s) {
        Direction up = capUp(face);
        float outerPlane = face.getAxisDirection() == AxisDirection.POSITIVE ? 1f : 0f;
        float innerPlane = face.getAxisDirection() == AxisDirection.POSITIVE ? 1f - CAP : CAP;

        quads.add(tileQuad(neighbors, face, up, rightOf(up, face), outerPlane, s.top(), s.topBend(), face, false));
        Direction inward = face.getOpposite();
        quads.add(tileQuad(neighbors, face, up, rightOf(up, inward), innerPlane, s.inner(), s.innerBend(), inward, true));
    }

    private static void lidSideQuad(List<BakedQuad> quads, Direction end, Direction around,
                                    TextureAtlasSprite sprite) {
        float plane = around.getAxisDirection() == AxisDirection.POSITIVE ? 1f : 0f;
        quads.add(quad(around, end, plane, 0f, 1f - CAP, 1f, 1f, sprite, 0f, 0f, 1f, CAP));
    }

    // vanilla lights a flat quad from the block it faces when the block is a full cube, so inner faces went black against ground or walls, tilting one vertex makes it use this block
    private static BakedQuad ownLight(BakedQuad quad) {
        int[] data = quad.getVertices();
        int axis = quad.getDirection().getAxis().ordinal();
        data[axis] = Float.floatToRawIntBits(Float.intBitsToFloat(data[axis]) + LIGHT_NUDGE);
        return quad;
    }

    private static Direction capUp(Direction face) {
        if (face.getAxis() == Axis.Y) return face == Direction.UP ? Direction.NORTH : Direction.SOUTH;
        return Direction.UP;
    }

    private static Direction rightOf(Direction up, Direction normal) {
        Vec3i u = up.getNormal();
        Vec3i n = normal.getNormal();
        Direction right = Direction.fromDelta(u.getY() * n.getZ() - u.getZ() * n.getY(),
                u.getZ() * n.getX() - u.getX() * n.getZ(), u.getX() * n.getY() - u.getY() * n.getX());
        if (right == null) throw new IllegalStateException("up " + up + " is parallel to normal " + normal);
        return right;
    }

    private static boolean connected(Neighbors neighbors, Direction toward, Direction exposure) {
        return neighbors.at(toward.getNormal()) && !neighbors.at(toward, exposure);
    }

    private static int tileIndex(Neighbors neighbors, Direction exposure, Direction up, Direction right) {
        boolean top = connected(neighbors, up, exposure);
        boolean bottom = connected(neighbors, up.getOpposite(), exposure);
        boolean rightSide = connected(neighbors, right, exposure);
        boolean leftSide = connected(neighbors, right.getOpposite(), exposure);
        int x = leftSide && rightSide ? 2 : leftSide ? 3 : rightSide ? 1 : 0;
        int y = top && bottom ? 1 : top ? 2 : bottom ? 0 : 3;
        return x + y * SHEET;
    }

    private static boolean diagonal(Neighbors neighbors, Direction a, Direction b, Direction exposure) {
        Vec3i offset = a.getNormal().offset(b.getNormal());
        return neighbors.at(offset) && !neighbors.at(offset.offset(exposure.getNormal()));
    }

    private static boolean hasBend(boolean up, boolean down, boolean left, boolean right, boolean tl, boolean tr,
                                   boolean bl, boolean br) {
        return up && left && !tl || up && right && !tr || down && left && !bl || down && right && !br;
    }

    // omni index copied from create, tiles that touch a bend use the 8x8 sheet, the rest keep the curated 4x4 one
    private static BakedQuad tileQuad(Neighbors neighbors, Direction exposure, Direction up, Direction right,
                                      float plane, TextureAtlasSprite sheet, TextureAtlasSprite bendSheet,
                                      Direction normal, boolean inner) {
        Direction left = right.getOpposite();
        Direction down = up.getOpposite();
        boolean u = connected(neighbors, up, exposure);
        boolean d = connected(neighbors, down, exposure);
        boolean l = connected(neighbors, left, exposure);
        boolean r = connected(neighbors, right, exposure);
        boolean tl = diagonal(neighbors, up, left, exposure);
        boolean tr = diagonal(neighbors, up, right, exposure);
        boolean bl = diagonal(neighbors, down, left, exposure);
        boolean br = diagonal(neighbors, down, right, exposure);
        BakedQuad quad;
        if (bendSheet != null && hasBend(u, d, l, r, tl, tr, bl, br)) {
            int tile = omniIndex(u, d, l, r, tl, tr, bl, br);
            quad = sheetQuad(normal, up, plane, bendSheet, tile, BEND_SHEET);
        } else {
            quad = sheetQuad(normal, up, plane, sheet, tileIndex(neighbors, exposure, up, right), SHEET);
        }
        return inner ? ownLight(quad) : quad;
    }

    private static int omniIndex(boolean up, boolean down, boolean left, boolean right, boolean tl, boolean tr,
                                 boolean bl, boolean br) {
        int tx = 0;
        int ty = 0;
        int borders = (up ? 0 : 1) + (down ? 0 : 1) + (left ? 0 : 1) + (right ? 0 : 1);
        if (up) tx++;
        if (down) tx += 2;
        if (left) ty++;
        if (right) ty += 2;
        if (borders == 0) {
            if (tr) tx++;
            if (tl) tx += 2;
            if (br) ty += 2;
            if (bl) ty++;
        }
        if (borders == 1) {
            if (!right && (tl || bl)) {
                ty = 4;
                tx = -1 + (bl ? 1 : 0) + (tl ? 2 : 0);
            }
            if (!left && (tr || br)) {
                ty = 5;
                tx = -1 + (br ? 1 : 0) + (tr ? 2 : 0);
            }
            if (!down && (tl || tr)) {
                ty = 6;
                tx = -1 + (tl ? 1 : 0) + (tr ? 2 : 0);
            }
            if (!up && (bl || br)) {
                ty = 7;
                tx = -1 + (bl ? 1 : 0) + (br ? 2 : 0);
            }
        }
        if (borders == 2 && (up && left && tl || down && left && bl || up && right && tr || down && right && br)) {
            tx += 3;
        }
        return tx + BEND_SHEET * ty;
    }

    private static BakedQuad sheetQuad(Direction normal, Direction up, float plane, TextureAtlasSprite sheet,
                                       int tile, int size) {
        float u0 = (tile % size) / (float) size;
        float v0 = (tile / size) / (float) size;
        return quad(normal, up, plane, 0f, 0f, 1f, 1f, sheet, u0, v0, u0 + 1f / size, v0 + 1f / size);
    }

    private static BakedQuad quad(Direction normal, Direction up, float plane, float right0, float up0,
                                  float right1, float up1, TextureAtlasSprite sprite, float u0, float v0,
                                  float u1, float v1) {
        Direction right = rightOf(up, normal);
        float[][] corners = {
                {right0, up1, u0, v0},
                {right0, up0, u0, v1},
                {right1, up0, u1, v1},
                {right1, up1, u1, v0}
        };

        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            float[] position = new float[3];
            position[normal.getAxis().ordinal()] = plane;
            position[right.getAxis().ordinal()] =
                    right.getAxisDirection() == AxisDirection.POSITIVE ? corners[i][0] : 1f - corners[i][0];
            position[up.getAxis().ordinal()] =
                    up.getAxisDirection() == AxisDirection.POSITIVE ? corners[i][1] : 1f - corners[i][1];
            float u = sprite.getU0() + corners[i][2] * (sprite.getU1() - sprite.getU0());
            float v = sprite.getV0() + corners[i][3] * (sprite.getV1() - sprite.getV0());
            QuadVertices.put(data, i, position[0], position[1], position[2], u, v, normal);
        }
        return new BakedQuad(data, -1, normal, sprite, true);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        return RENDER_TYPES;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                             @NotNull RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public @NotNull ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
}
