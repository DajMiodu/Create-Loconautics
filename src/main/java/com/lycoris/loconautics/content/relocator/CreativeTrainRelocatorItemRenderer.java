package com.lycoris.loconautics.content.relocator;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

public class CreativeTrainRelocatorItemRenderer extends CustomRenderedItemModelRenderer {

    private static final float OPEN_LEN = 0.5f;
    private static final float CLOSE_LEN = 0.5f;
    private static final float LOOP_LEN = 1.0f;
    private static final float SPIN_LEN = 1.0f;
    private static final float TAU = (float) (Math.PI * 2);

    private static final int SHUT = 0;
    private static final int OPENING = 1;
    private static final int IDLE = 2;
    private static final int CLOSING = 3;

    private static final float JAW_PIVOT_X = 0f, JAW_PIVOT_Y = 15f - 8f, JAW_PIVOT_Z = -0.2f;
    private static final float COOB_PIVOT_X = 0f, COOB_PIVOT_Y = 9f - 8f, COOB_PIVOT_Z = -0.9f;

    private static final Map<ItemStack, AnimState> STATES = new WeakHashMap<>();

    private static final class AnimState {
        boolean lastHeldTrain;
        float transitionAt = Float.NEGATIVE_INFINITY;
        int phase = SHUT;
        float phaseTime;
        float spin;
    }

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer, ItemDisplayContext transformType,
                          PoseStack ms, MultiBufferSource buffer, int light, int overlay) {

        AnimState state = STATES.computeIfAbsent(stack, s -> new AnimState());
        float now = AnimationTickHolder.getRenderTime() / 20.0f;
        boolean heldTrain = SableTrainRelocator.isRelocating() || RelocatorItemData.read(stack) != null;

        if (heldTrain != state.lastHeldTrain) {
            state.lastHeldTrain = heldTrain;
            state.transitionAt = now;
        }
        resolvePhase(state, heldTrain, now - state.transitionAt);
        state.spin = ((now % SPIN_LEN) + SPIN_LEN) % SPIN_LEN / SPIN_LEN;

        renderer.render(model.getOriginalModel(), light);

        renderJawGroup(ms, renderer, light, state, LoconauticsPartialModels.RELOCATOR_DRAWHANDLE.get());
        renderJawGroup(ms, renderer, light, state, LoconauticsPartialModels.RELOCATOR_LOWER_JAW.get());
        renderGreaterJaw(ms, renderer, light, state);
        renderOmnicoob(ms, renderer, light, state);
    }

    private void resolvePhase(AnimState state, boolean heldTrain, float elapsed) {
        if (heldTrain) {
            if (elapsed < OPEN_LEN) {
                state.phase = OPENING;
                state.phaseTime = elapsed;
            } else {
                state.phase = IDLE;
                state.phaseTime = (elapsed - OPEN_LEN) % LOOP_LEN;
            }
            return;
        }
        if (elapsed < CLOSE_LEN) {
            state.phase = CLOSING;
            state.phaseTime = elapsed;
        } else {
            state.phase = SHUT;
            state.phaseTime = 0f;
        }
    }

    private float jawRotX(AnimState state) {
        return switch (state.phase) {
            case OPENING -> state.phaseTime < 0.25f
                    ? ease(state.phaseTime, 0f, -25.5f, 0f, 0.25f)
                    : ease(state.phaseTime, -25.5f, -22.5f, 0.25f, 0.5f);
            case IDLE -> -21.25f - 1.25f * Mth.cos(TAU * state.phaseTime / LOOP_LEN);
            case CLOSING -> state.phaseTime < 0.25f ? -22.5f : easeIn(state.phaseTime, -22.5f, 0f, 0.25f, 0.5f);
            default -> 0f;
        };
    }

    private float greaterJawY(AnimState state) {
        return switch (state.phase) {
            case OPENING -> state.phaseTime < 0.25f
                    ? ease(state.phaseTime, 0f, 3.4f, 0f, 0.25f)
                    : ease(state.phaseTime, 3.4f, 3f, 0.25f, 0.5f);
            case IDLE -> 3.25f - 0.25f * Mth.cos(TAU * state.phaseTime / LOOP_LEN);
            case CLOSING -> state.phaseTime < 0.25f ? 3f : easeIn(state.phaseTime, 3f, 0f, 0.25f, 0.5f);
            default -> 0f;
        };
    }

    private float omnicoobY(AnimState state) {
        return switch (state.phase) {
            case OPENING -> state.phaseTime < 0.25f
                    ? ease(state.phaseTime, 7f, 8.75f, 0f, 0.25f)
                    : ease(state.phaseTime, 8.75f, 8.5f, 0.25f, 0.4f);
            case IDLE -> 8.6f - 0.1f * Mth.cos(TAU * state.phaseTime / LOOP_LEN);
            case CLOSING -> state.phaseTime < 0.2083f ? 8.5f : easeIn(state.phaseTime, 8.5f, 7f, 0.2083f, 0.5f);
            default -> 7f;
        };
    }

    private float omnicoobScale(AnimState state) {
        return switch (state.phase) {
            case OPENING -> state.phaseTime < 0.2f
                    ? ease(state.phaseTime, 0f, 1.15f, 0f, 0.2f)
                    : ease(state.phaseTime, 1.15f, 1f, 0.2f, 0.3833f);
            case IDLE -> 1f;
            case CLOSING -> state.phaseTime < 0.0833f ? 1f : easeIn(state.phaseTime, 1f, 0f, 0.0833f, 0.5f);
            default -> 0f;
        };
    }

    private void renderJawGroup(PoseStack ms, PartialItemModelRenderer renderer, int light, AnimState state,
                                net.minecraft.client.resources.model.BakedModel part) {
        ms.pushPose();
        float px = JAW_PIVOT_X / 16f, py = JAW_PIVOT_Y / 16f, pz = JAW_PIVOT_Z / 16f;
        ms.translate(px, py, pz);
        ms.mulPose(Axis.XP.rotationDegrees(jawRotX(state)));
        ms.translate(-px, -py, -pz);
        renderer.render(part, light);
        ms.popPose();
    }

    private void renderGreaterJaw(PoseStack ms, PartialItemModelRenderer renderer, int light, AnimState state) {
        ms.pushPose();
        ms.translate(0f, greaterJawY(state) / 16f, 0f);
        renderer.render(LoconauticsPartialModels.RELOCATOR_GREATER_JAW.get(), light);
        ms.popPose();
    }

    private void renderOmnicoob(PoseStack ms, PartialItemModelRenderer renderer, int light, AnimState state) {
        float scale = omnicoobScale(state);
        if (scale <= 0f) {
            return;
        }
        ms.pushPose();
        ms.translate(0f, omnicoobY(state) / 16f, -1.25f / 16f);

        float px = COOB_PIVOT_X / 16f, py = COOB_PIVOT_Y / 16f, pz = COOB_PIVOT_Z / 16f;
        ms.translate(px, py, pz);
        ms.mulPose(Axis.ZP.rotationDegrees(-270f - state.spin * 360f));
        ms.mulPose(Axis.YP.rotationDegrees(-45f));
        ms.mulPose(Axis.XP.rotationDegrees(180f - state.spin * 360f));
        ms.scale(scale, scale, scale);
        ms.translate(-px, -py, -pz);

        renderer.render(LoconauticsPartialModels.RELOCATOR_OMNICOOB.get(), light);
        ms.popPose();
    }

    private static float ease(float t, float v0, float v1, float t0, float t1) {
        float n = Math.clamp((t - t0) / (t1 - t0), 0f, 1f);
        return v0 + n * n * (3f - 2f * n) * (v1 - v0);
    }

    private static float easeIn(float t, float v0, float v1, float t0, float t1) {
        float n = Math.clamp((t - t0) / (t1 - t0), 0f, 1f);
        return v0 + n * n * n * (v1 - v0);
    }
}
