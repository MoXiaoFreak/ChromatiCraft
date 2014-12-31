package Reika.ChromatiCraft.API;

import java.util.Random;

import net.minecraft.world.World;
import Reika.DragonAPI.Instantiable.Event.WorldGenEvent;


/** Fired when a rainbow tree is generated. */
public class RainbowTreeEvent extends WorldGenEvent {

	public RainbowTreeEvent(World world, int x, int y, int z, Random r) {
		super(world, x, y, z, r);
	}

}