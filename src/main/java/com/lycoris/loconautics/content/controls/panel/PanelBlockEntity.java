package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.foundation.LabelText;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PanelBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, PartialSafeNBT {

    private static final String PARTS_TAG = "Gauges";

    private static final String TYPE_KEY = "Type";

    private static final String LINKS_TAG = "LinkFrequencies";

    private static final String LABELS_TAG = "Labels";

    private static final String INVERTED_TAG = "Inverted";

    private static final String GLASS_TAG = "GlassOpen";

    private static final List<String> PICK_TAGS = List.of(PARTS_TAG, LINKS_TAG, LABELS_TAG, INVERTED_TAG, GLASS_TAG);

    private static final double BULB_CHASE_SPEED = 0.7;

    private static final long SETTLED_MILLIS = 100_000L;

    private static final long HOLD_REPEAT_TICKS = 5L;

    protected final Map<PanelSlot, PanelPart> parts = new EnumMap<>(PanelSlot.class);

    private final Map<PanelSlot, LerpedFloat> animations = new EnumMap<>(PanelSlot.class);

    private final Map<PanelSlot, LerpedFloat> bulbs = new EnumMap<>(PanelSlot.class);

    private final Map<PanelSlot, PanelLinkTransmitter> transmitters = new EnumMap<>(PanelSlot.class);

    private final Map<PanelSlot, Component> labels = new EnumMap<>(PanelSlot.class);

    private final Set<PanelSlot> inverted = EnumSet.noneOf(PanelSlot.class);

    private final Set<PanelSlot> glassOpen = EnumSet.noneOf(PanelSlot.class);

    private final Map<PanelSlot, GlassLid> lids = new EnumMap<>(PanelSlot.class);

    private final Map<PanelSlot, Long> lastGlassUse = new EnumMap<>(PanelSlot.class);

    private record GlassLid(boolean open, long startMillis) {
    }

    public PanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void onAssembled(BlockPos shift) {
        assemblyShift = shift;
    }

    private void relocateParts() {
        BlockPos shift = assemblyShift;
        assemblyShift = null;
        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = parts.get(slot);
            if (part == null) continue;
            PanelPart moved = part.relocated(level, shift);
            if (!moved.equals(part)) replacePart(slot, moved);
        }
    }

    @Override
    public void tick() {
        super.tick();
        boolean server = level != null && !level.isClientSide;
        if (server && assemblyShift != null) relocateParts();
        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = parts.get(slot);
            if (part == null) continue;
            if (server) {
                part.serverTick(this, slot);
                transmit(slot, part);
            }
            LerpedFloat animation = animation(slot);
            animation.chase(part.animationTarget(this), part.chaseSpeed(), LerpedFloat.Chaser.EXP);
            animation.tickChaser();

            LerpedFloat bulb = bulbs.computeIfAbsent(slot, k -> LerpedFloat.linear().startWithValue(0));
            bulb.chase(bulbTarget(slot, part), BULB_CHASE_SPEED, LerpedFloat.Chaser.EXP);
            bulb.tickChaser();
        }
    }

    private float bulbTarget(PanelSlot slot, PanelPart part) {
        return part.lightLevel(this, slot);
    }

    public float bulbGlow(PanelSlot slot, float partialTick) {
        LerpedFloat bulb = bulbs.get(slot);
        return bulb == null ? 0f : bulb.getValue(partialTick);
    }

    private void transmit(PanelSlot slot, PanelPart part) {
        PanelLinkTransmitter transmitter = transmitters.get(slot);
        if (transmitter != null && part instanceof RedstoneLinkedPart linked) {
            transmitter.transmit(level, outputSignal(slot, linked.signal(level)));
        }
    }

    @Nullable
    public PanelLinkTransmitter transmitter(PanelSlot slot) {
        return transmitters.get(slot);
    }

    public boolean hasLinkFrequency(PanelPart part) {
        for (PanelSlot slot : transmitters.keySet()) {
            if (parts.get(slot) == part) return true;
        }
        return false;
    }

    public void setLinkFrequency(PanelSlot slot, ItemStack first, ItemStack second) {
        dropTransmitter(slot);
        if (!PanelLinkTransmitter.isEmpty(first, second)) {
            transmitters.put(slot, new PanelLinkTransmitter(this, first, second));
        }
        notifyUpdate();
    }

    private void dropTransmitter(PanelSlot slot) {
        PanelLinkTransmitter transmitter = transmitters.remove(slot);
        if (transmitter != null && level != null) transmitter.release(level);
    }

    private void dropAllTransmitters() {
        for (PanelSlot slot : PanelSlot.values()) dropTransmitter(slot);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        dropAllTransmitters();
    }

    public LerpedFloat animation(PanelSlot slot) {
        return animations.computeIfAbsent(slot, k -> {
            PanelPart part = parts.get(k);
            return part == null ? LerpedFloat.linear().startWithValue(0) : part.newAnimation();
        });
    }

    public boolean hasPart(PanelSlot slot) {
        return parts.containsKey(slot);
    }

    public int partCount() {
        return parts.size();
    }

    public boolean isFull() {
        return parts.size() >= slots().length;
    }

    public PanelSlot[] slots() {
        return PanelSlot.values();
    }

    @Nullable
    public PanelPart getPart(PanelSlot slot) {
        return parts.get(slot);
    }

    public ItemStack stackForSlot(PanelSlot slot) {
        PanelPart part = parts.get(slot);
        if (part == null) return ItemStack.EMPTY;
        ItemStack stack = part.asStack();
        Component label = labels.get(slot);
        if (label != null) stack.set(DataComponents.CUSTOM_NAME, label);
        return stack;
    }

    public boolean isInverted(PanelSlot slot) {
        return inverted.contains(slot);
    }

    public void setInverted(PanelSlot slot, boolean value) {
        if (!hasPart(slot) || inverted.contains(slot) == value) return;
        if (value) inverted.add(slot);
        else inverted.remove(slot);
        notifyUpdate();
    }

    public boolean isGlassClosed(PanelSlot slot) {
        PanelPart part = parts.get(slot);
        return part != null && part.glass() != null && !glassOpen.contains(slot);
    }

    public void toggleGlassFromUse(PanelSlot slot) {
        if (level == null) return;
        long now = level.getGameTime();
        Long last = lastGlassUse.put(slot, now);
        if (last != null && now - last <= HOLD_REPEAT_TICKS) return;
        toggleGlass(slot);
    }

    public void toggleGlass(PanelSlot slot) {
        setGlassOpen(slot, !glassOpen.contains(slot));
    }

    public void setGlassOpen(PanelSlot slot, boolean value) {
        PanelPart part = parts.get(slot);
        if (part == null || part.glass() == null || glassOpen.contains(slot) == value) return;
        if (value) glassOpen.add(slot);
        else glassOpen.remove(slot);
        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, value ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.5f, 1.4f);
        }
        notifyUpdate();
    }

    public float glassAngle(PanelSlot slot) {
        boolean open = glassOpen.contains(slot);
        long now = Util.getMillis();
        GlassLid lid = lids.get(slot);
        if (lid == null) lid = new GlassLid(open, now - SETTLED_MILLIS);
        else if (lid.open() != open) lid = new GlassLid(open, now);
        lids.put(slot, lid);
        return PanelGlassLid.angle(lid.open(), (now - lid.startMillis()) / 1000f);
    }

    public int outputSignal(PanelSlot slot, int signal) {
        return inverted.contains(slot) ? RedstoneLinkedPart.MAX_SIGNAL - signal : signal;
    }

    @Nullable
    public Component label(PanelSlot slot) {
        return labels.get(slot);
    }

    public void setLabel(PanelSlot slot, @Nullable Component label) {
        if (!hasPart(slot)) return;
        if (label == null) labels.remove(slot);
        else labels.put(slot, LabelText.parse(label));
        notifyUpdate();
    }

    @Nullable
    public PanelSlot firstFreeSlot(PanelSlot preferred) {
        if (!hasPart(preferred)) return preferred;
        for (PanelSlot slot : slots()) {
            if (!hasPart(slot)) return slot;
        }
        return null;
    }

    @Nullable private BlockPos assemblyShift;

    private boolean centered;

    public boolean isCentered() {
        return centered && parts.size() == 1;
    }

    @Nullable
    public PanelSlot soleSlot() {
        return isCentered() ? parts.keySet().iterator().next() : null;
    }

    private void vacateCenter(PanelSlot incoming) {
        PanelSlot sole = soleSlot();
        centered = false;
        if (sole == null || sole == PanelSlot.TOP_LEFT || incoming == PanelSlot.TOP_LEFT) return;
        parts.put(PanelSlot.TOP_LEFT, parts.remove(sole));
        LerpedFloat animation = animations.remove(sole);
        if (animation != null) animations.put(PanelSlot.TOP_LEFT, animation);
        LerpedFloat bulb = bulbs.remove(sole);
        if (bulb != null) bulbs.put(PanelSlot.TOP_LEFT, bulb);
        Component label = labels.remove(sole);
        if (label != null) labels.put(PanelSlot.TOP_LEFT, label);
        if (inverted.remove(sole)) inverted.add(PanelSlot.TOP_LEFT);
        if (glassOpen.remove(sole)) glassOpen.add(PanelSlot.TOP_LEFT);
        lids.remove(sole);
        PanelLinkTransmitter transmitter = transmitters.remove(sole);
        if (transmitter != null) transmitters.put(PanelSlot.TOP_LEFT, transmitter);
    }

    public boolean putPart(PanelSlot slot, PanelPart part, boolean center) {
        if (hasPart(slot)) return false;
        vacateCenter(slot);
        if (parts.isEmpty()) centered = center;
        parts.put(slot, part);
        syncSlots();
        return true;
    }

    public boolean putPart(PanelSlot slot, PanelPart part) {
        return putPart(slot, part, false);
    }

    public void replacePart(PanelSlot slot, PanelPart part) {
        if (!hasPart(slot)) return;
        parts.put(slot, part);
        notifyUpdate();
    }

    public boolean removePart(PanelSlot slot) {
        PanelPart removed = parts.remove(slot);
        if (removed == null) return false;
        if (level != null && !level.isClientSide) removed.onRemoved(this);
        dropTransmitter(slot);
        animations.remove(slot);
        bulbs.remove(slot);
        labels.remove(slot);
        inverted.remove(slot);
        glassOpen.remove(slot);
        lids.remove(slot);
        lastGlassUse.remove(slot);
        syncSlots();
        return true;
    }

    public void releaseParts() {
        if (level == null || level.isClientSide) return;
        parts.values().forEach(part -> part.onRemoved(this));
        dropAllTransmitters();
    }

    protected void syncSlots() {
        if (level == null) return;
        BlockState state = getBlockState();
        for (PanelSlot slot : PanelSlot.values()) {
            if (!state.hasProperty(slot.property())) continue;
            state = state.setValue(slot.property(), hasPart(slot));
        }
        if (state != getBlockState()) {
            level.setBlock(worldPosition, state, 3);
        }
        notifyUpdate();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (parts.isEmpty()) return false;
        if (net.minecraft.client.Minecraft.getInstance().screen != null) return false;
        if (com.lycoris.loconautics.content.controls.panellever.LeverHoldInteraction.gripped()) return false;

        PanelSlot looked = lookedAtSlot();
        PanelPart lookedPart = looked == null ? null : parts.get(looked);
        if (lookedPart != null) {
            addPartTooltip(looked, lookedPart, tooltip);
        } else {
            parts.forEach((slot, part) -> addPartTooltip(slot, part, tooltip));
        }
        return true;
    }

    private void addPartTooltip(PanelSlot slot, PanelPart part, List<Component> tooltip) {
        int title = tooltip.size();
        part.addToGoggleTooltip(this, tooltip);
        Component label = labels.get(slot);
        if (label != null && tooltip.size() > title) {
            tooltip.add(title + 1, Component.literal("    ").append(label));
        }
        if (inverted.contains(slot) && tooltip.size() > title) {
            LoconauticsLang.translate("gui.goggles.panel_inverted")
                    .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
    }

    @Nullable
    private PanelSlot lookedAtSlot() {
        if (!(net.minecraft.client.Minecraft.getInstance().hitResult
                instanceof net.minecraft.world.phys.BlockHitResult hit)) {
            return null;
        }
        if (!hit.getBlockPos().equals(worldPosition)) return null;
        if (!(getBlockState().getBlock() instanceof PanelHost host)) return null;
        return host.slotFor(this, worldPosition, getBlockState(), hit.getLocation());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        CompoundTag slots = new CompoundTag();
        parts.forEach((slot, part) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(TYPE_KEY, part.type().getSerializedName());
            part.write(entry);
            slots.put(slot.getSerializedName(), entry);
        });
        tag.put(PARTS_TAG, slots);
        CompoundTag links = new CompoundTag();
        transmitters.forEach((slot, transmitter) ->
                links.put(slot.getSerializedName(), transmitter.write(registries)));
        tag.put(LINKS_TAG, links);
        CompoundTag names = new CompoundTag();
        labels.forEach((slot, label) ->
                names.putString(slot.getSerializedName(), Component.Serializer.toJson(label, registries)));
        tag.put(LABELS_TAG, names);
        CompoundTag flipped = new CompoundTag();
        inverted.forEach(slot -> flipped.putBoolean(slot.getSerializedName(), true));
        tag.put(INVERTED_TAG, flipped);
        CompoundTag opened = new CompoundTag();
        glassOpen.forEach(slot -> opened.putBoolean(slot.getSerializedName(), true));
        tag.put(GLASS_TAG, opened);
        tag.putBoolean("Centered", centered);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        write(tag, registries, false);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        PanelSlot picked = level != null && level.isClientSide ? lookedAtSlot() : null;
        for (String key : PICK_TAGS) {
            if (picked == null) tag.remove(key);
            else tag.getCompound(key).getAllKeys().removeIf(slot -> !slot.equals(picked.getSerializedName()));
        }
        tag.remove("Centered");
        if (picked != null) {
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
        }
    }

    public void applyPicked(PanelSlot target, CompoundTag picked, HolderLookup.Provider registries) {
        CompoundTag slots = picked.getCompound(PARTS_TAG);
        if (slots.getAllKeys().size() != 1) return;
        String source = slots.getAllKeys().iterator().next();
        CompoundTag entry = slots.getCompound(source);
        PanelPart current = parts.get(target);
        PanelPartType type = PanelPartType.byName(entry.getString(TYPE_KEY));
        if (current == null || type != current.type()) return;
        PanelPart part = type.read(entry);
        if (part != null) replacePart(target, part);
        setInverted(target, picked.getCompound(INVERTED_TAG).getBoolean(source));
        setGlassOpen(target, picked.getCompound(GLASS_TAG).getBoolean(source));
        CompoundTag names = picked.getCompound(LABELS_TAG);
        if (names.contains(source)) {
            Component label = Component.Serializer.fromJson(names.getString(source), registries);
            if (label != null) setLabel(target, label);
        }
        CompoundTag links = picked.getCompound(LINKS_TAG);
        if (links.contains(source)) {
            dropTransmitter(target);
            transmitters.put(target, PanelLinkTransmitter.read(this, links.getCompound(source), registries));
            notifyUpdate();
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        Map<PanelSlot, PanelPartType> before = new EnumMap<>(PanelSlot.class);
        parts.forEach((slot, part) -> before.put(slot, part.type()));

        centered = tag.getBoolean("Centered");
        parts.clear();
        CompoundTag slots = tag.getCompound(PARTS_TAG);
        for (PanelSlot slot : PanelSlot.values()) {
            String key = slot.getSerializedName();
            if (!slots.contains(key)) continue;
            CompoundTag entry = slots.getCompound(key);
            PanelPartType partType = PanelPartType.byName(entry.getString(TYPE_KEY));
            if (partType == null) continue;
            PanelPart part = partType.read(entry);
            if (part != null) parts.put(slot, part);
        }

        inverted.clear();
        CompoundTag flipped = tag.getCompound(INVERTED_TAG);
        for (PanelSlot slot : PanelSlot.values()) {
            if (flipped.getBoolean(slot.getSerializedName()) && parts.containsKey(slot)) inverted.add(slot);
        }

        glassOpen.clear();
        CompoundTag opened = tag.getCompound(GLASS_TAG);
        for (PanelSlot slot : PanelSlot.values()) {
            if (opened.getBoolean(slot.getSerializedName()) && parts.containsKey(slot)) glassOpen.add(slot);
        }

        labels.clear();
        CompoundTag names = tag.getCompound(LABELS_TAG);
        for (PanelSlot slot : PanelSlot.values()) {
            String key = slot.getSerializedName();
            if (!names.contains(key) || !parts.containsKey(slot)) continue;
            Component label = Component.Serializer.fromJson(names.getString(key), registries);
            if (label != null) labels.put(slot, label);
        }

        dropAllTransmitters();
        CompoundTag links = tag.getCompound(LINKS_TAG);
        for (PanelSlot slot : PanelSlot.values()) {
            String key = slot.getSerializedName();
            if (links.contains(key)) transmitters.put(slot, PanelLinkTransmitter.read(this, links.getCompound(key), registries));
        }

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart now = parts.get(slot);
            if (now == null || before.get(slot) != now.type()) {
                animations.remove(slot);
                bulbs.remove(slot);
            }
        }

        if (!clientPacket && level != null && !level.isClientSide) notifyUpdate();
    }
}