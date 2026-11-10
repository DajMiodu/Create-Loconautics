package com.lycoris.loconautics.trainphysics.visual;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BogeyYawVisual {

    private static final String RAIL_OFF_X_KEY = "LoconauticsRailOffX";
    private static final String RAIL_OFF_Y_KEY = "LoconauticsRailOffY";
    private static final String RAIL_OFF_Z_KEY = "LoconauticsRailOffZ";
    private static final String BODY_CT_X_KEY = "LoconauticsBodyCounterX";
    private static final String BODY_CT_Y_KEY = "LoconauticsBodyCounterY";
    private static final String BODY_CT_Z_KEY = "LoconauticsBodyCounterZ";
    private static final String LINK_GRAPH_KEY = "LoconauticsRailGraph";
    private static final String LINK_LEAD_N1_KEY = "LoconauticsRailLeadN1";
    private static final String LINK_LEAD_N2_KEY = "LoconauticsRailLeadN2";
    private static final String LINK_LEAD_POS_KEY = "LoconauticsRailLeadPos";
    private static final String LINK_TRAIL_N1_KEY = "LoconauticsRailTrailN1";
    private static final String LINK_TRAIL_N2_KEY = "LoconauticsRailTrailN2";
    private static final String LINK_TRAIL_POS_KEY = "LoconauticsRailTrailPos";
    private static final String LINK_FLIP_KEY = "LoconauticsRailChordFlip";
    private static final String SWING_X_KEY = "LoconauticsWheelSwingX";
    private static final String SWING_Y_KEY = "LoconauticsWheelSwingY";
    private static final String SWING_Z_KEY = "LoconauticsWheelSwingZ";
    private static final String SWING_W_KEY = "LoconauticsWheelSwingW";

    private BogeyYawVisual() {
    }

    private static void sync(AbstractBogeyBlockEntity be) {
        be.setChanged();
        var level = be.getLevel();
        if (level != null) {
            var pos = be.getBlockPos();
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 2);
        }
    }

    public static void clearRailBodyPose(AbstractBogeyBlockEntity be) {
        CompoundTag data = be.getBogeyData();
        if (data == null || !data.contains(RAIL_OFF_X_KEY)) {
            return;
        }
        for (String key : new String[] { RAIL_OFF_X_KEY, RAIL_OFF_Y_KEY, RAIL_OFF_Z_KEY,
                BODY_CT_X_KEY, BODY_CT_Y_KEY, BODY_CT_Z_KEY }) {
            data.remove(key);
        }
        setRailLink(be, null);
        be.setBogeyData(data);
        sync(be);
    }

    public static Vector3f getRailLocalOffset(AbstractBogeyBlockEntity be, Vector3f dest) {
        CompoundTag data = be.getBogeyData();
        if (data == null || !data.contains(RAIL_OFF_X_KEY)) {
            return dest.zero();
        }
        return dest.set(data.getFloat(RAIL_OFF_X_KEY), data.getFloat(RAIL_OFF_Y_KEY), data.getFloat(RAIL_OFF_Z_KEY));
    }

    public static Vector3f getBodyCounter(AbstractBogeyBlockEntity be, Vector3f dest) {
        CompoundTag data = be.getBogeyData();
        if (data == null || !data.contains(BODY_CT_X_KEY)) {
            return dest.zero();
        }
        return dest.set(data.getFloat(BODY_CT_X_KEY), data.getFloat(BODY_CT_Y_KEY), data.getFloat(BODY_CT_Z_KEY));
    }

    public static Quaternionf getWheelSwing(AbstractBogeyBlockEntity be, Quaternionf dest) {
        CompoundTag data = be.getBogeyData();
        if (data == null || !data.contains(SWING_W_KEY)) {
            return null;
        }
        return dest.set(data.getFloat(SWING_X_KEY), data.getFloat(SWING_Y_KEY), data.getFloat(SWING_Z_KEY),
                data.getFloat(SWING_W_KEY));
    }

    public static void setWheelSwing(AbstractBogeyBlockEntity be, Quaternionf swing) {
        CompoundTag data = be.getBogeyData();
        boolean present = data != null && data.contains(SWING_W_KEY);
        if (swing == null) {
            if (!present) {
                return;
            }
            for (String key : new String[] { SWING_X_KEY, SWING_Y_KEY, SWING_Z_KEY, SWING_W_KEY }) {
                data.remove(key);
            }
        } else {
            if (data == null) {
                data = new CompoundTag();
            }
            if (present && Math.abs(data.getFloat(SWING_X_KEY) - swing.x) < 0.0008f
                    && Math.abs(data.getFloat(SWING_Y_KEY) - swing.y) < 0.0008f
                    && Math.abs(data.getFloat(SWING_Z_KEY) - swing.z) < 0.0008f
                    && Math.abs(data.getFloat(SWING_W_KEY) - swing.w) < 0.0008f) {
                return;
            }
            data.putFloat(SWING_X_KEY, swing.x);
            data.putFloat(SWING_Y_KEY, swing.y);
            data.putFloat(SWING_Z_KEY, swing.z);
            data.putFloat(SWING_W_KEY, swing.w);
        }
        be.setBogeyData(data);
        sync(be);
    }

    public record Link(java.util.UUID graphId, int leadNode1, int leadNode2, float leadPos,
                       int trailNode1, int trailNode2, float trailPos, boolean chordFlipped) {
    }

    public static Link getRailLink(AbstractBogeyBlockEntity be) {
        CompoundTag data = be.getBogeyData();
        if (data == null || !data.hasUUID(LINK_GRAPH_KEY)) {
            return null;
        }
        return new Link(data.getUUID(LINK_GRAPH_KEY),
                data.getInt(LINK_LEAD_N1_KEY), data.getInt(LINK_LEAD_N2_KEY), data.getFloat(LINK_LEAD_POS_KEY),
                data.getInt(LINK_TRAIL_N1_KEY), data.getInt(LINK_TRAIL_N2_KEY), data.getFloat(LINK_TRAIL_POS_KEY),
                data.getBoolean(LINK_FLIP_KEY));
    }

    public static void setRailLink(AbstractBogeyBlockEntity be, Link link) {
        CompoundTag data = be.getBogeyData();
        if (data == null) {
            data = new CompoundTag();
        }
        if (link == null) {
            if (!data.hasUUID(LINK_GRAPH_KEY)) {
                return;
            }
            for (String key : new String[] { LINK_GRAPH_KEY, LINK_LEAD_N1_KEY, LINK_LEAD_N2_KEY, LINK_LEAD_POS_KEY,
                    LINK_TRAIL_N1_KEY, LINK_TRAIL_N2_KEY, LINK_TRAIL_POS_KEY, LINK_FLIP_KEY }) {
                data.remove(key);
            }
        } else {
            data.putUUID(LINK_GRAPH_KEY, link.graphId());
            data.putInt(LINK_LEAD_N1_KEY, link.leadNode1());
            data.putInt(LINK_LEAD_N2_KEY, link.leadNode2());
            data.putFloat(LINK_LEAD_POS_KEY, link.leadPos());
            data.putInt(LINK_TRAIL_N1_KEY, link.trailNode1());
            data.putInt(LINK_TRAIL_N2_KEY, link.trailNode2());
            data.putFloat(LINK_TRAIL_POS_KEY, link.trailPos());
            data.putBoolean(LINK_FLIP_KEY, link.chordFlipped());
        }
        be.setBogeyData(data);
    }

    public static void setRailPose(AbstractBogeyBlockEntity be, Link link, Vector3f offset, Vector3f counter) {
        CompoundTag data = be.getBogeyData();
        float curOx = 0.0f, curOy = 0.0f, curOz = 0.0f;
        if (data != null) {
            curOx = data.getFloat(RAIL_OFF_X_KEY);
            curOy = data.getFloat(RAIL_OFF_Y_KEY);
            curOz = data.getFloat(RAIL_OFF_Z_KEY);
        }
        if (java.util.Objects.equals(link, getRailLink(be))
                && Math.abs(curOx - offset.x) < 0.0008f
                && Math.abs(curOy - offset.y) < 0.0008f
                && Math.abs(curOz - offset.z) < 0.0008f
                && counter.distanceSquared(getBodyCounter(be, new Vector3f())) < 0.0008f * 0.0008f
                && data != null && data.contains(RAIL_OFF_X_KEY)) {
            return;
        }
        setRailLink(be, link);
        data = be.getBogeyData();
        if (data == null) {
            data = new CompoundTag();
        }
        data.putFloat(RAIL_OFF_X_KEY, offset.x);
        data.putFloat(RAIL_OFF_Y_KEY, offset.y);
        data.putFloat(RAIL_OFF_Z_KEY, offset.z);
        data.putFloat(BODY_CT_X_KEY, counter.x);
        data.putFloat(BODY_CT_Y_KEY, counter.y);
        data.putFloat(BODY_CT_Z_KEY, counter.z);
        be.setBogeyData(data);
        sync(be);
    }
}
