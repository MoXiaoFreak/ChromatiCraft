/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Auxiliary;

import java.util.ArrayList;
import java.util.Collection;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import Reika.ChromatiCraft.Registry.ChromaBlocks;
import Reika.ChromatiCraft.Registry.ChromaTiles;
import Reika.DragonAPI.Instantiable.Data.Maps.BlockMap;
import Reika.DragonAPI.Libraries.ReikaPlayerAPI;

public class ChromaHelpData {

	public static final ChromaHelpData instance = new ChromaHelpData();

	private final BlockMap<HelpKey> data = new BlockMap();

	private static final String NBT_TAG = "ChromaExploreHelp";

	private ChromaHelpData() {
		this.addKey(ChromaBlocks.CRYSTAL, "crystals");
		this.addKey(ChromaBlocks.RAINBOWLEAF, "rainbowleaf");
		this.addKey(ChromaBlocks.PYLON, ChromaTiles.PYLON.getBlockMetadata(), "pylon");
		this.addKey(ChromaBlocks.PYLONSTRUCT, 3, "pylon");
		this.addKey(ChromaBlocks.PYLONSTRUCT, 4, "pylon");
		this.addKey(ChromaBlocks.PYLONSTRUCT, 5, "pylon");
		this.addKey(ChromaBlocks.RUNE, "rune");
		this.addKey(ChromaBlocks.TIEREDORE, 0, "ore0");
		this.addKey(ChromaBlocks.TIEREDORE, 1, "ore1");
		this.addKey(ChromaBlocks.TIEREDORE, 2, "ore2");
		this.addKey(ChromaBlocks.TIEREDORE, 3, "ore3");
		this.addKey(ChromaBlocks.TIEREDORE, 4, "ore4");
		this.addKey(ChromaBlocks.TIEREDORE, 5, "ore5");
		this.addKey(ChromaBlocks.TIEREDORE, 6, "ore6");
		this.addKey(ChromaBlocks.TIEREDORE, 7, "ore7");
		this.addKey(ChromaBlocks.TIEREDORE, 8, "ore8");
		this.addKey(ChromaBlocks.TIEREDORE, 9, "ore9");
		this.addKey(ChromaBlocks.TIEREDORE, 9, "ore10");
		this.addKey(ChromaBlocks.TIEREDORE, 9, "ore11");
		this.addKey(ChromaBlocks.TIEREDORE, 9, "ore12");
		this.addKey(ChromaBlocks.TIEREDPLANT, 0, "plant0");
		this.addKey(ChromaBlocks.TIEREDPLANT, 1, "plant1");
		this.addKey(ChromaBlocks.TIEREDPLANT, 2, "plant2");
		this.addKey(ChromaBlocks.TIEREDPLANT, 3, "plant3");
		this.addKey(ChromaBlocks.TIEREDPLANT, 4, "plant4");
	}

	private void addKey(ChromaBlocks b, String s) {
		data.put(b.getBlockInstance(), new HelpKey(s));
	}

	private void addKey(ChromaBlocks b, int meta, String s) {
		data.put(b.getBlockInstance(), meta, new HelpKey(s));
	}

	private HelpKey getKey(Block b, int meta) {
		return data.get(b, meta);
	}

	public String getText(Block b, int meta) {
		HelpKey key = this.getKey(b, meta);
		return key != null ? key.getText() : null;
	}

	public String getText(World world, int x, int y, int z) {
		return this.getText(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
	}
	/*
	public String getText(World world, MovingObjectPosition mov) {
		return this.getText(world, mov.blockX, mov.blockY, mov.blockZ);
	}*/

	public Collection<String> getHelpKeys() {
		Collection<String> c = new ArrayList();
		for (HelpKey h : data.values()) {
			String s = h.key;
			if (!c.contains(s))
				c.add(s);
		}
		return c;
	}

	private static class HelpKey {

		private final String key;

		private HelpKey(String xml) {
			key = xml;
		}

		public String getText() {
			return ChromaDescriptions.getHoverText(key);
		}

	}

	public void markDiscovered(EntityPlayer ep, Block b, int meta) {
		NBTTagCompound nbt = ReikaPlayerAPI.getDeathPersistentNBT(ep);
		NBTTagCompound tag = nbt.getCompoundTag(NBT_TAG);
		String sg = String.format("%d:%d", Block.getIdFromBlock(b), meta);
		boolean has = tag.getBoolean(sg);
		if (!has) {
			tag.setBoolean(sg, true);
			nbt.setTag(NBT_TAG, tag);
			ReikaPlayerAPI.syncCustomDataFromClient(ep);
		}
	}

	public boolean hasDiscovered(EntityPlayer ep, Block b, int meta) {
		NBTTagCompound nbt = ReikaPlayerAPI.getDeathPersistentNBT(ep);
		NBTTagCompound tag = nbt.getCompoundTag(NBT_TAG);
		String sg = String.format("%d:%d", Block.getIdFromBlock(b), meta);
		return tag.getBoolean(sg);
	}

}
