package com.raoulvdberge.refinedstorage.apiimpl.storage;

import com.raoulvdberge.refinedstorage.api.storage.IDiskRegistry;
import com.raoulvdberge.refinedstorage.api.storage.StorageType;
import com.raoulvdberge.refinedstorage.apiimpl.storage.fluid.FluidStorageNBT;
import com.raoulvdberge.refinedstorage.apiimpl.storage.item.ItemStorageNBT;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class DiskRegistry implements IDiskRegistry {

    private Map<Item, Function<ItemStack, Integer>> stackToCapacityMap = new HashMap<>();

    @Override
    public ItemStack prepareNBT(ItemStack stack, StorageType type) {
        switch (type) {
            case ITEMS:
                stack = ItemStorageNBT.createStackWithNBT(stack);
                break;
            case FLUIDS:
                stack = FluidStorageNBT.createStackWithNBT(stack);
                break;
        }
        return stack;
    }

    @Override
    public NBTTagCompound getNBTShareTag(ItemStack disk, StorageType type) {
        switch (type) {
            case ITEMS:
                return ItemStorageNBT.getNBTShareTag(disk.getTagCompound());
            case FLUIDS:
                return FluidStorageNBT.getNBTShareTag(disk.getTagCompound());
        }
        return new NBTTagCompound();
    }

    @Override
    public boolean registerDisk(Item item, Function<ItemStack, Integer> stackToCapacity) {
        if (!stackToCapacityMap.containsKey(item)) {
            stackToCapacityMap.put(item, stackToCapacity);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getDiskCapacity(ItemStack disk) {
        return stackToCapacityMap.containsKey(disk.getItem()) ? stackToCapacityMap.get(disk.getItem()).apply(disk) : 0;
    }
}
