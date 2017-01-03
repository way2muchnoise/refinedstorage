package com.raoulvdberge.refinedstorage.apiimpl.storage.item;

import com.raoulvdberge.refinedstorage.api.storage.IDiskRegistry;
import com.raoulvdberge.refinedstorage.api.storage.item.IItemStorage;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A implementation of {@link IItemStorage} that stores storage items in NBT.
 */
public abstract class ItemStorageNBT implements IItemStorage {
    /**
     * The current save protocol that is used. It's set to every {@link ItemStorageNBT} to allow for
     * safe backwards compatibility breaks.
     */
    private static final int PROTOCOL = 1;

    private static final String NBT_ITEM_TYPE = "Type";
    private static final String NBT_ITEM_QUANTITY = "Quantity";
    private static final String NBT_ITEM_DAMAGE = "Damage";
    private static final String NBT_ITEM_NBT = "NBT";
    private static final String NBT_ITEM_CAPS = "Caps";

    private NBTTagCompound tag;
    private int capacity;
    private TileEntity tile;

    private List<ItemStack> stacks = new ArrayList<>();

    /**
     * @param tag      The NBT tag we are reading from and writing the amount stored to, has to be initialized with {@link ItemStorageNBT#createNBT()} if it doesn't exist yet
     * @param capacity The capacity of this storage, -1 for infinite capacity
     * @param tile     A {@link TileEntity} that the NBT storage is in, will be marked dirty when the storage changes
     */
    public ItemStorageNBT(NBTTagCompound tag, int capacity, @Nullable TileEntity tile) {
        this.tag = tag;
        this.capacity = capacity;
        this.tile = tile;

        readFromNBT();
    }

    private void readFromNBT() {
        NBTTagList list = (NBTTagList) tag.getTag(IDiskRegistry.NBT_ITEMS);

        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound tag = list.getCompoundTagAt(i);

            ItemStack stack = new ItemStack(
                Item.getItemById(tag.getInteger(NBT_ITEM_TYPE)),
                tag.getInteger(NBT_ITEM_QUANTITY),
                tag.getInteger(NBT_ITEM_DAMAGE),
                tag.hasKey(NBT_ITEM_CAPS) ? tag.getCompoundTag(NBT_ITEM_CAPS) : null
            );

            stack.setTagCompound(tag.hasKey(NBT_ITEM_NBT) ? tag.getCompoundTag(NBT_ITEM_NBT) : null);

            if (stack.getItem() != null) {
                stacks.add(stack);
            }
        }
    }

    // ItemHandlerHelper#copyStackWithSize is not null-safe!
    private ItemStack safeCopy(ItemStack stack, int size) {
        ItemStack newStack = stack.copy();
        newStack.stackSize = size;
        return newStack;
    }

    /**
     * Writes the items to the NBT tag.
     */
    public void writeToNBT() {
        NBTTagList list = new NBTTagList();

        // Dummy value for extracting ForgeCaps
        NBTTagCompound dummy = new NBTTagCompound();

        for (ItemStack stack : stacks) {
            NBTTagCompound itemTag = new NBTTagCompound();

            itemTag.setInteger(NBT_ITEM_TYPE, Item.getIdFromItem(stack.getItem()));
            itemTag.setInteger(NBT_ITEM_QUANTITY, stack.stackSize);
            itemTag.setInteger(NBT_ITEM_DAMAGE, stack.getItemDamage());

            if (stack.hasTagCompound()) {
                itemTag.setTag(NBT_ITEM_NBT, stack.getTagCompound());
            }

            stack.writeToNBT(dummy);

            if (dummy.hasKey("ForgeCaps")) {
                itemTag.setTag(NBT_ITEM_CAPS, dummy.getTag("ForgeCaps"));
            }

            dummy.removeTag("ForgeCaps");

            list.appendTag(itemTag);
        }

        tag.setTag(IDiskRegistry.NBT_ITEMS, list);
        tag.setInteger(IDiskRegistry.NBT_PROTOCOL, PROTOCOL);
    }

    @Override
    public List<ItemStack> getStacks() {
        return stacks;
    }

    @Override
    public synchronized ItemStack insertItem(ItemStack stack, int size, boolean simulate) {
        for (ItemStack otherStack : stacks) {
            if (API.instance().getComparer().isEqualNoQuantity(otherStack, stack)) {
                if (getCapacity() != -1 && getStored() + size > getCapacity()) {
                    int remainingSpace = getCapacity() - getStored();

                    if (remainingSpace <= 0) {
                        return ItemHandlerHelper.copyStackWithSize(stack, size);
                    }

                    if (!simulate) {
                        tag.setInteger(IDiskRegistry.NBT_STORED, getStored() + remainingSpace);

                        otherStack.stackSize += remainingSpace;

                        onStorageChanged();
                    }

                    return ItemHandlerHelper.copyStackWithSize(otherStack, size - remainingSpace);
                } else {
                    if (!simulate) {
                        tag.setInteger(IDiskRegistry.NBT_STORED, getStored() + size);

                        otherStack.stackSize += size;

                        onStorageChanged();
                    }

                    return null;
                }
            }
        }

        if (getCapacity() != -1 && getStored() + size > getCapacity()) {
            int remainingSpace = getCapacity() - getStored();

            if (remainingSpace <= 0) {
                return ItemHandlerHelper.copyStackWithSize(stack, size);
            }

            if (!simulate) {
                tag.setInteger(IDiskRegistry.NBT_STORED, getStored() + remainingSpace);

                stacks.add(safeCopy(stack, remainingSpace));

                onStorageChanged();
            }

            return ItemHandlerHelper.copyStackWithSize(stack, size - remainingSpace);
        } else {
            if (!simulate) {
                tag.setInteger(IDiskRegistry.NBT_STORED, getStored() + size);

                stacks.add(safeCopy(stack, size));

                onStorageChanged();
            }

            return null;
        }
    }

    @Override
    public synchronized ItemStack extractItem(ItemStack stack, int size, int flags, boolean simulate) {
        for (ItemStack otherStack : stacks) {
            if (API.instance().getComparer().isEqual(otherStack, stack, flags)) {
                if (size > otherStack.stackSize) {
                    size = otherStack.stackSize;
                }

                if (!simulate) {
                    if (otherStack.stackSize - size == 0) {
                        stacks.remove(otherStack);
                    } else {
                        otherStack.stackSize -= size;
                    }

                    tag.setInteger(IDiskRegistry.NBT_STORED, getStored() - size);

                    onStorageChanged();
                }

                return ItemHandlerHelper.copyStackWithSize(otherStack, size);
            }
        }

        return null;
    }

    public void onStorageChanged() {
        if (tile != null) {
            tile.markDirty();
        }
    }

    @Override
    public int getStored() {
        return getStoredFromNBT(tag);
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isFull() {
        return getStored() == getCapacity();
    }

    public NBTTagCompound getTag() {
        return tag;
    }

    public static int getStoredFromNBT(NBTTagCompound tag) {
        return tag.getInteger(IDiskRegistry.NBT_STORED);
    }

    public static NBTTagCompound getNBTShareTag(NBTTagCompound tag) {
        NBTTagCompound otherTag = new NBTTagCompound();

        otherTag.setInteger(IDiskRegistry.NBT_STORED, getStoredFromNBT(tag));
        otherTag.setTag(IDiskRegistry.NBT_ITEMS, new NBTTagList());
        otherTag.setInteger(IDiskRegistry.NBT_PROTOCOL, PROTOCOL);

        return otherTag;
    }

    /*
     * @return A NBT tag initialized with the fields that {@link NBTStorage} uses
     */
    public static NBTTagCompound createNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setTag(IDiskRegistry.NBT_ITEMS, new NBTTagList());
        tag.setInteger(IDiskRegistry.NBT_STORED, 0);
        tag.setInteger(IDiskRegistry.NBT_PROTOCOL, PROTOCOL);

        return tag;
    }

    public static boolean isValid(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(IDiskRegistry.NBT_ITEMS) && stack.getTagCompound().hasKey(IDiskRegistry.NBT_STORED);
    }

    /**
     * @param stack The {@link ItemStack} to populate with the NBT tags from {@link ItemStorageNBT#createNBT()}
     * @return The provided {@link ItemStack} with NBT tags from {@link ItemStorageNBT#createNBT()}
     */
    public static ItemStack createStackWithNBT(ItemStack stack) {
        stack.setTagCompound(createNBT());

        return stack;
    }
}