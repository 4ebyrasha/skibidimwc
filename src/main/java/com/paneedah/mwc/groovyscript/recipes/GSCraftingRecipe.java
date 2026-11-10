package com.paneedah.mwc.groovyscript.recipes;

import com.paneedah.weaponlib.crafting.CraftingEntry;
import com.paneedah.weaponlib.crafting.CraftingGroup;
import com.paneedah.weaponlib.crafting.ICraftingRecipe;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;

/**
 * @author yor42 & Luna Mira Lage (Desoroxxx)
 * @version 2024-12-04
 * @since 0.1
 */
@Getter
@AllArgsConstructor
public final class GSCraftingRecipe implements ICraftingRecipe {

    private final ItemStack output;
    @Setter private CraftingGroup craftingGroup;
    @Setter private CraftingEntry[] craftingRecipe;
}
