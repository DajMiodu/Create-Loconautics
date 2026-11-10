package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.schedule.IScheduleInput;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.ScheduleEntry;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.destination.ScheduleInstruction;
import dan200.computercraft.api.lua.LuaException;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class CcSchedule {

    private CcSchedule() {
    }

    public static Schedule fromLua(HolderLookup.Provider registries, Map<?, ?> table) throws LuaException {
        Schedule schedule = new Schedule();
        schedule.cyclic = bool(table.get("cyclic"), true);
        List<?> entries = array(table.get("entries"));
        if (entries.isEmpty()) {
            throw new LuaException("schedule needs at least one entry");
        }
        for (Object raw : entries) {
            schedule.entries.add(entry(registries, map(raw, "entry")));
        }
        return schedule;
    }

    public static Map<String, Object> toLua(Schedule schedule) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cyclic", schedule.cyclic);
        List<Object> entries = new ArrayList<>();
        for (ScheduleEntry entry : schedule.entries) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("instruction", entry.instruction.getId().toString());
            line.put("data", readData(entry.instruction));
            line.put("items", readItems(entry.instruction));
            List<Object> columns = new ArrayList<>();
            for (List<ScheduleWaitCondition> column : entry.conditions) {
                List<Object> conditions = new ArrayList<>();
                for (ScheduleWaitCondition condition : column) {
                    Map<String, Object> shown = new LinkedHashMap<>();
                    shown.put("type", condition.getId().toString());
                    shown.put("data", readData(condition));
                    shown.put("items", readItems(condition));
                    conditions.add(shown);
                }
                columns.add(conditions);
            }
            line.put("conditions", columns);
            entries.add(line);
        }
        out.put("entries", entries);
        return out;
    }

    private static ScheduleEntry entry(HolderLookup.Provider registries, Map<?, ?> raw) throws LuaException {
        ScheduleInstruction instruction = build(registries, raw,
                resolve(Schedule.INSTRUCTION_TYPES, string(raw.get("instruction"), "instruction")));
        ScheduleEntry entry = new ScheduleEntry();
        entry.instruction = instruction;
        if (!instruction.supportsConditions()) {
            return entry;
        }
        for (Object column : array(raw.get("conditions"))) {
            List<ScheduleWaitCondition> group = new ArrayList<>();
            for (Object one : array(column)) {
                Map<?, ?> conditionTable = map(one, "condition");
                group.add(build(registries, conditionTable,
                        resolve(Schedule.CONDITION_TYPES, string(conditionTable.get("type"), "type"))));
            }
            entry.conditions.add(group);
        }
        return entry;
    }

    private static <T extends IScheduleInput> T build(HolderLookup.Provider registries, Map<?, ?> raw, T target)
            throws LuaException {
        CompoundTag data = target.getData().copy();
        Object supplied = raw.get("data");
        if (supplied != null) {
            for (Map.Entry<?, ?> field : map(supplied, "data").entrySet()) {
                write(data, String.valueOf(field.getKey()), field.getValue());
            }
        }
        target.setData(registries, data);
        List<?> items = array(raw.get("items"));
        for (int slot = 0; slot < items.size() && slot < target.slotsTargeted(); slot++) {
            target.setItem(slot, item(string(items.get(slot), "item id")));
        }
        return target;
    }

    private static <T> T resolve(List<Pair<ResourceLocation, Supplier<? extends T>>> types, String name)
            throws LuaException {
        ResourceLocation exact = ResourceLocation.tryParse(name);
        List<String> known = new ArrayList<>();
        for (Pair<ResourceLocation, Supplier<? extends T>> pair : types) {
            ResourceLocation id = pair.getFirst();
            known.add(id.toString());
            if (id.equals(exact) || (!name.contains(":") && id.getPath().equals(name))) {
                return pair.getSecond().get();
            }
        }
        throw new LuaException("unknown schedule type \"" + name + "\", known types are " + String.join(", ", known));
    }

    private static void write(CompoundTag tag, String key, Object value) throws LuaException {
        if (value instanceof String text) {
            tag.putString(key, text);
            return;
        }
        if (value instanceof Boolean flag) {
            tag.putInt(key, flag ? 1 : 0);
            return;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                tag.putInt(key, (int) d);
            } else {
                tag.putDouble(key, d);
            }
            return;
        }
        if (value instanceof Map<?, ?> nested) {
            CompoundTag child = new CompoundTag();
            for (Map.Entry<?, ?> field : nested.entrySet()) {
                write(child, String.valueOf(field.getKey()), field.getValue());
            }
            tag.put(key, child);
            return;
        }
        throw new LuaException("field \"" + key + "\" must be a string, number, boolean or table");
    }

    private static Map<String, Object> readData(IScheduleInput input) {
        Map<String, Object> out = new LinkedHashMap<>();
        CompoundTag data = input.getData();
        for (String key : data.getAllKeys()) {
            Tag value = data.get(key);
            if (value instanceof NumericTag number) {
                out.put(key, number.getAsDouble());
            } else if (value instanceof StringTag) {
                out.put(key, data.getString(key));
            }
        }
        return out;
    }

    private static List<Object> readItems(IScheduleInput input) {
        List<Object> out = new ArrayList<>();
        for (int slot = 0; slot < input.slotsTargeted(); slot++) {
            ItemStack stack = input.getItem(slot);
            out.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return out;
    }

    private static ItemStack item(String id) throws LuaException {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            throw new LuaException("unknown item id \"" + id + "\"");
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    // cc hands a lua array over as a map keyed by 1.0, 2.0 and so on, so the order has to be rebuilt by index
    private static List<?> array(Object value) throws LuaException {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        Map<?, ?> table = map(value, "list");
        List<Object> out = new ArrayList<>();
        for (int index = 1; index <= table.size(); index++) {
            Object element = table.get((double) index);
            if (element == null) {
                element = table.get(index);
            }
            if (element == null) {
                throw new LuaException("list is missing index " + index + ", it must be dense");
            }
            out.add(element);
        }
        return out;
    }

    private static Map<?, ?> map(Object value, String what) throws LuaException {
        if (value instanceof Map<?, ?> table) {
            return table;
        }
        throw new LuaException(what + " must be a table");
    }

    private static String string(Object value, String what) throws LuaException {
        if (value instanceof String text) {
            return text;
        }
        throw new LuaException(what + " must be a string");
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
