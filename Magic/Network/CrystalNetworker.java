/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Magic.Network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import Reika.ChromatiCraft.ChromatiCraft;
import Reika.ChromatiCraft.Auxiliary.CrystalNetworkLogger;
import Reika.ChromatiCraft.Auxiliary.CrystalNetworkLogger.FlowFail;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalFuse;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalNetworkTile;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalReceiver;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalSource;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalTransmitter;
import Reika.ChromatiCraft.Magic.Interfaces.NaturalCrystalSource;
import Reika.ChromatiCraft.Magic.Interfaces.NotifiedNetworkTile;
import Reika.ChromatiCraft.Magic.Network.CrystalNetworkException.InvalidLocationException;
import Reika.ChromatiCraft.Registry.CrystalElement;
import Reika.ChromatiCraft.TileEntity.Networking.TileEntityCrystalPylon;
import Reika.ChromatiCraft.World.PylonGenerator;
import Reika.DragonAPI.Auxiliary.Trackers.TickRegistry.TickHandler;
import Reika.DragonAPI.Auxiliary.Trackers.TickRegistry.TickType;
import Reika.DragonAPI.Instantiable.Data.WeightedRandom;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldChunk;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldLocation;
import Reika.DragonAPI.Instantiable.Data.Maps.MultiMap;
import Reika.DragonAPI.Instantiable.Data.Maps.PluralMap;
import Reika.DragonAPI.Instantiable.Data.Maps.TileEntityCache;
import Reika.DragonAPI.Instantiable.Event.SetBlockEvent;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;
import Reika.DragonAPI.Libraries.MathSci.ReikaPhysicsHelper;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;

public class CrystalNetworker implements TickHandler {

	public static final CrystalNetworker instance = new CrystalNetworker();

	private static final String NBT_TAG = "crystalnet";

	private final TileEntityCache<CrystalNetworkTile> tiles = new TileEntityCache();
	private final EnumMap<CrystalElement, TileEntityCache<TileEntityCrystalPylon>> pylons = new EnumMap(CrystalElement.class);
	private final MultiMap<Integer, CrystalFlow> flows = new MultiMap(new MultiMap.HashSetFactory());
	private final HashMap<UUID, WorldLocation> verifier = new HashMap();
	private final MultiMap<WorldChunk, CrystalLink> losCache = new MultiMap(new MultiMap.HashSetFactory()).setNullEmpty();
	private final PluralMap<CrystalLink> links = new PluralMap(2);
	private final HashSet<CrystalFlow> toBreak = new HashSet();
	private final ArrayList<NotifiedNetworkTile> notifyCache = new ArrayList();
	//private final Collection<ChunkRequest> pathfindingChunkRequests = new ConcurrentLinkedQueue();

	private static final Random rand = new Random();

	private CrystalNetworker() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void markChunkCache(SetBlockEvent evt) {
		if (!evt.world.isRemote) {
			WorldChunk wc = new WorldChunk(evt.world, evt.chunkLocation);
			Collection<CrystalLink> c = losCache.get(wc);
			if (c != null) {
				for (CrystalLink l : c) {

					WorldLocation l1 = l.loc1;
					WorldLocation l2 = l.loc2;

					double[] angs = ReikaPhysicsHelper.cartesianToPolar(l1.xCoord-l2.xCoord, l1.yCoord-l2.yCoord, l1.zCoord-l2.zCoord);
					double[] angs2 = ReikaPhysicsHelper.cartesianToPolar(l1.xCoord-evt.xCoord, l1.yCoord-evt.yCoord, l1.zCoord-evt.zCoord);
					//Only check link if block near it
					if (ReikaMathLibrary.approxrAbs(angs[1], angs2[1], 3) && ReikaMathLibrary.approxrAbs(angs[2], angs2[2], 3)) {
						l.hasLOS = false;

						//Kill active flows if blocked
						for (CrystalFlow p : flows.get(evt.world.provider.dimensionId)) {
							if (!toBreak.contains(p) && p.containsLink(l) && !p.checkLineOfSight(l)) { //make only link check, not entire path
								CrystalNetworkLogger.logFlowBreak(p, FlowFail.SIGHT);
								this.schedulePathBreak(p);
							}
						}
					}
				}
			}
		}
	}

	private void schedulePathBreak(CrystalFlow p) {
		toBreak.add(p);
	}

	void addLink(CrystalLink l, boolean connect) {
		l.hasLOS = connect;
		for (WorldChunk wc : l.chunks)
			losCache.addValue(wc, l);
		links.put(l, l.loc1, l.loc2);
	}

	void addLink(WorldLocation l1, WorldLocation l2, boolean connect) {
		this.addLink(new CrystalLink(l1, l2), connect);
	}

	CrystalLink getLink(WorldLocation l1, WorldLocation l2) {
		CrystalLink l = links.get(l1, l2);
		if (l == null) {
			l = new CrystalLink(l1, l2);
			this.addLink(l, false);
		}
		return l;
	}

	@SubscribeEvent
	public void clearOnUnload(WorldEvent.Unload evt) {
		PylonFinder.stopAllSearches();
		int dim = evt.world.provider.dimensionId;
		ChromatiCraft.logger.debug("Unloading dimension "+dim+", clearing crystal network.");
		try {
			this.clear(dim);
			for (WorldLocation c : tiles.keySet()) {
				CrystalNetworkTile te = tiles.get(c);
				PylonFinder.removePathsWithTile(te);
				if (te instanceof CrystalTransmitter)
					((CrystalTransmitter)te).clearTargets(true);
			}
			tiles.removeWorld(evt.world);
			for (TileEntityCache c : pylons.values())
				c.removeWorld(evt.world);
		}
		catch (ConcurrentModificationException e) {
			ChromatiCraft.logger.logError("Clearing the crystal network on world unload caused a CME. This is indicative of a deeper problem.");
			e.printStackTrace();
		}
	}

	private void save(NBTTagCompound NBT) {
		NBTTagCompound tag = NBT.getCompoundTag(NBT_TAG);
		tiles.writeToNBT(tag);
		NBT.setTag(NBT_TAG, tag);
		//ReikaJavaLibrary.pConsole(tiles+" to "+tag, Side.SERVER);
	}

	private void load(NBTTagCompound NBT) {
		NBTTagCompound tag = NBT.getCompoundTag(NBT_TAG);
		tiles.readFromNBT(tag);

		for (CrystalNetworkTile te : tiles.values()) {
			if (te instanceof TileEntityCrystalPylon) {
				TileEntityCrystalPylon tile = (TileEntityCrystalPylon)te;
				this.addPylon(tile);
			}
		}
		//ReikaJavaLibrary.pConsole(tiles+" from "+tag, Side.SERVER);
	}

	public boolean checkConnectivity(CrystalElement e, CrystalReceiver r) {
		EntityPlayer ep = r.getPlacerUUID() != null ? r.getWorld().func_152378_a(r.getPlacerUUID()) : null;
		try {
			CrystalPath p = new PylonFinder(e, r, ep).findPylon();
			return p != null && p.canTransmit();
		}
		catch (ConcurrentModificationException ex) {
			ex.printStackTrace();
			ChromatiCraft.logger.logError("CME during pathfinding!");
			return false;
		}
	}

	public CrystalSource getConnectivity(CrystalElement e, CrystalReceiver r) {
		EntityPlayer ep = r.getPlacerUUID() != null ? r.getWorld().func_152378_a(r.getPlacerUUID()) : null;
		try {
			CrystalPath p = new PylonFinder(e, r, ep).findPylon();
			return p != null && p.canTransmit() ? p.transmitter : null;
		}
		catch (ConcurrentModificationException ex) {
			ex.printStackTrace();
			ChromatiCraft.logger.logError("CME during pathfinding!");
			return null;
		}
	}

	public boolean makeRequest(CrystalReceiver r, CrystalElement e, int amount, int range) {
		return this.makeRequest(r, e, amount, r.getWorld(), range, Integer.MAX_VALUE);
	}

	public boolean makeRequest(CrystalReceiver r, CrystalElement e, int amount, int range, int maxthru) {
		return this.makeRequest(r, e, amount, r.getWorld(), range, maxthru);
	}

	public boolean makeRequest(CrystalReceiver r, CrystalElement e, int amount, World world, int range, int maxthru) {
		if (amount <= 0)
			return false;
		if (this.hasFlowTo(r, e, world))
			return false;
		EntityPlayer ep = r.getPlacerUUID() != null ? world.func_152378_a(r.getPlacerUUID()) : null;
		CrystalFlow p = new PylonFinder(e, r, ep).findPylon(amount, maxthru);
		//ReikaJavaLibrary.pConsole(p, Side.SERVER);
		CrystalNetworkLogger.logRequest(r, e, amount, p);
		if (p != null) {
			flows.addValue(world.provider.dimensionId, p);
			p.transmitter.onUsedBy(ep, e);
			return true;
		}
		return false;
	}

	public boolean findSourceWithX(CrystalReceiver r, CrystalElement e, int amount, int range, boolean consume) {
		EntityPlayer ep = r.getPlacerUUID() != null ? r.getWorld().func_152378_a(r.getPlacerUUID()) : null;
		CrystalPath p = new PylonFinder(e, r, ep).findPylonWith(amount);
		if (p != null) {
			if (consume)
				p.transmitter.drain(e, amount);
			return true;
		}
		return false;
	}

	public boolean hasFlowTo(CrystalReceiver r, CrystalElement e, World world) {
		Collection<CrystalFlow> li = flows.get(world.provider.dimensionId);
		for (CrystalFlow f : li) {
			if (f.element == e && f.receiver == r)
				return true;
		}
		return false;
	}

	public void tick(TickType type, Object... data) {
		for (int dim : flows.keySet()) {
			Collection<CrystalFlow> c = flows.get(dim);
			Iterator<CrystalFlow> it = c.iterator();
			while (it.hasNext()) {
				CrystalFlow p = it.next();

				if (toBreak.contains(p)) {
					p.receiver.onPathBroken(p, FlowFail.SIGHT); //sight
					p.resetTiles();
					it.remove();
				}
				else {
					if (p.transmitter.canConduct() && p.canTransmit()) {
						int amt = p.drain();
						if (amt > 0) {
							int add = p.receiver.receiveElement(p.element, amt);
							p.transmitter.drain(p.element, amt);
							if (p.isComplete()) {
								p.resetTiles();
								it.remove();
								CrystalNetworkLogger.logFlowSatisfy(p);
								p.receiver.onPathCompleted(p);
							}
							else if (add <= 0) {
								CrystalNetworkLogger.logFlowBreak(p, FlowFail.FULL);
								//p.receiver.onPathBroken(p.element);
								p.resetTiles();
								it.remove();
							}
						}
					}
					else {
						CrystalNetworkLogger.logFlowBreak(p, FlowFail.ENERGY);
						p.receiver.onPathBroken(p, FlowFail.ENERGY);
						p.resetTiles();
						it.remove();
					}
				}
			}
		}
		toBreak.clear();
		/*
		for (ChunkRequest cr : pathfindingChunkRequests) {
			Chunk c = cr.chunk.load();
			cr.pathfinder.receiveChunk(c);
		}
		pathfindingChunkRequests.clear();
		 */
	}

	public void clear(int dim) {
		//do not clear tiles!
		Collection<CrystalFlow> li = flows.get(dim);
		for (CrystalFlow f : li) {
			f.resetTiles();
		}
		flows.remove(dim);
	}

	public void addTile(CrystalNetworkTile te) {
		if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
			WorldLocation loc = PylonFinder.getLocation(te);
			CrystalNetworkTile old = tiles.get(loc);
			if (old != null) { //cache cleaning; old TEs may get out of sync for things like charge
				this.removeTile(old);
			}
			tiles.put(loc, te);
			if (te instanceof TileEntityCrystalPylon) {
				this.addPylon((TileEntityCrystalPylon)te);
			}
			this.verifyTileAt(te, loc);

			for (NotifiedNetworkTile tile : notifyCache) {
				tile.onTileNetworkTopologyChange(te, false);
			}

			if (te instanceof NotifiedNetworkTile) {
				notifyCache.add((NotifiedNetworkTile)te);
			}

			WorldCrystalNetworkData.initNetworkData(te.getWorld()).setDirty(true);
			if (te instanceof TileEntityCrystalPylon) {
				PylonLocationData.initNetworkData(te.getWorld()).setDirty(true);
			}
		}
	}

	private void verifyTileAt(CrystalNetworkTile te, WorldLocation loc) {
		UUID key = te.getUniqueID();
		WorldLocation prev = verifier.get(key);
		if (prev != null && !prev.equals(loc)) {
			te.getWorld().setBlockToAir(te.getX(), te.getY(), te.getZ());
			throw new InvalidLocationException(te, loc, prev);
		}
		else
			verifier.put(key, loc);
	}

	public UUID getNewUniqueID() {
		UUID id = UUID.randomUUID();
		while (verifier.containsKey(id))
			id = UUID.randomUUID();
		return id;
	}

	private void addPylon(TileEntityCrystalPylon te) {
		CrystalElement e = te.getColor();
		TileEntityCache<TileEntityCrystalPylon> c = pylons.get(e);
		if (c == null) {
			c = new TileEntityCache();
			pylons.put(e, c);
		}
		c.put(te);
	}

	public Collection<TileEntityCrystalPylon> getNearbyPylons(World world, double x, double y, double z, CrystalElement e, int range, boolean LOS) {
		return this.getNearbyPylons(world, MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), e, range, LOS);
	}

	public Collection<TileEntityCrystalPylon> getNearbyPylons(World world, int x, int y, int z, CrystalElement e, int range, boolean LOS) {
		TileEntityCache<TileEntityCrystalPylon> c = pylons.get(e);
		Collection<TileEntityCrystalPylon> li = new ArrayList();
		if (c != null) {
			for (WorldLocation loc : c.keySet()) {
				if (loc.dimensionID == world.provider.dimensionId && loc.getDistanceTo(x, y, z) <= range) {
					if (!LOS || PylonFinder.lineOfSight(world, x, y, z, loc.xCoord, loc.yCoord, loc.zCoord)) {
						li.add(c.get(loc));
					}
				}
			}
		}
		return li;
	}

	public void removeTile(CrystalNetworkTile te) {
		tiles.remove(PylonFinder.getLocation(te));

		if (te instanceof NotifiedNetworkTile) {
			notifyCache.remove(te);
		}
		for (NotifiedNetworkTile tile : notifyCache) {
			tile.onTileNetworkTopologyChange(te, true);
		}

		if (te instanceof TileEntityCrystalPylon) {
			TileEntityCrystalPylon tile = (TileEntityCrystalPylon)te;
			TileEntityCache<TileEntityCrystalPylon> c = pylons.get(tile.getColor());
			if (c != null)
				c.remove(tile);
		}
		Collection<CrystalFlow> li = flows.get(te.getWorld().provider.dimensionId);
		Iterator<CrystalFlow> it = li.iterator();
		while (it.hasNext()) {
			CrystalFlow p = it.next();
			if (p.contains(te)) {
				CrystalNetworkLogger.logFlowBreak(p, FlowFail.TILE);
				p.resetTiles();
				p.receiver.onPathBroken(p, FlowFail.TILE);
				it.remove();
			}
		}
		PylonFinder.removePathsWithTile(te);
		WorldCrystalNetworkData.initNetworkData(te.getWorld()).setDirty(true); //was false
		if (te instanceof TileEntityCrystalPylon) {
			PylonLocationData.initNetworkData(te.getWorld()).setDirty(true);
		}
	}

	public void breakPaths(CrystalNetworkTile te) {
		Collection<CrystalFlow> li = flows.get(te.getWorld().provider.dimensionId);
		Iterator<CrystalFlow> it = li.iterator();
		while (it.hasNext()) {
			CrystalFlow p = it.next();
			if (p.contains(te)) {
				CrystalNetworkLogger.logFlowBreak(p, FlowFail.TILE);
				p.receiver.onPathBroken(p, FlowFail.TILE);
				p.resetTiles();
				it.remove();
			}
		}
	}

	ArrayList<CrystalTransmitter> getTransmittersTo(CrystalReceiver r, CrystalElement e) {
		ArrayList<CrystalTransmitter> li = new ArrayList();
		for (WorldLocation c : tiles.keySet()) {
			if (c.dimensionID == r.getWorld().provider.dimensionId) {
				CrystalNetworkTile tile = tiles.get(c);
				if (tile instanceof CrystalTransmitter && r != tile) {
					CrystalTransmitter te = (CrystalTransmitter)tile;
					if (te.canConduct() && te.canTransmitTo(r)) {
						if (e == null || te.isConductingElement(e)) {
							double d = te.getDistanceSqTo(r.getX(), r.getY(), r.getZ());
							//ReikaJavaLibrary.pConsole(e+": "+d+": "+te);
							double send = te.getSendRange();
							double dist = r.getReceiveRange();
							if (d <= Math.min(dist*dist, send*send)) {
								li.add(te);
							}
						}
					}
				}
			}
		}
		return li;
	}

	ArrayList<CrystalReceiver> getNearbyReceivers(CrystalTransmitter r, CrystalElement e) {
		ArrayList<CrystalReceiver> li = new ArrayList();
		try {
			for (WorldLocation c : tiles.keySet()) {
				if (c.dimensionID == r.getWorld().provider.dimensionId) {
					CrystalNetworkTile tile = tiles.get(c);
					if (tile instanceof CrystalReceiver && r != tile) {
						CrystalReceiver te = (CrystalReceiver)tile;
						if (te.canConduct() && r.canTransmitTo(te)) {
							if (e == null || te.isConductingElement(e)) {
								double d = te.getDistanceSqTo(r.getX(), r.getY(), r.getZ());
								//ReikaJavaLibrary.pConsole(e+": "+d+": "+te);
								double send = r.getSendRange();
								double dist = te.getReceiveRange();
								if (d <= Math.min(dist*dist, send*send)) {
									li.add(te);
								}
							}
						}
					}
				}
			}
		}
		catch (ConcurrentModificationException ex) {
			ex.printStackTrace();
			ChromatiCraft.logger.logError("CME when trying to pathfind on the crystal network. This indicates a deeper issue.");
		}
		return li;
	}

	ArrayList<CrystalSource> getAllSourcesFor(CrystalElement e, boolean activeOnly) {
		ArrayList<CrystalSource> li = new ArrayList();
		for (WorldLocation c : tiles.keySet()) {
			CrystalNetworkTile tile = tiles.get(c);
			if (tile instanceof CrystalSource) {
				CrystalSource te = (CrystalSource)tile;
				if (te.canConduct() || !activeOnly) {
					if (e == null || te.isConductingElement(e)) {
						li.add(te);
					}
				}
			}
		}
		return li;
	}

	public CrystalNetworkTile getNearestTileOfType(CrystalNetworkTile te, Class<? extends CrystalNetworkTile> type, int range) {
		CrystalNetworkTile ret = null;
		double dist = Double.POSITIVE_INFINITY;
		HashSet<WorldLocation> rem = new HashSet();
		for (WorldLocation c : tiles.keySet()) {
			CrystalNetworkTile tile = tiles.get(c);
			if (tile == null) {
				ChromatiCraft.logger.logError("Null tile at "+c+" but still cached?!");
				//c.setBlock(Blocks.brick_block);
				rem.add(c);
			}
			else if (tile == te) {

			}
			else if (te.getWorld().provider.dimensionId == c.dimensionID) {
				if (type.isAssignableFrom(tile.getClass())) {
					double d = tile.getDistanceSqTo(te.getX(), te.getY(), te.getZ());
					if (d <= range*range && d < dist) {
						dist = d;
						ret = tile;
					}
				}
			}
		}
		for (WorldLocation loc : rem) {
			tiles.remove(loc);
		}
		return ret;
	}

	public Collection<CrystalNetworkTile> getNearTilesOfType(CrystalNetworkTile te, Class<? extends CrystalNetworkTile> type, int range) {
		return this.getNearTilesOfType(te.getWorld(), te.getX(), te.getY(), te.getZ(), type, range);
	}

	public Collection<CrystalNetworkTile> getNearTilesOfType(World world, int x, int y, int z, Class<? extends CrystalNetworkTile> type, int range) {
		Collection<CrystalNetworkTile> ret = new ArrayList();
		HashSet<WorldLocation> rem = new HashSet();
		for (WorldLocation c : tiles.keySet()) {
			CrystalNetworkTile tile = tiles.get(c);
			if (tile == null) {
				ChromatiCraft.logger.logError("Null tile at "+c+" but still cached?!");
				//c.setBlock(Blocks.brick_block);
				rem.add(c);
			}
			else if (world.provider.dimensionId == c.dimensionID) {
				if (type.isAssignableFrom(tile.getClass())) {
					double d = tile.getDistanceSqTo(x, y, z);
					if (d <= range*range) {
						ret.add(tile);
					}
				}
			}
		}
		for (WorldLocation loc : rem) {
			tiles.remove(loc);
		}
		return ret;
	}

	@Override
	public EnumSet<TickType> getType() {
		return EnumSet.of(TickType.SERVER);
	}

	@Override
	public boolean canFire(Phase p) {
		return p == Phase.START;
	}

	@Override
	public String getLabel() {
		return "Crystal Networker";
	}

	public static class PylonLocationData extends WorldSavedData {

		private static final String IDENTIFIER = PylonGenerator.NBT_TAG;

		public PylonLocationData() {
			super(IDENTIFIER);
		}

		public PylonLocationData(String s) {
			super(s);
		}

		@Override
		public void readFromNBT(NBTTagCompound NBT) {
			PylonGenerator.instance.loadPylonLocations(NBT);
		}

		@Override
		public void writeToNBT(NBTTagCompound NBT) {
			PylonGenerator.instance.savePylonLocations(NBT);
		}

		private static PylonLocationData initNetworkData(World world) {
			PylonLocationData data = (PylonLocationData)world.loadItemData(PylonLocationData.class, IDENTIFIER);
			if (data == null) {
				data = new PylonLocationData();
				world.setItemData(IDENTIFIER, data);
			}
			return data;
		}
	}

	public static class WorldCrystalNetworkData extends WorldSavedData {

		private static final String IDENTIFIER = NBT_TAG;

		public WorldCrystalNetworkData() {
			super(IDENTIFIER);
		}

		public WorldCrystalNetworkData(String s) {
			super(s);
		}

		@Override
		public void readFromNBT(NBTTagCompound NBT) {
			instance.load(NBT);
		}

		@Override
		public void writeToNBT(NBTTagCompound NBT) {
			instance.save(NBT);
		}

		private static WorldCrystalNetworkData initNetworkData(World world) {
			WorldCrystalNetworkData data = (WorldCrystalNetworkData)world.loadItemData(WorldCrystalNetworkData.class, IDENTIFIER);
			if (data == null) {
				data = new WorldCrystalNetworkData();
				world.setItemData(IDENTIFIER, data);
			}
			return data;
		}
	}

	static class CrystalLink {

		public final WorldLocation loc1;
		public final WorldLocation loc2;
		private final HashSet<WorldChunk> chunks = new HashSet();
		private boolean hasLOS = false;

		CrystalLink(WorldLocation l1, WorldLocation l2) {
			loc1 = l1;
			loc2 = l2;
			double dd = l1.getDistanceTo(l2);
			World world = l1.getWorld();
			for (int i = 0; i < dd; i++) {
				int x = MathHelper.floor_double(l1.xCoord+i*(l2.xCoord-l1.xCoord)/dd);
				int z = MathHelper.floor_double(l1.zCoord+i*(l2.zCoord-l1.zCoord)/dd);
				WorldChunk ch = new WorldChunk(world, new ChunkCoordIntPair(x >> 4, z >> 4));
				if (!chunks.contains(ch))
					chunks.add(ch);
			}
		}

		void recalculateLOS() {
			hasLOS = PylonFinder.lineOfSight(loc1, loc2);
		}

		public boolean isChunkInPath(WorldChunk wc) {
			return chunks.contains(wc);
		}

		@Override
		public final int hashCode() {
			return loc1.hashCode()^loc2.hashCode();
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof CrystalLink) {
				CrystalLink l = (CrystalLink)o;
				return l.loc1.equals(loc1) && l.loc2.equals(loc2);
			}
			return false;
		}

		@Override
		public String toString() {
			return "["+loc1+" > "+loc2+"]";
		}

		final boolean hasLineOfSight() {
			return hasLOS;
		}

	}

	public void overloadColorConnectedTo(CrystalTransmitter te, CrystalElement color, int num, boolean recursive) {
		ArrayList<CrystalReceiver> li = this.getAllColorConnectedTo(te, color, recursive);
		if (li.isEmpty())
			return;
		WeightedRandom<CrystalNetworkTile> w = new WeightedRandom();
		for (CrystalReceiver r : li) {
			if (r instanceof NaturalCrystalSource)
				continue;
			double wt = 1;
			if (r instanceof CrystalFuse) {
				wt = ((CrystalFuse)r).getFailureWeight(color);
			}
			w.addEntry(r, wt*10);
		}
		for (int i = 0; i < num; i++) {
			CrystalNetworkTile tile = w.getRandomEntry();
			if (tile instanceof CrystalFuse) {
				((CrystalFuse)tile).overload(color);
			}
			else {
				double x = tile.getX()+0.5;
				double y = tile.getY()+0.5;
				double z = tile.getZ()+0.5;
				tile.getWorld().setBlock(tile.getX(), tile.getY(), tile.getZ(), Blocks.air);
				tile.getWorld().createExplosion(null, x, y, z, 1.5F+rand.nextFloat()*1.5F, true);
			}
		}
	}

	public ArrayList<CrystalReceiver> getAllColorConnectedTo(CrystalTransmitter te, CrystalElement color, boolean recursive) {
		return this.getAllColorConnectedTo(te, color, recursive, new HashSet());
	}

	private ArrayList<CrystalReceiver> getAllColorConnectedTo(CrystalTransmitter te, CrystalElement color, boolean recursive, HashSet<WorldLocation> locs) {
		ArrayList<CrystalReceiver> li = this.getNearbyReceivers(te, color);
		if (recursive) {
			for (CrystalReceiver r : li) {
				WorldLocation loc = new WorldLocation(r.getWorld(), r.getX(), r.getY(), r.getZ());
				if (!locs.contains(loc)) {
					locs.add(loc);
					if (r instanceof CrystalTransmitter) {
						ArrayList<CrystalReceiver> li2 = this.getAllColorConnectedTo((CrystalTransmitter)r, color, recursive, locs);
						for (CrystalReceiver r2 : li2) {
							WorldLocation loc2 = new WorldLocation(r.getWorld(), r.getX(), r.getY(), r.getZ());
							if (!locs.contains(loc2)) {
								locs.add(loc2);
								li.add(r2);
							}
						}
					}
				}
			}
		}
		return li;
	}

}
