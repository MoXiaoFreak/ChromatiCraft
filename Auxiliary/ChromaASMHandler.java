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

import java.util.HashMap;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.classloading.FMLForgePlugin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import Reika.DragonAPI.ModList;
import Reika.DragonAPI.Libraries.Java.ReikaASMHelper;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex;

@SortingIndex(1001)
@MCVersion("1.7.10")
public class ChromaASMHandler implements IFMLLoadingPlugin {


	@Override
	public String[] getASMTransformerClass() {
		return new String[]{ASMExecutor.class.getName()};
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {

	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

	public static class ASMExecutor implements IClassTransformer {

		private static final HashMap<String, ClassPatch> classes = new HashMap();

		private static enum ClassPatch {
			ENDPROVIDER("net.minecraft.world.gen.ChunkProviderEnd", "ara"),
			REACHDIST("net.minecraft.client.multiplayer.PlayerControllerMP", "bje"),
			//CHARWIDTH("Reika.ChromatiCraft.Auxiliary.ChromaFontRenderer"), //Thank you, Optifine T_T
			CHUNKPOPLN("net.minecraft.world.gen.ChunkProviderServer", "ms"),
			;

			private final String obfName;
			private final String deobfName;

			private static final ClassPatch[] list = values();

			private ClassPatch(String name) {
				this(name, name);
			}

			private ClassPatch(String deobf, String obf) {
				obfName = obf;
				deobfName = deobf;
			}

			private byte[] apply(byte[] data) {
				ClassNode cn = new ClassNode();
				ClassReader classReader = new ClassReader(data);
				classReader.accept(cn, 0);
				switch(this) {
					case ENDPROVIDER: { //THIS WORKS
						if (ModList.ENDEREXPANSION.isLoaded())
							break;
						MethodNode m = ReikaASMHelper.getMethodByName(cn, "func_73187_a", "initializeNoiseField", "([DIIIIII)[D");
						String func = FMLForgePlugin.RUNTIME_DEOBF ? "func_76129_c" : "sqrt_float";
						AbstractInsnNode loc = ReikaASMHelper.getFirstMethodCall(cn, m, "net/minecraft/util/MathHelper", func, "(F)F");
						while (loc.getOpcode() != Opcodes.FSTORE) {
							loc = loc.getNext();
						}
						InsnList call = new InsnList();
						call.add(new VarInsnNode(Opcodes.FLOAD, 23)); //+1 to all the arguments because of double_2nd somewhere lower on stack
						call.add(new VarInsnNode(Opcodes.FLOAD, 21));
						call.add(new VarInsnNode(Opcodes.FLOAD, 22));
						call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "Reika/ChromatiCraft/Auxiliary/ChromaAux", "getIslandBias", "(FFF)F", false));
						call.add(new VarInsnNode(Opcodes.FSTORE, 23));
						m.instructions.insert(loc, call);
						ReikaASMHelper.log("Successfully applied "+this+" ASM handler!");
						break;
					}
					case REACHDIST: {
						MethodNode m = ReikaASMHelper.getMethodByName(cn, "func_78757_d", "getBlockReachDistance", "()F");
						m.instructions.insert(new InsnNode(Opcodes.I2F));
						m.instructions.insert(new FieldInsnNode(Opcodes.GETFIELD, "Reika/ChromatiCraft/Auxiliary/AbilityHelper", "playerReach", "I"));
						m.instructions.insert(new FieldInsnNode(Opcodes.GETSTATIC, "Reika/ChromatiCraft/Auxiliary/AbilityHelper", "instance", "LReika/ChromatiCraft/Auxiliary/AbilityHelper;"));
						AbstractInsnNode index = null;
						for (int i = 0; i < m.instructions.size(); i++) {
							AbstractInsnNode ain = m.instructions.get(i);
							if (ain.getOpcode() == Opcodes.FRETURN) {
								index = ain;
								break;
							}
						}
						if (index != null) {
							m.instructions.insertBefore(index, new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false));
							ReikaASMHelper.log("Successfully applied "+this+" ASM handler!");
						}
						break;
					}/*
				case CHARWIDTH: { //[I to [F
					try {
						Class optifine = Class.forName("optifine.OptiFineClassTransformer");
						ReikaASMHelper.log("Optifine loaded. Editing FontRenderer class.");
					}
					catch (Exception e) {
						ReikaASMHelper.log("Optifine loaded. Not editing FontRenderer class.");
						break;
					}
					/*
					//FieldNode fn = ReikaASMHelper.getFieldByName(cn, "charWidth");
					//cn.fields.remove(fn);
					String field = FMLForgePlugin.RUNTIME_DEOBF ? "field_78286_d" : "charWidth";
					int count = 0;
					boolean primed = false;
					for (MethodNode m : cn.methods) {
						for (int i = 0; i < m.instructions.size(); i++) {
							AbstractInsnNode ain = m.instructions.get(i);
							if (ain instanceof FieldInsnNode) {
								FieldInsnNode fin = (FieldInsnNode)ain;

								if (fin.name.equals(field) && fin.desc.equals("[I")) {
									fin.desc = "[F";
									count++;
									ReikaASMHelper.log("Successfully applied "+this+" ASM handler x"+count+"!");
									primed = true;
								}
								/*
								if (FMLForgePlugin.RUNTIME_DEOBF && fin.name.equals("charWidth")) {
									fin.name = "field_78286_d";
									fin.owner = "net/minecraft/client/gui/FontRenderer";
								}*//*
							}
							else if (primed && (ain.getOpcode() == Opcodes.IALOAD || ain.getOpcode() == Opcodes.IASTORE)) {
								if (ain.getOpcode() == Opcodes.IALOAD)
									ReikaASMHelper.changeOpcode(ain, Opcodes.FALOAD);
								if (ain.getOpcode() == Opcodes.IASTORE)
									ReikaASMHelper.changeOpcode(ain, Opcodes.FASTORE);
								ReikaASMHelper.log("Successfully applied "+this+" ASM handler x"+count+"b!");
								primed = false;
							}
						}
					}*//*
				break;
				}*/
					case CHUNKPOPLN: {
						MethodNode m = ReikaASMHelper.getMethodByName(cn, "func_73153_a", "populate", "(Lnet/minecraft/world/chunk/IChunkProvider;II)V");
						boolean primed = false;
						for (int i = 0; i < m.instructions.size(); i++) {
							AbstractInsnNode ain = m.instructions.get(i);
							if (ain.getOpcode() == Opcodes.INVOKEINTERFACE) {
								primed = true;
							}
							else if (primed && ain.getOpcode() == Opcodes.INVOKESTATIC) {
								MethodInsnNode min = (MethodInsnNode)ain;
								if (min.owner.contains("GameRegistry") && min.name.equals("generateWorld")) {
									primed = false;

									min.owner = "Reika/ChromatiCraft/Auxiliary/ChromaAux";
									min.name = "interceptChunkPopulation";
									min.desc = "(IILnet/minecraft/world/World;Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/chunk/IChunkProvider;)V";

									ReikaASMHelper.log("Successfully applied "+this+" ASM handler!");
									break;
								}
							}
						}
						break;

					}

				}

				ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS/* | ClassWriter.COMPUTE_FRAMES*/);
				cn.accept(writer);
				return writer.toByteArray();
			}
		}

		@Override
		public byte[] transform(String className, String className2, byte[] opcodes) {
			if (!classes.isEmpty()) {
				ClassPatch p = classes.get(className);
				if (p != null) {
					ReikaASMHelper.activeMod = "ChromatiCraft";
					ReikaASMHelper.log("Patching class "+p.deobfName);
					opcodes = p.apply(opcodes);
					classes.remove(className); //for maximizing performance
					ReikaASMHelper.activeMod = null;
				}
			}
			return opcodes;
		}

		static {
			for (int i = 0; i < ClassPatch.list.length; i++) {
				ClassPatch p = ClassPatch.list[i];
				String s = !FMLForgePlugin.RUNTIME_DEOBF ? p.deobfName : p.obfName;
				classes.put(s, p);
			}
		}
	}

}
