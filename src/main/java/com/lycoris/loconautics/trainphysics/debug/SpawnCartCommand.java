package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.content.relocator.RelocatorItemData;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.spawn.TrainStructures;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpawnCartCommand {

    private SpawnCartCommand() {
    }

    private static final int MAX_CARTS = 64;
    private static final int END_DEPTH = 2;

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("spawn")
                .executes(context -> give(context.getSource(), 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_CARTS))
                        .executes(context -> give(context.getSource(),
                                IntegerArgumentType.getInteger(context, "count"))))
                .then(Commands.literal("boiler")
                        .executes(context -> placeBoiler(context.getSource())))
                .then(Commands.literal("boilercart")
                        .executes(context -> giveBoilerCart(context.getSource()))));
    }

    // a knuckle only survives on a solid block, so each end gets a stone behind it
    private static CompoundTag withKnuckleEnds(CompoundTag base) {
        CompoundTag tag = base.copy();
        ListTag size = tag.getList("size", Tag.TAG_INT);
        int length = size.getInt(2);
        size.set(2, IntTag.valueOf(length + 2 * END_DEPTH));
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            ListTag pos = blocks.getCompound(i).getList("pos", Tag.TAG_INT);
            pos.set(2, IntTag.valueOf(pos.getInt(2) + END_DEPTH));
        }
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        int stone = paletteIndex(palette, Blocks.STONE.defaultBlockState());
        BlockState knuckle = LoconauticsRegistries.KNUCKLE.get().defaultBlockState();
        int northKnuckle = paletteIndex(palette, knuckle.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        int southKnuckle = paletteIndex(palette, knuckle.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        int last = length + 2 * END_DEPTH - 1;
        blocks.add(blockAt(last - 1, stone));
        blocks.add(blockAt(last, southKnuckle));
        blocks.add(blockAt(1, stone));
        blocks.add(blockAt(0, northKnuckle));
        tag.put("palette", palette);
        tag.put("blocks", blocks);
        return tag;
    }

    private static int paletteIndex(ListTag palette, BlockState state) {
        CompoundTag entry = NbtUtils.writeBlockState(state);
        int found = palette.indexOf(entry);
        if (found >= 0) {
            return found;
        }
        palette.add(entry);
        return palette.size() - 1;
    }

    private static CompoundTag blockAt(int z, int paletteIndex) {
        CompoundTag block = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(z));
        block.put("pos", pos);
        block.putInt("state", paletteIndex);
        return block;
    }

    private static int placeBoiler(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("only a player has a front to place the boiler in"));
            return 0;
        }
        Path file = FMLPaths.GAMEDIR.get().resolve("schematics").resolve("boiler.nbt");
        if (!Files.isRegularFile(file)) {
            source.sendFailure(Component.literal("no boiler.nbt in the schematics folder"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        StructureTemplate template = new StructureTemplate();
        try {
            template.load(level.holderLookup(Registries.BLOCK), NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
        } catch (IOException e) {
            source.sendFailure(Component.literal("could not read boiler.nbt, " + e.getMessage()));
            return 0;
        }
        Vec3i size = template.getSize();
        BlockPos front = player.blockPosition().relative(player.getDirection(), 2 + Math.max(size.getX(), size.getZ()) / 2);
        BlockPos origin = front.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        template.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_ALL);
        TrainStructures.randomizeFrequencies(level, template, settings, origin);
        source.sendSuccess(() -> Component.literal("boiler placed in front of you").withStyle(ChatFormatting.GREEN),
                false);
        return 1;
    }

    private static int giveBoilerCart(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("only a player can be handed a cart"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        CompoundTag structure = TrainStructures.boilerCart(level);
        if (structure == null) {
            source.sendFailure(Component.literal("the boiler cart structure is missing from the mod jar"));
            return 0;
        }
        List<CompoundTag> carts = List.of(structure);
        ItemStack relocator = RelocatorItemData.forStored(UUID.randomUUID(), "Boiler Cart", carts,
                TrainStructures.consistLength(level, carts));
        if (!player.getInventory().add(relocator)) {
            player.drop(relocator, false);
        }
        source.sendSuccess(() -> Component.literal("boiler cart in hand, right click a track to put it down")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int give(CommandSourceStack source, int count) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("only a player can be handed a cart"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        CompoundTag structure = TrainStructures.defaultCart(level);
        if (structure == null) {
            source.sendFailure(Component.literal("the default cart structure is missing from the mod jar"));
            return 0;
        }
        List<CompoundTag> carts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            carts.add(withKnuckleEnds(structure));
        }
        ItemStack relocator = RelocatorItemData.forStored(UUID.randomUUID(), count == 1 ? "Test Cart" : "Test Rake",
                carts, TrainStructures.consistLength(level, carts));
        if (!player.getInventory().add(relocator)) {
            player.drop(relocator, false);
        }
        source.sendSuccess(() -> Component.literal(count + " test cart" + (count == 1 ? "" : "s")
                + " in hand, right click a track to put " + (count == 1 ? "it" : "them") + " down")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
