/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2014
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Auxiliary.Interfaces;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public interface ItemOnRightClick extends IInventory {

	public ItemStack onRightClickWith(ItemStack item);

}
