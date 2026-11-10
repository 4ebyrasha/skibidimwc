package com.paneedah.weaponlib.crafting;

import net.minecraft.item.ItemStack;

/**
 * Represents a crafting recipe, defining several key properties using getters and setters:
 * <ul>
 *   <li>The output with {@link #getOutput}</li>
 *   <li>The required items and their quantities with {@link #getCraftingRecipe} & {@link #setCraftingRecipe}</li>
 *   <li>The group with {@link #getCraftingGroup} & {@link #setCraftingGroup}</li>
 * </ul>
 *
 * @author Homer Riva-Cambrin & Luna Mira Lage (Desoroxxx)
 * @version 2024-12-04
 * @since 0.1
 */
public interface ICraftingRecipe {

    // region Getters

    ItemStack getOutput();

    CraftingEntry[] getCraftingRecipe();

    CraftingGroup getCraftingGroup();

    // endregion

    // region Setters

    void setCraftingRecipe(final CraftingEntry[] craftingRecipe);

    void setCraftingGroup(final CraftingGroup group);

    // endregion
}
