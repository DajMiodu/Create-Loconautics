package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import dan200.computercraft.api.lua.LuaException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

final class CcDrivetrain {

    private CcDrivetrain() {
    }

    static String describe(String problem) {
        return switch (problem) {
            case "no_transmission" -> "this carriage has no transmission, place one on the shaft end of the bearing axle";
            case "many_transmissions" -> "this carriage has more than one transmission, keep only one";
            case "no_bearing_axle" -> "this carriage has no bearing axle";
            case "bearing_axle_no_bogey" -> "the bearing axle is not touching a bogey, place it right next to the bogey it drives";
            case "bearing_axle_wrong_axis" -> "the bearing axle does not line up with its bogey, turn it so it faces along the bogey's rails";
            case "transmission_not_touching" -> "the transmission is not touching the bearing axle, place it on the shaft end of the axle";
            case "transmission_wrong_side" -> "the transmission is on the wrong side of the bearing axle, move it to the shaft end, the side opposite the front";
            case "transmission_wrong_axis" -> "the transmission shaft does not line up with the bearing axle, rotate it with a wrench so both shafts run along one line";
            default -> problem;
        };
    }

    static Map<String, Object> report(LocomotiveHardware.Drivetrain drivetrain) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", drivetrain.ok());
        if (!drivetrain.ok()) {
            out.put("problem", drivetrain.problem());
            out.put("message", describe(drivetrain.problem()));
        }
        return out;
    }

    static ServerSubLevel subLevelAt(Level level, BlockPos pos) throws LuaException {
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            throw new LuaException("this block is not in a sub level, the carriage has to be assembled");
        }
        return sub;
    }

    static LocomotiveHardware.Drivetrain check(Level level, BlockPos pos) throws LuaException {
        return LocomotiveHardware.drivetrain(subLevelAt(level, pos));
    }

    static void require(Level level, BlockPos pos) throws LuaException {
        LocomotiveHardware.Drivetrain drivetrain = check(level, pos);
        if (!drivetrain.ok()) {
            throw new LuaException("the train cannot be driven, " + describe(drivetrain.problem()));
        }
    }
}
