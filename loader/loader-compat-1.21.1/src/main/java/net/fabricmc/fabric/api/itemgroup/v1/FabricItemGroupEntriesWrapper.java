package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FabricItemGroupEntriesWrapper extends FabricItemGroupEntries {
    public static class Entry {
        public final ItemStack stack;
        public final CreativeModeTab.TabVisibility visibility;

        public Entry(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
            this.stack = stack;
            this.visibility = visibility;
        }
    }

    private final CreativeModeTab.Output output;
    private final List<Entry> addedEntries = new ArrayList<>();

    public FabricItemGroupEntriesWrapper(CreativeModeTab.Output output) {
        this.output = output;
    }

    @Override
    public void accept(ItemLike item) {
        add(item);
    }

    @Override
    public void accept(ItemStack stack) {
        add(stack);
    }

    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        add(stack, visibility);
    }

    @Override
    public void accept(ItemLike item, CreativeModeTab.TabVisibility visibility) {
        add(item, visibility);
    }

    @Override
    public void add(ItemStack stack) {
        add(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    @Override
    public void add(ItemLike item) {
        add(item, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    @Override
    public void add(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (stack != null) {
            addedEntries.add(new Entry(stack, visibility));
        }
    }

    @Override
    public void add(ItemLike item, CreativeModeTab.TabVisibility visibility) {
        if (item != null) {
            addedEntries.add(new Entry(new ItemStack(item), visibility));
        }
    }

    @Override
    public void addBefore(ItemLike helper, ItemStack... stacks) {
        List<Entry> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(new Entry(s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
        insertBefore(helper, list);
    }

    @Override
    public void addBefore(ItemLike helper, Collection<ItemStack> stacks) {
        List<Entry> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(new Entry(s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
        insertBefore(helper, list);
    }

    @Override
    public void addBefore(ItemLike helper, ItemLike... items) {
        List<Entry> list = new ArrayList<>();
        for (ItemLike item : items) {
            if (item != null) list.add(new Entry(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
        insertBefore(helper, list);
    }

    @Override
    public void addBefore(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (stack != null) {
            List<Entry> list = new ArrayList<>();
            list.add(new Entry(stack, visibility));
            insertBefore(helper, list);
        }
    }

    @Override
    public void addBefore(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {
        List<Entry> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(new Entry(s, visibility));
        }
        insertBefore(helper, list);
    }

    @Override
    public void addBefore(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility) {
        if (item != null) {
            List<Entry> list = new ArrayList<>();
            list.add(new Entry(new ItemStack(item), visibility));
            insertBefore(helper, list);
        }
    }

    @Override
    public void addAfter(ItemLike helper, ItemStack... stacks) {
        List<Entry> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(new Entry(s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
        insertAfter(helper, list);
    }

    @Override
    public void addAfter(ItemLike helper, Collection<ItemStack> stacks) {
        List<Entry> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(new Entry(s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
        insertAfter(helper, list);
    }

    @Override
    public void addAfter(ItemLike helper, ItemLike... items) {
        List<Entry> list = new ArrayList<>();
        for (ItemLike item : items) {
            if (item != null) list.add(new Entry(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
        insertAfter(helper, list);
    }

    @Override
    public void addAfter(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (stack != null) {
            List<Entry> list = new ArrayList<>();
            list.add(new Entry(stack, visibility));
            insertAfter(helper, list);
        }
    }

    @Override
    public void addAfter(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {
        List<Entry> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(new Entry(s, visibility));
        }
        insertAfter(helper, list);
    }

    @Override
    public void addAfter(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility) {
        if (item != null) {
            List<Entry> list = new ArrayList<>();
            list.add(new Entry(new ItemStack(item), visibility));
            insertAfter(helper, list);
        }
    }

    private int findIndex(ItemLike helper) {
        if (helper == null) return -1;
        net.minecraft.world.item.Item targetItem = helper.asItem();
        if (targetItem == null) return -1;
        for (int i = 0; i < addedEntries.size(); i++) {
            ItemStack stack = addedEntries.get(i).stack;
            if (stack != null && stack.asItem() == targetItem) {
                return i;
            }
        }
        return -1;
    }

    private int findLastIndex(ItemLike helper) {
        if (helper == null) return -1;
        net.minecraft.world.item.Item targetItem = helper.asItem();
        if (targetItem == null) return -1;
        for (int i = addedEntries.size() - 1; i >= 0; i--) {
            ItemStack stack = addedEntries.get(i).stack;
            if (stack != null && stack.asItem() == targetItem) {
                return i;
            }
        }
        return -1;
    }

    private void insertBefore(ItemLike helper, Collection<Entry> entries) {
        int index = findIndex(helper);
        if (index != -1) {
            addedEntries.addAll(index, entries);
        } else {
            addedEntries.addAll(entries);
        }
    }

    private void insertAfter(ItemLike helper, Collection<Entry> entries) {
        int index = findLastIndex(helper);
        if (index != -1) {
            addedEntries.addAll(index + 1, entries);
        } else {
            addedEntries.addAll(entries);
        }
    }

    public void commit() {
        for (Entry entry : addedEntries) {
            output.accept(entry.stack, entry.visibility);
        }
        addedEntries.clear();
    }

    public List<ItemStack> getAddedStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (Entry e : addedEntries) {
            list.add(e.stack);
        }
        return list;
    }
}
