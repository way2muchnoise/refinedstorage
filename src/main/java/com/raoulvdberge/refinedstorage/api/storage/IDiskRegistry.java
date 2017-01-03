package com.raoulvdberge.refinedstorage.api.storage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.function.Function;

public interface IDiskRegistry {
    String NBT_PROTOCOL = "Protocol";
    String NBT_ITEMS = "Items";
    String NBT_FLUIDS = "Fluids";
    String NBT_STORED = "Stored";

    /**
     * Prepares the {@link net.minecraft.nbt.NBTTagCompound} of the {@link ItemStack} to be a valid drive
     *
     * @param stack the {@link ItemStack} to become a valid disk
     * @param type  the {@link StorageType} to become
     * @return the {@link ItemStack} with prepared {@link net.minecraft.nbt.NBTTagCompound}, don't change the existing tags
     */
    ItemStack prepareNBT(ItemStack stack, StorageType type);

    /**
     * Creates a {@link NBTTagCompound} shareTag from the given disk
     *
     * @param disk the {@link ItemStack} that is a drive
     * @param type the {@link StorageType} that the drive is
     * @return the {@link NBTTagCompound} shareTag for the disk
     */
    NBTTagCompound getNBTShareTag(ItemStack disk, StorageType type);

    /**
     * Get the currently stored amount
     *
     * @param disk the disk {@link ItemStack}
     * @return the stored amount
     */
    default int getStored(ItemStack disk) {
        return disk.getTagCompound().getInteger(NBT_STORED);
    }

    /**
     * Register your disk drive
     *
     * @param item            the disk {@link Item}
     * @param stackToCapacity a {@link Function} to calculate the capacity
     * @return true if added, false if the {@link Item} was already registered
     */
    boolean registerDisk(Item item, Function<ItemStack, Integer> stackToCapacity);

    /**
     * Get the capacity of a given disk
     *
     * @param disk the {@link ItemStack}
     * @return the capacity of the disk, a capacity of -1 identifies as infinite
     */
    int getDiskCapacity(ItemStack disk);
}
