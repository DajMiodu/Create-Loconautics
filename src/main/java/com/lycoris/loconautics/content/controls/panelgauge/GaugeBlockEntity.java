package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GaugeBlockEntity extends PanelBlockEntity {

    public record Gauge(BlockPos firebox, GaugeMetric metric, int background, int front,
                        int needleColor, int options) implements PanelPart {

        public static final BlockPos UNBOUND = new BlockPos(0, -2048, 0);

        public boolean bound() {
            return !UNBOUND.equals(firebox);
        }

        public GaugeNeedle needle() {
            return GaugeNeedle.forBackground(background);
        }

        public static Gauge read(CompoundTag tag) {
            GaugeMetric metric = GaugeMetric.byName(tag.getString("Metric"));
            int background = Mth.clamp(tag.contains("Background")
                    ? tag.getInt("Background") : DEFAULT_BACKGROUND, 0, 35);
            int front = Mth.clamp(tag.contains("Front") ? tag.getInt("Front") : DEFAULT_FRONT, 0, 3);
            int needleColor = GaugeNeedle.clampColor(tag.contains("NeedleColor")
                    ? tag.getInt("NeedleColor") : DEFAULT_NEEDLE_COLOR);
            int options = PanelStyles.GAUGE.clampOptions(tag.contains("Options")
                    ? tag.getInt("Options") : DEFAULT_OPTIONS);
            return new Gauge(BlockPos.of(tag.getLong("Firebox")), metric, background, front, needleColor, options);
        }

        @Override
        public PanelPart relocated(Level level, BlockPos shift) {
            if (!bound()) return this;
            return new Gauge(PanelPart.followMoved(level, firebox, shift), metric, background, front, needleColor, options);
        }

        @Override
        public boolean bindFromHeld(PanelBlockEntity panel, PanelSlot slot, Player player) {
            if (bound() || !(panel instanceof GaugeBlockEntity gaugePanel)) return false;
            ItemStack held = player.getMainHandItem();
            BlockPos target = held.getItem() instanceof GaugeBlockItem ? GaugeBlockItem.boundFirebox(held) : null;
            if (target == null) return false;
            if (!player.level().isClientSide) {
                gaugePanel.replacePart(slot, new Gauge(target, GaugeBlockItem.boundMetric(held), background, front,
                        needleColor, options));
            }
            return true;
        }

        @Override
        public PanelPartType type() {
            return PanelPartType.GAUGE;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putLong("Firebox", firebox.asLong());
            tag.putString("Metric", metric.getSerializedName());
            tag.putInt("Background", background);
            tag.putInt("Front", front);
            tag.putInt("NeedleColor", needleColor);
            tag.putInt("Options", options);
        }

        @Override
        public ItemStack asStack() {
            return GaugeBlockItem.stackFor(this);
        }

        @Override
        public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
            return InteractionResult.PASS;
        }

        @Override
        public boolean consumesUse() {
            return false;
        }

        @Override
        public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
            PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
            return InteractionResult.CONSUME;
        }

        @Override
        public float animationTarget(PanelBlockEntity panel) {
            return metric.normalized(sourceOf(panel, this));
        }

        @Override
        public float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
            return panel.animation(slot).getValue();
        }

        @Override
        public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
            LoconauticsLang.translate("gui.goggles.gauge_stats").forGoggles(tooltip);
            BlockEntity source = sourceOf(panel, this);
            if (source == null) {
                PanelPart.addDisconnected(tooltip);
                return;
            }
            LoconauticsLang.translate(metric.langKey())
                    .style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.builder().text(metric.read(source))
                    .style(metric.accepts(source) && !metric.faulted(source)
                            ? metric.colorFor(source) : ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }
    }

    public static final int DEFAULT_BACKGROUND = 9;
    public static final int DEFAULT_FRONT = 3;
    public static final int DEFAULT_NEEDLE_COLOR = 4;
    public static final int DEFAULT_OPTIONS = PanelStyles.GAUGE.defaultOptions();

    public GaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public LerpedFloat needle(PanelSlot slot) {
        return animation(slot);
    }

    public boolean hasGauge(PanelSlot slot) {
        return getGauge(slot) != null;
    }

    @Nullable
    public Gauge getGauge(PanelSlot slot) {
        return getPart(slot) instanceof Gauge gauge ? gauge : null;
    }

    public int gaugeCount() {
        return partCount();
    }

    public boolean addGauge(PanelSlot slot, BlockPos firebox, GaugeMetric metric) {
        return putPart(slot, new Gauge(firebox, metric, metric.defaultBackground(), DEFAULT_FRONT,
                DEFAULT_NEEDLE_COLOR, DEFAULT_OPTIONS));
    }

    public boolean removeGauge(PanelSlot slot) {
        return removePart(slot);
    }

    public void configure(PanelSlot slot, GaugeMetric metric,
                          int background, int front, int needleColor, int options) {
        Gauge gauge = getGauge(slot);
        if (gauge == null) return;
        replacePart(slot, new Gauge(gauge.firebox(), accepts(gauge, metric) ? metric : gauge.metric(),
                Mth.clamp(background, 0, 35),
                Mth.clamp(front, 0, 3),
                GaugeNeedle.clampColor(needleColor),
                PanelStyles.GAUGE.clampOptions(options)));
    }

    private boolean accepts(Gauge gauge, GaugeMetric metric) {
        return metric.group() == gauge.metric().group() || metric.group() == liveGroup(gauge);
    }

    @Nullable
    public GaugeMetric.Group liveGroup(Gauge gauge) {
        return GaugeMetric.Group.of(sourceOf(this, gauge));
    }

    public GaugeMetric pickableMetric(Gauge gauge) {
        GaugeMetric.Group live = liveGroup(gauge);
        return live == null || live == gauge.metric().group()
                ? gauge.metric() : GaugeMetric.defaultFor(live);
    }

    public void openConfiguration(Player player, PanelSlot slot) {
        if (!hasGauge(slot) || level == null || level.isClientSide) return;
        PanelFrequencyMenu.open(player, worldPosition, slot);
    }

    @Nullable
    public BlockEntity sourceOf(Gauge gauge) {
        return sourceOf(this, gauge);
    }

    @Nullable
    static BlockEntity sourceOf(PanelBlockEntity panel, @Nullable Gauge gauge) {
        if (panel.getLevel() == null || gauge == null) return null;
        BlockEntity source = panel.getLevel().getBlockEntity(gauge.firebox());
        if (source == null && gauge.metric().group() == GaugeMetric.Group.SMALL_ENGINE) {
            return SmallEngineBlockEntity.resolve(panel.getLevel(), gauge.firebox());
        }
        return source;
    }
}
