package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class TrainStructures {

    public static final ResourceLocation DEFAULT_CART = LoconauticsConstants.id("default_cart");

    private TrainStructures() {
    }

    public static final ResourceLocation BOILER_CART = LoconauticsConstants.id("boiler_cart");

    @Nullable
    public static CompoundTag defaultCart(ServerLevel level) {
        return bundled(level, DEFAULT_CART);
    }

    @Nullable
    public static CompoundTag boilerCart(ServerLevel level) {
        return bundled(level, BOILER_CART);
    }

    @Nullable
    private static CompoundTag bundled(ServerLevel level, ResourceLocation id) {
        StructureTemplate template = level.getServer().getStructureManager().get(id).orElse(null);
        if (template == null) {
            LoconauticsConstants.LOGGER.warn("[sabletrain] no cart structure at {}", id);
            return null;
        }
        return template.save(new CompoundTag());
    }

    @Nullable
    public static CompoundTag capture(ServerLevel level, ServerSubLevel sub) {
        var box = sub.getPlot().getBoundingBox();
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
        Vec3i size = new Vec3i(box.maxX() - box.minX() + 1, box.maxY() - box.minY() + 1,
                box.maxZ() - box.minZ() + 1);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, true, null);
        return template.save(new CompoundTag());
    }

    public static void removeBody(ServerLevel level, SableTrain train) {
        UUID subId = train.car().subLevelId();
        GhostWheelBodies.releaseAll(train);
        SableTrainRegistry.remove(train.id());
        RerailApproach.forget(train.id());
        SableTrainPersistence.unpersist(level.getServer(), train.id());
        SableTrainSyncPacket.broadcastRemoval(subId);
        ServerSubLevel sub = SubLevelData.resolve(level, subId);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (sub == null || container == null) {
            return;
        }
        wipePlot(level, sub);
        if (container.getSubLevel(subId) == sub) {
            container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        }
    }

    private static void wipePlot(ServerLevel level, ServerSubLevel sub) {
        var box = sub.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    public record Placement(boolean success, String failure, UUID trainId) {

        static Placement failed(String reason) {
            return new Placement(false, reason, null);
        }
    }

    // a curve has no block under it, so the cart goes where the rail point is
    public static Placement place(ServerLevel level, CompoundTag structureTag, TrackGraphLocation loc, Vec3 railPoint,
                                  Vec3 upNormal, Vec3 look, UUID owner, @Nullable String name) {
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), structureTag);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotationFor(look, cartFront(template)))
                .setIgnoreEntities(true);
        BlockPos anchor = bogeyAnchor(template, settings);
        if (anchor == null) {
            return Placement.failed("the stored cart has no bogey in it");
        }
        BlockPos seat = BlockPos.containing(railPoint);
        SableTrainSpawner.TrackHit rail = SableTrainSpawner.findRailBelow(level, seat, look);
        if (rail != null && seat.getY() <= rail.railY()) {
            seat = seat.above(rail.railY() + 1 - seat.getY());
        }
        BlockPos origin = seat.subtract(anchor);
        if (!roomToPlace(level, template, settings, origin)) {
            return Placement.failed("there is something in the way, clear the space above the track");
        }
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS)) {
            return Placement.failed("the cart could not be placed here");
        }
        randomizeFrequencies(level, template, settings, origin);

        SableTrain train = SableTrainSpawner.assembleAt(level, seat, look, owner, name);
        if (train == null) {
            return Placement.failed("the cart was placed but would not assemble, check it is on the rail");
        }
        // the assembled reference forward follows the rail edge, not the cart, so the nose picks which way the cart lies
        Vec3 carLook = SableTrainSpawner.facesAgainst(level, train, SableTrainDriver.axleNose(train))
                ? look.reverse() : look;
        SableTrainSpawner.seatCarAt(level, train, loc, upNormal, carLook, false, true);
        return new Placement(true, null, train.id());
    }

    public static final String TRAIN_IN_THE_WAY = "another train is on the track there";

    private record RailHead(TrackGraphLocation location, Vec3 upNormal) {
    }

    @Nullable
    private static RailHead railHeadAt(ServerLevel level, RailPick pick, Vec3 look) {
        BlockState trackState = level.getBlockState(pick.pos());
        if (!(trackState.getBlock() instanceof ITrackBlock track)) {
            return null;
        }
        TrackGraphLocation head = pick.graphLocation(level, track, trackState, look);
        if (head == null || head.graph == null) {
            return null;
        }
        return new RailHead(head, track.getUpNormal(level, pick.pos(), trackState));
    }

    // per cart, its center distance from the head and then its half length
    private static List<double[]> cartSpans(ServerLevel level, List<CompoundTag> structures) {
        List<double[]> spans = new ArrayList<>(structures.size());
        double cursor = 0.0;
        double prevHalf = 0.0;
        for (CompoundTag structure : structures) {
            double half = length(level, structure) / 2.0;
            cursor += spans.isEmpty() ? half : prevHalf + TrainLength.COUPLING_GAP + half;
            prevHalf = half;
            spans.add(new double[]{cursor, half});
        }
        return spans;
    }

    private static boolean anyCartBlocked(ServerLevel level, List<double[]> spans, RailHead head, Vec3 look) {
        for (double[] cart : spans) {
            PlacementClearance.Span span = PlacementClearance.between(
                    railPoint(SableTrainSpawner.locationBack(head.location(), head.upNormal(), look,
                            cart[0] - cart[1], true)),
                    railPoint(SableTrainSpawner.locationBack(head.location(), head.upNormal(), look,
                            cart[0] + cart[1], true)));
            if (span != null && PlacementClearance.blocker(level, span, Set.of()) != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean blockedByTrain(ServerLevel level, List<CompoundTag> structures, RailPick pick, Vec3 look) {
        RailHead head = railHeadAt(level, pick, look);
        return head != null && anyCartBlocked(level, cartSpans(level, structures), head, look);
    }

    public static Placement placeAll(ServerLevel level, List<CompoundTag> structures, RailPick pick, Vec3 look,
                                     UUID owner, @Nullable String name) {
        if (!(level.getBlockState(pick.pos()).getBlock() instanceof ITrackBlock)) {
            return Placement.failed("that is not a track block");
        }
        RailHead head = railHeadAt(level, pick, look);
        if (head == null) {
            return Placement.failed("no track graph here, is this rail connected to anything");
        }
        List<double[]> spans = cartSpans(level, structures);
        if (anyCartBlocked(level, spans, head, look)) {
            return Placement.failed(TRAIN_IN_THE_WAY);
        }

        Placement first = null;
        for (int i = 0; i < structures.size(); i++) {
            TrackGraphLocation back = SableTrainSpawner.locationBack(head.location(), head.upNormal(), look,
                    spans.get(i)[0], true);
            Vec3 point = back == null ? null : railPoint(back);
            if (point == null) {
                return first != null ? first : Placement.failed("not enough track to place the whole train");
            }
            Placement placed = place(level, structures.get(i), back, point, head.upNormal(), look, owner, name);
            if (!placed.success()) {
                return first != null ? first : placed;
            }
            if (first == null) {
                first = placed;
            }
        }
        return first == null ? Placement.failed("nothing stored in that relocator") : first;
    }

    @Nullable
    private static Vec3 railPoint(@Nullable TrackGraphLocation loc) {
        if (loc == null) {
            return null;
        }
        var point = com.lycoris.loconautics.trainphysics.rail.RailFollower.pointAt(loc);
        return point == null ? null : point.getPosition(loc.graph);
    }

    public static double consistLength(ServerLevel level, List<CompoundTag> structures) {
        List<Double> lengths = new ArrayList<>();
        for (CompoundTag structure : structures) {
            lengths.add(length(level, structure));
        }
        return TrainLength.rake(lengths);
    }

    public static double length(ServerLevel level, CompoundTag structureTag) {
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), structureTag);
        Vec3i size = template.getSize();
        List<StructureTemplate.StructureBlockInfo> bogeys = new ArrayList<>();
        bogeys.addAll(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), AllBlocks.SMALL_BOGEY.get()));
        bogeys.addAll(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), AllBlocks.LARGE_BOGEY.get()));
        if (bogeys.isEmpty()) {
            return Math.max(size.getX(), size.getZ());
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructureTemplate.StructureBlockInfo info : bogeys) {
            minX = Math.min(minX, info.pos().getX());
            maxX = Math.max(maxX, info.pos().getX());
            minZ = Math.min(minZ, info.pos().getZ());
            maxZ = Math.max(maxZ, info.pos().getZ());
        }
        boolean alongX = maxX - minX != maxZ - minZ ? maxX - minX > maxZ - minZ : size.getX() >= size.getZ();
        double mid = alongX ? (minX + maxX) / 2.0 + 0.5 : (minZ + maxZ) / 2.0 + 0.5;
        int extent = alongX ? size.getX() : size.getZ();
        double spacing = Math.max(1.0, alongX ? maxX - minX : maxZ - minZ);
        return Math.max(spacing, 2.0 * Math.max(mid, extent - mid));
    }

    private static Rotation rotationFor(Vec3 look, Direction front) {
        Direction target = Direction.getNearest(look.x, 0.0, look.z);
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(front) == target) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    // the bearing axle is the only block that knows which end of a cart is the front, everything else is symmetric
    private static Direction cartFront(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> axles = template.filterBlocks(BlockPos.ZERO,
                new StructurePlaceSettings(), LoconauticsRegistries.BEARING_AXLE.get());
        for (StructureTemplate.StructureBlockInfo info : axles) {
            Direction front = BearingAxleBlock.front(info.state());
            if (front.getAxis().isHorizontal()) {
                return front;
            }
        }
        return Direction.SOUTH;
    }

    @Nullable
    private static BlockPos bogeyAnchor(StructureTemplate template, StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> found = new ArrayList<>();
        found.addAll(template.filterBlocks(BlockPos.ZERO, settings, AllBlocks.SMALL_BOGEY.get()));
        found.addAll(template.filterBlocks(BlockPos.ZERO, settings, AllBlocks.LARGE_BOGEY.get()));
        BlockPos best = null;
        for (StructureTemplate.StructureBlockInfo info : found) {
            if (best == null || info.pos().getY() < best.getY()) {
                best = info.pos();
            }
        }
        return best;
    }

    public static void randomizeFrequencies(ServerLevel level, StructureTemplate template,
                                             StructurePlaceSettings settings, BlockPos origin) {
        ItemStack[] left = randomPair(level);
        ItemStack[] right = randomPair(level);
        ItemStack[] speed = randomPair(level);
        ItemStack[] direction = randomPair(level);

        forEachPlacedCell(template, settings, origin, pos -> {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BearingAxleBlockEntity axle) {
                axle.setLeftFrequency(left[0], left[1]);
                axle.setRightFrequency(right[0], right[1]);
            } else if (be instanceof AnalogControllerBlockEntity controller) {
                controller.setFrequency(speed[0], speed[1]);
                controller.setBackwardFrequency(direction[0], direction[1]);
                controller.setLeftFrequency(left[0], left[1]);
                controller.setRightFrequency(right[0], right[1]);
            } else if (be instanceof TransmissionBlockEntity transmission) {
                transmission.setSpeedFrequency(speed[0], speed[1]);
                transmission.setDirectionFrequency(direction[0], direction[1]);
            }
            return true;
        });
    }

    private static boolean forEachPlacedCell(StructureTemplate template, StructurePlaceSettings settings,
                                             BlockPos origin, Predicate<BlockPos> visitor) {
        Vec3i size = template.getSize();
        BlockPos far = StructureTemplate.calculateRelativePosition(settings,
                new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.min(0, far.getX()); x <= Math.max(0, far.getX()); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = Math.min(0, far.getZ()); z <= Math.max(0, far.getZ()); z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!visitor.test(pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static ItemStack[] randomPair(ServerLevel level) {
        return new ItemStack[]{randomFrequency(level), randomFrequency(level)};
    }

    private static ItemStack randomFrequency(ServerLevel level) {
        return BuiltInRegistries.ITEM.getRandom(level.getRandom())
                .map(holder -> new ItemStack(holder))
                .orElse(ItemStack.EMPTY);
    }

    private static boolean roomToPlace(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings,
                                       BlockPos origin) {
        return forEachPlacedCell(template, settings, origin, pos -> level.getBlockState(pos).canBeReplaced());
    }
}
