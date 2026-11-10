package com.lycoris.loconautics.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

public class CarriageHandle {

    private final LoconauticsTrainPeripheral train;
    private final int index;

    CarriageHandle(LoconauticsTrainPeripheral train, int index) {
        this.train = train;
        this.index = index;
    }

    @LuaFunction
    public final int getIndex() {
        return index;
    }

    @LuaFunction(mainThread = true)
    public final double getLength() throws LuaException {
        return train.getCarriageLength(index);
    }

    @LuaFunction(mainThread = true)
    public final double getWidth() throws LuaException {
        return train.getCarriageWidth(index);
    }

    @LuaFunction(mainThread = true)
    public final double getHeight() throws LuaException {
        return train.getCarriageHeight(index);
    }

    @LuaFunction(mainThread = true)
    public final int getBlockCount() throws LuaException {
        return train.getCarriageBlockCount(index);
    }

    @LuaFunction(mainThread = true)
    public final int getItemCount(IArguments args) throws LuaException {
        return train.carriageItemCount(index, args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Integer> getItems() throws LuaException {
        return train.getCarriageItems(index);
    }

    @LuaFunction(mainThread = true)
    public final int getItemCapacity() throws LuaException {
        return train.getCarriageItemCapacity(index);
    }

    @LuaFunction(mainThread = true)
    public final int getFluidAmount(IArguments args) throws LuaException {
        return train.carriageFluidAmount(index, args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Map<String, Integer>> getFluids() throws LuaException {
        return train.getCarriageFluids(index);
    }

    @LuaFunction(mainThread = true)
    public final int getFluidCapacity() throws LuaException {
        return train.getCarriageFluidCapacity(index);
    }

    @LuaFunction(mainThread = true)
    public final int getPassengerCount() throws LuaException {
        return train.getCarriagePassengerCount(index);
    }

    @LuaFunction(mainThread = true)
    public final boolean hasPassengers() throws LuaException {
        return train.getCarriageHasPassengers(index);
    }
}
