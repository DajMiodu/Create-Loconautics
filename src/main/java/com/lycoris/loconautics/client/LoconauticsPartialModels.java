package com.lycoris.loconautics.client;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyle;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.item.DyeColor;

import java.util.HashMap;
import java.util.Map;

public class LoconauticsPartialModels {

    public static final PartialModel TURNTABLE_COG =
            PartialModel.of(LoconauticsConstants.id("block/turntablebearing/cog"));

    public static final PartialModel CONTROLS_LEVER =
            PartialModel.of(LoconauticsConstants.id("block/analogcontroller/controls_lever"));

    public static final PartialModel GAUGE_BACK =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/back"));
    public static final PartialModel BODY =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/body"));
    public static final PartialModel SLIM_BODY =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/slim_body"));
    public static final PartialModel BIG_BODY =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/big_body"));
    public static final PartialModel PLATE =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/plate"));
    public static final PartialModel SLIM_PLATE =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/slim_plate"));

    public static final PartialModel GAUGE_FACE =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/face"));
    public static final PartialModel BUTTON_CAP =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/button_cap"));

    public static final PartialModel LEVER_HANDLE =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/lever_handle"));
    public static final PartialModel LEVER_KNOB =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/lever_knob"));

    public record StyleKit(PartialModel handle, PartialModel knob, PartialModel base) {
    }

    public static final Map<String, StyleKit> LEVER_STYLE_KITS = new HashMap<>();

    static {
        for (PanelStyle style : PanelStyles.LEVER.all()) {
            PanelStyle.ModelKit kit = style.kit();
            if (kit == null) continue;
            String path = "block/controls/gauge/lever_" + kit.name() + "_";
            LEVER_STYLE_KITS.put(kit.name(), new StyleKit(
                    PartialModel.of(LoconauticsConstants.id(path + "handle")),
                    PartialModel.of(LoconauticsConstants.id(path + "knob")),
                    kit.base() ? PartialModel.of(LoconauticsConstants.id(path + "base")) : null));
        }
    }

    public static final Map<String, PartialModel> BUTTON_STYLE_CAPS = new HashMap<>();

    static {
        for (PanelStyle style : PanelStyles.BUTTON.all()) {
            PanelStyle.ModelKit kit = style.kit();
            if (kit == null) continue;
            BUTTON_STYLE_CAPS.put(kit.name(), PartialModel.of(
                    LoconauticsConstants.id("block/controls/gauge/button_" + kit.name() + "_cap")));
        }
    }

    public static final PartialModel LEVER_BULB =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/lever_bulb"));

    public static final PartialModel GLASS_BODY =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/glass_body"));
    public static final PartialModel GLASS_SLIM =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/glass_slim"));

    public static PartialModel glass(PanelGlass glass) {
        return switch (glass) {
            case BODY -> GLASS_BODY;
            case SLIM -> GLASS_SLIM;
        };
    }

    public static final PartialModel GAUGE_BULB =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/bulb"));

    public static final PartialModel SWITCH_BULB =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/switch_bulb"));

    public static final PartialModel SWITCH_PADDLE =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/switch_paddle"));

    public static final PartialModel PANEL_RECEIVER =
            PartialModel.of(LoconauticsConstants.id("block/controls/panel/receiver"));
    public static final PartialModel PANEL_RECEIVER_POWERED =
            PartialModel.of(LoconauticsConstants.id("block/controls/panel/receiver_powered"));

    public static final PartialModel VALVE_BASE =
            PartialModel.of(LoconauticsConstants.id("block/controls/valve/base"));
    public static final PartialModel VALVE_HANDLE =
            PartialModel.of(LoconauticsConstants.id("block/controls/valve/handle"));

    public static final PartialModel NEEDLE_LINEAR =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_linear"));
    public static final PartialModel NEEDLE_DEFAULT =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_default"));
    public static final PartialModel NEEDLE_CLASSIC =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_classic"));
    public static final PartialModel NEEDLE_VERTICAL =
            PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_vertical"));

    public static final PartialModel SCREW_LINK_LINK =
            PartialModel.of(LoconauticsConstants.id("block/screwlink/link"));
    public static final PartialModel SCREW_LINK_KNOT =
            PartialModel.of(LoconauticsConstants.id("block/screwlink/knot"));

    public static final PartialModel STEEL_CABLE_ROPE =
            PartialModel.of(LoconauticsConstants.id("block/steelcable/rope"));
    public static final PartialModel STEEL_CABLE_KNOT =
            PartialModel.of(LoconauticsConstants.id("block/steelcable/knot"));

    public static final PartialModel STEEL_CABLE_CONNECTOR_KNOT =
            PartialModel.of(LoconauticsConstants.id("block/steelcable/connector_knot"));

    public static final PartialModel HOSE_CONNECTOR_KNOT =
            PartialModel.of(LoconauticsConstants.id("block/hose/connector_knot"));

    public static final PartialModel STEEL_CABLE_WINCH_COIL =
            PartialModel.of(LoconauticsConstants.id("block/steelcable/winch_coil"));

    public static final PartialModel KNUCKLE_HAND =
            PartialModel.of(LoconauticsConstants.id("block/knuckle/hand"));
    public static final PartialModel KNUCKLE_COUPLING =
            PartialModel.of(LoconauticsConstants.id("block/knuckle/coupling"));
    public static final PartialModel KNUCKLE_PIN =
            PartialModel.of(LoconauticsConstants.id("block/knuckle/pin"));
    public static final PartialModel KNUCKLE_STRETCH_ARM =
            PartialModel.of(LoconauticsConstants.id("block/knuckle/stretch_arm"));

    public static final Map<DyeColor, PartialModel> ENGINE_PISTONS = new java.util.EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            ENGINE_PISTONS.put(color, PartialModel.of(
                    LoconauticsConstants.id("block/combustion/" + color.getSerializedName() + "/piston")));
        }
    }

    public static final Map<DyeColor, PartialModel> BUFFER_SLEEVES = new java.util.EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, PartialModel> BUFFER_RODS = new java.util.EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, PartialModel> BUFFER_HEADS = new java.util.EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            String folder = "block/buffer/" + color.getSerializedName() + "/";
            BUFFER_SLEEVES.put(color, PartialModel.of(LoconauticsConstants.id(folder + "sleeve")));
            BUFFER_RODS.put(color, PartialModel.of(LoconauticsConstants.id(folder + "rod")));
            BUFFER_HEADS.put(color, PartialModel.of(LoconauticsConstants.id(folder + "head")));
        }
    }

    public static final PartialModel HOSE_STRAND =
            PartialModel.of(LoconauticsConstants.id("block/hose/strand"));
    public static final PartialModel HOSE_KNOT =
            PartialModel.of(LoconauticsConstants.id("block/hose/knot"));

    public static final PartialModel HATCH_FLUID_BASE =
            PartialModel.of(LoconauticsConstants.id("block/hatch/fluid_hatch_gray"));
    public static final PartialModel HATCH_FLUID_LID =
            PartialModel.of(LoconauticsConstants.id("block/hatch/fluid_hatch_lid_gray"));

    public static final PartialModel FIREBOX_DOOR_LEFT =
            PartialModel.of(LoconauticsConstants.id("block/boiler/firebox/door_left"));
    public static final PartialModel FIREBOX_DOOR_RIGHT =
            PartialModel.of(LoconauticsConstants.id("block/boiler/firebox/door_right"));
    public static final PartialModel FIREBOX_LEVER_ARM =
            PartialModel.of(LoconauticsConstants.id("block/boiler/firebox/lever_arm"));

    public static final PartialModel HOSE_WINCH_COIL =
            PartialModel.of(LoconauticsConstants.id("block/hose/coil"));

    public static final PartialModel BARREL_PIPE_CONNECTOR =
            PartialModel.of(LoconauticsConstants.id("block/boiler/barrel/pipe_connector"));

    public static final PartialModel BOGEY_DRIVEN_TOP =
            PartialModel.of(LoconauticsConstants.id("block/bearingaxle/bogey/top"));

    public static final PartialModel BOGEY_SHAFTED =
            PartialModel.of(LoconauticsConstants.id("block/bearingaxle/bogey/shafted"));

    public static final PartialModel BOGEY_DRIVEN_SHAFTED =
            PartialModel.of(LoconauticsConstants.id("block/bearingaxle/bogey/driven_shafted"));

    public static final PartialModel INDUSTRIAL_PIPE_CASING =
            PartialModel.of(LoconauticsConstants.id("block/industrial/pipe/casing"));

    public static final PartialModel RELOCATOR_DRAWHANDLE =
            PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/drawhandle"));
    public static final PartialModel RELOCATOR_LOWER_JAW =
            PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/lower_jaw"));
    public static final PartialModel RELOCATOR_GREATER_JAW =
            PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/greater_jaw"));
    public static final PartialModel RELOCATOR_OMNICOOB =
            PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/omnicoob"));

    public static final java.util.Map<
            com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials,
            java.util.Map<net.minecraft.core.Direction, PartialModel>> INDUSTRIAL_PIPE_ATTACHMENTS =
            new java.util.EnumMap<>(
                    com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials.class);

    static {
        for (var type : com.simibubi.create.content.fluids.FluidTransportBehaviour
                .AttachmentTypes.ComponentPartials.values()) {
            java.util.Map<net.minecraft.core.Direction, PartialModel> map = new java.util.HashMap<>();
            for (net.minecraft.core.Direction d : net.createmod.catnip.data.Iterate.directions) {
                map.put(d, PartialModel.of(LoconauticsConstants.id("block/industrial/pipe/"
                        + net.createmod.catnip.lang.Lang.asId(type.name())
                        + "/" + net.createmod.catnip.lang.Lang.asId(d.getSerializedName()))));
            }
            INDUSTRIAL_PIPE_ATTACHMENTS.put(type, map);
        }
    }

    public static final java.util.Map<net.minecraft.world.item.DyeColor, PartialModel> CONDUCTORS_HATS =
            new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);

    static {
        for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
            CONDUCTORS_HATS.put(color, PartialModel.of(
                    LoconauticsConstants.id("entity/conductors_hat/" + color.getSerializedName())));
        }
    }

    public static PartialModel conductorsHat(long seed) {
        return CONDUCTORS_HATS.get(
                com.lycoris.loconautics.content.conductorshat.ConductorsHats.colorFor(seed));
    }

    public static void init() {}
}