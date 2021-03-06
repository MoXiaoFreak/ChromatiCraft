/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Base;

import net.minecraft.world.gen.feature.WorldGenerator;

public abstract class ChromaWorldGenerator extends WorldGenerator {

	public abstract float getGenerationChance(int cx, int cz);

}
