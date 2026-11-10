package com.lycoris.loconautics.content.headstock;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatMaterialHolder;
import com.lycoris.loconautics.common.copycat.CopycatMaterialStore;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

public class CopycatHeadstockBlockEntity extends SmartBlockEntity implements CopycatMaterialHolder {

    private final CopycatMaterialStore copycat = new CopycatMaterialStore(this);

    public CopycatHeadstockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public CopycatMaterialStore copycat() {
        return copycat;
    }

    @Override
    public ModelData getModelData() {
        return CopycatBlockLogic.modelData(copycat);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        copycat.write(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        copycat.read(tag, registries, clientPacket);
    }
}
