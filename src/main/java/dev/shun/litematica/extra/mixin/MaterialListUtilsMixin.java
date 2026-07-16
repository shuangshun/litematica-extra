/*
 * This file is part of the Litematica Extra project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  shuangshun and contributors
 *
 * Litematica Extra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Litematica Extra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Litematica Extra.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.shun.litematica.extra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.Identifier;
import net.minecraft.block.BlockState;
import net.minecraft.registry.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
//#if MC >= 1.20.5
//$$ import net.minecraft.component.type.*;
//$$ import net.minecraft.component.DataComponentTypes;
//#endif
import fi.dy.masa.malilib.util.ItemType;
import fi.dy.masa.litematica.materials.*;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import static fi.dy.masa.malilib.util.Constants.NBT.*;

import java.util.*;

@Mixin(MaterialListUtils.class)
public abstract class MaterialListUtilsMixin {

    @Unique
    private static final IdentityHashMap<EntityType<?>, Item> ENTITY_TO_ITEM = new IdentityHashMap<>();
    @Unique
    private static final Map<EntityType<?>, Item> SPECIAL_EGG_OVERRIDES = new IdentityHashMap<>();

    static {
        // MINECART
        ENTITY_TO_ITEM.put(EntityType.MINECART, Items.MINECART);
        ENTITY_TO_ITEM.put(EntityType.CHEST_MINECART, Items.CHEST_MINECART);
        ENTITY_TO_ITEM.put(EntityType.FURNACE_MINECART, Items.FURNACE_MINECART);
        ENTITY_TO_ITEM.put(EntityType.HOPPER_MINECART, Items.HOPPER_MINECART);
        ENTITY_TO_ITEM.put(EntityType.TNT_MINECART, Items.TNT_MINECART);
        ENTITY_TO_ITEM.put(EntityType.COMMAND_BLOCK_MINECART, Items.COMMAND_BLOCK_MINECART);

        // OTHER
        ENTITY_TO_ITEM.put(EntityType.ARMOR_STAND, Items.ARMOR_STAND);
        ENTITY_TO_ITEM.put(EntityType.PAINTING, Items.PAINTING);
        ENTITY_TO_ITEM.put(EntityType.ITEM_FRAME, Items.ITEM_FRAME);
        ENTITY_TO_ITEM.put(EntityType.GLOW_ITEM_FRAME, Items.GLOW_ITEM_FRAME);
        ENTITY_TO_ITEM.put(EntityType.END_CRYSTAL, Items.END_CRYSTAL);

        SPECIAL_EGG_OVERRIDES.put(EntityType.GIANT, Items.ZOMBIE_SPAWN_EGG);
        SPECIAL_EGG_OVERRIDES.put(EntityType.ILLUSIONER, Items.EVOKER_SPAWN_EGG);
    }

    @Unique
    private static final int MAX_RECURSIVE_DEPTH = 128;

    //#if MC >= 1.20.5
    //$$ @Unique
    //$$ private static final RegistryWrapper.WrapperLookup WRAPPER_LOOKUP = BuiltinRegistries.createWrapperLookup();
    //#endif

    @Shadow
    private static void convertStatesToStacks(
            Object2IntOpenHashMap<BlockState> blockStatesIn,
            Object2IntOpenHashMap<ItemType> itemTypesOut,
            MaterialCache cache
    ) {}

    @Shadow
    public static Object2IntOpenHashMap<ItemType> getInventoryItemCounts(Inventory inv) {
        return null;
    }

    /**
     * @author shuangshun
     * @reason Extend schematic material statistics
     */
    @Overwrite
    public static List<MaterialListEntry> createMaterialListFor(LitematicaSchematic schematic, Collection<String> subRegions) {
        Object2IntOpenHashMap<BlockState> countsTotal = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<ItemType> itemTypesTotal = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<ItemType> itemTypesMissing = new Object2IntOpenHashMap<>();

        for (String regionName : subRegions) {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            if (container == null) continue;

            Vec3i size = container.getSize();
            final int sx = size.getX(), sy = size.getY(), sz = size.getZ();

            Map<BlockPos, NbtCompound> tileEntities = schematic.getBlockEntityMapForRegion(regionName);
            if (tileEntities == null) tileEntities = Collections.emptyMap();

            // Blocks
            for (int y = 0; y < sy; ++y) {
                for (int z = 0; z < sz; ++z) {
                    for (int x = 0; x < sx; ++x) {
                        BlockState state = container.get(x, y, z);
                        countsTotal.addTo(state, 1);

                        // BlockEntities
                        BlockPos pos = new BlockPos(x, y, z);
                        NbtCompound teTag = tileEntities.get(pos);
                        if (teTag != null && teTag.contains("Items", TAG_LIST)) {
                            NbtList itemsList = teTag.getList("Items", TAG_COMPOUND);
                            for (int i = 0; i < itemsList.size(); ++i) {
                                NbtCompound itemTag = itemsList.getCompound(i);
                                ItemStack stack = fromNbt(itemTag);
                                if (!stack.isEmpty()) {
                                    processItemStackRecursively(stack, 0, itemTypesTotal, itemTypesMissing);
                                }
                            }
                        }
                    }
                }
            }
        }

        MaterialCache cache = MaterialCache.getInstance();

        //  Block states -> items
        Object2IntOpenHashMap<ItemType> blockItems = new Object2IntOpenHashMap<>();
        convertStatesToStacks(countsTotal, blockItems, cache);
        for (ItemType type : blockItems.keySet()) {
            int count = blockItems.getInt(type);
            itemTypesTotal.addTo(type, count);
            itemTypesMissing.addTo(type, count);
        }

        // Entities
        for (String regionName : subRegions) {
            List<LitematicaSchematic.EntityInfo> entityList = schematic.getEntityListForRegion(regionName);
            if (entityList != null) {
                for (LitematicaSchematic.EntityInfo info : entityList) {
                    addEntityItemsToMaps(info.nbt, itemTypesTotal, itemTypesMissing, 0);
                }
            }
        }

        // Player Inventory
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        Object2IntOpenHashMap<ItemType> playerInvItems = null;
        if (player != null) {
            playerInvItems = getInventoryItemCounts(player.getInventory());
        }

        List<MaterialListEntry> list = new ArrayList<>();
        for (ItemType type : itemTypesTotal.keySet()) {
            if (playerInvItems != null) {
                list.add(new MaterialListEntry(
                        type.getStack().copy(),
                        itemTypesTotal.getInt(type),
                        itemTypesMissing.getInt(type),
                        0,
                        playerInvItems.getInt(type)
                ));
            }
        }
        return list;
    }

    @Unique
    private static NbtCompound getSubNbt(ItemStack stack, String key) {
        //#if MC >= 1.20.5
        //$$ if ("BlockEntityTag".equals(key)) {
        //$$     NbtComponent component = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        //$$     return component != null ? component.copyNbt() : null;
        //$$ }
        //$$ return null;
        //#else
        return stack.getSubNbt(key);
        //#endif
    }

    @Unique
    private static ItemStack fromNbt(NbtCompound tag) {
        if (tag == null || !tag.contains("id", NbtElement.STRING_TYPE)) {
            return ItemStack.EMPTY;
        }

        //#if MC >= 1.20.5
        //$$ return ItemStack.fromNbt(WRAPPER_LOOKUP, tag).orElse(ItemStack.EMPTY);
        //#else
        return ItemStack.fromNbt(tag);
        //#endif
    }

    @Unique
    private static void setCustomName(ItemStack stack, Text name) {
        //#if MC >= 1.20.5
        //$$ stack.set(DataComponentTypes.CUSTOM_NAME, name);
        //#else
        stack.setCustomName(name);
        //#endif
    }

    @Unique
    private static void processItemStackRecursively(
            ItemStack stack, int depth,
            Object2IntOpenHashMap<ItemType> totalMap,
            Object2IntOpenHashMap<ItemType> missingMap
    ) {
        if (depth > MAX_RECURSIVE_DEPTH) return;

        if (stack.isEmpty()) return;

        // Current Item
        boolean matchNBT = stack.getItem() instanceof SpawnEggItem;
        ItemType type = new ItemType(stack, true, matchNBT);
        int count = stack.getCount();
        totalMap.addTo(type, count);
        missingMap.addTo(type, count);


        if (isContainerItem(stack)) {
            //#if MC >= 1.20.5
            //$$ ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            //$$ if (container != null) {
            //$$     for (ItemStack innerStack : container.iterateNonEmpty()) {
            //$$         if (!innerStack.isEmpty()) {
            //$$             processItemStackRecursively(innerStack, depth + 1, totalMap, missingMap);
            //$$         }
            //$$     }
            //$$ }
            //#else
            NbtCompound blockEntityTag = getSubNbt(stack, "BlockEntityTag");
            if (blockEntityTag != null && blockEntityTag.contains("Items", TAG_LIST)) {
                NbtList itemsList = blockEntityTag.getList("Items", TAG_COMPOUND);
                for (int i = 0; i < itemsList.size(); ++i) {
                    NbtCompound itemTag = itemsList.getCompound(i);
                    ItemStack innerStack = fromNbt(itemTag);
                    if (!innerStack.isEmpty()) {
                        processItemStackRecursively(innerStack, depth + 1, totalMap, missingMap);
                    }
                }
            }
            //#endif
        }
    }

    @Unique
    private static boolean isContainerItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (!(stack.getItem() instanceof BlockItem)) return false;

        //#if MC >= 1.20.5
        //$$ if (stack.get(DataComponentTypes.CONTAINER) != null) return true;
        //#endif

        NbtCompound tag = getSubNbt(stack, "BlockEntityTag");

        return tag != null && tag.contains("Items", TAG_LIST);
    }

    @Unique
    private static void addEntityItemsToMaps(NbtCompound tag,
            Object2IntOpenHashMap<ItemType> totalMap,
            Object2IntOpenHashMap<ItemType> missingMap,
            int depth
    ) {
        if (depth > MAX_RECURSIVE_DEPTH) return;

        String id = tag.getString("id");
        EntityType<?> typeEntity = Registries.ENTITY_TYPE.get(new Identifier(id));

        // Items carried by entities
        ItemStack selfStack = getItemStackForEntityTag(tag);
        if (!selfStack.isEmpty()) {
            processItemStackRecursively(selfStack, 0, totalMap, missingMap);
        }

        // Items in containers
        if (tag.contains("Items", TAG_LIST)) {
            NbtList itemsList = tag.getList("Items", TAG_COMPOUND);
            for (int i = 0; i < itemsList.size(); ++i) {
                NbtCompound itemTag = itemsList.getCompound(i);
                ItemStack stack = fromNbt(itemTag);
                if (!stack.isEmpty()) {
                    processItemStackRecursively(stack, 0, totalMap, missingMap);
                }
            }
        }

        // Include item frame items
        if (typeEntity != EntityType.ITEM) {
            if (tag.contains("Item", TAG_COMPOUND)) {
                NbtCompound itemTag = tag.getCompound("Item");
                ItemStack extraStack = fromNbt(itemTag);
                if (!extraStack.isEmpty()) {
                    processItemStackRecursively(extraStack, 0, totalMap, missingMap);
                }
            }
        }

        if (typeEntity == EntityType.ARMOR_STAND) {
            if (tag.contains("ArmorItems", TAG_LIST)) {
                NbtList armorList = tag.getList("ArmorItems", TAG_COMPOUND);
                for (int i = 0; i < armorList.size(); ++i) {
                    NbtCompound itemTag = armorList.getCompound(i);
                    ItemStack stack = fromNbt(itemTag);
                    if (!stack.isEmpty()) {
                        processItemStackRecursively(stack, 0, totalMap, missingMap);
                    }
                }
            }
            if (tag.contains("HandItems", TAG_LIST)) {
                NbtList handList = tag.getList("HandItems", TAG_COMPOUND);
                for (int i = 0; i < handList.size(); ++i) {
                    NbtCompound itemTag = handList.getCompound(i);
                    ItemStack stack = fromNbt(itemTag);
                    if (!stack.isEmpty()) {
                        processItemStackRecursively(stack, 0, totalMap, missingMap);
                    }
                }
            }
        }

        if (typeEntity != EntityType.LEASH_KNOT) {
            if (tag.contains("Leash", TAG_COMPOUND)) {
                ItemStack leashStack = new ItemStack(Items.LEAD);
                ItemType type = new ItemType(leashStack, true, false);
                totalMap.addTo(type, 1);
                missingMap.addTo(type, 1);
            }
        }

        if (tag.contains("Passengers", TAG_LIST)) {
            NbtList passengersList = tag.getList("Passengers", TAG_COMPOUND);
            for (int i = 0; i < passengersList.size(); ++i) {
                NbtCompound passengerTag = passengersList.getCompound(i);
                addEntityItemsToMaps(passengerTag, totalMap, missingMap, depth + 1);
            }
        }
    }

    @Unique
    private static ItemStack getItemStackForEntityTag(NbtCompound tag) {
        String id = tag.getString("id");
        EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(id));

        if (type == EntityType.ITEM) {
            NbtCompound itemTag = tag.getCompound("Item");
            if (itemTag != null && !itemTag.isEmpty()) {
                ItemStack stack = fromNbt(itemTag);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        Item item = ENTITY_TO_ITEM.get(type);
        if (item != null) return new ItemStack(item);

        if (type == EntityType.BOAT) {
            return getBoatItem(tag, false);
        }
        if (type == EntityType.CHEST_BOAT) {
            return getBoatItem(tag, true);
        }

        // SpecialEgg Mapping
        Item specialEggItem = SPECIAL_EGG_OVERRIDES.get(type);
        if (specialEggItem != null) {
            ItemStack stack = new ItemStack(specialEggItem);
            setCustomName(stack, type.getName());
            return stack;
        }

        SpawnEggItem egg = SpawnEggItem.forEntity(type);
        if (egg != null) {
            ItemStack stack = new ItemStack(egg);
            // Localized name of the entity
            setCustomName(stack, type.getName());
            return stack;
        }

        // If a living entity has no spawn egg
        // Use a chicken spawn egg as a placeholder
        // And set its name to the entity's name
        if (type.isSummonable()) {
            ItemStack stack = new ItemStack(Items.CHICKEN_SPAWN_EGG);
            setCustomName(stack, type.getName());
            return stack;
        }

        return ItemStack.EMPTY;
    }

    @Unique
    private static ItemStack getBoatItem(NbtCompound tag, boolean isChestBoat) {
        String typeStr = tag.contains("Type") ? tag.getString("Type") : "oak";
        if (typeStr.startsWith("minecraft:")) {
            typeStr = typeStr.substring(10);
        }
        typeStr = typeStr.toLowerCase(Locale.ROOT);

        return switch (typeStr) {
            case "spruce" -> isChestBoat ? new ItemStack(Items.SPRUCE_CHEST_BOAT) : new ItemStack(Items.SPRUCE_BOAT);
            case "birch" -> isChestBoat ? new ItemStack(Items.BIRCH_CHEST_BOAT) : new ItemStack(Items.BIRCH_BOAT);
            case "jungle" -> isChestBoat ? new ItemStack(Items.JUNGLE_CHEST_BOAT) : new ItemStack(Items.JUNGLE_BOAT);
            case "acacia" -> isChestBoat ? new ItemStack(Items.ACACIA_CHEST_BOAT) : new ItemStack(Items.ACACIA_BOAT);
            case "dark_oak" -> isChestBoat ? new ItemStack(Items.DARK_OAK_CHEST_BOAT) : new ItemStack(Items.DARK_OAK_BOAT);
            case "mangrove" -> isChestBoat ? new ItemStack(Items.MANGROVE_CHEST_BOAT) : new ItemStack(Items.MANGROVE_BOAT);
            case "bamboo" -> isChestBoat ? new ItemStack(Items.BAMBOO_CHEST_RAFT) : new ItemStack(Items.BAMBOO_RAFT);
            case "cherry" -> isChestBoat ? new ItemStack(Items.CHERRY_CHEST_BOAT) : new ItemStack(Items.CHERRY_BOAT);
            default -> isChestBoat ? new ItemStack(Items.OAK_CHEST_BOAT) : new ItemStack(Items.OAK_BOAT);
        };
    }
}