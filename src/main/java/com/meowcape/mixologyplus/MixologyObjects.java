package com.meowcape.mixologyplus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Live references to the lab's interactive objects, tracked by spawn
 * events (the AGA lever is a decorative object, hence TileObject).
 */
@Singleton
public class MixologyObjects
{
	public static final int LEVER_MOX = ObjectID.MM_LAB_SWITCH_MOX;
	public static final int LEVER_AGA = ObjectID.MM_LAB_SWITCH_AGA;
	public static final int LEVER_LYE = ObjectID.MM_LAB_SWITCH_LYE;
	public static final int STATION_RETORT = ObjectID.MM_LAB_MACHINE_RETORT;
	public static final int STATION_AGITATOR = ObjectID.MM_LAB_MACHINE_AGITATOR;
	public static final int STATION_ALEMBIC = ObjectID.MM_LAB_MACHINE_ALEMBIC;
	public static final int VESSEL = ObjectID.MM_LAB_VESSEL;
	public static final int CONVEYOR = ObjectID.MM_LAB_CONVEYOR;
	// digweed spawn spots (multiloc parents, one per corner); their varbit
	// morphs them 0 -> "Young digweed" (always visible, not pickable),
	// 1 -> "Mature digweed" (pickable), 2 -> nothing (cache-verified)
	public static final int HERB_SPOT_1 = ObjectID.MM_HERB_1;
	public static final int HERB_SPOT_2 = ObjectID.MM_HERB_2;
	public static final int HERB_SPOT_3 = ObjectID.MM_HERB_3;
	public static final int HERB_SPOT_4 = ObjectID.MM_HERB_4;
	public static final int[] HERB_SPOTS = {HERB_SPOT_1, HERB_SPOT_2, HERB_SPOT_3, HERB_SPOT_4};
	public static final int HERB_MATURE = ObjectID.MM_HERB_READY;

	/**
	 * Every live instance per id: some lab objects (the conveyor belt) are
	 * the same id repeated once per segment.
	 */
	private final Map<Integer, List<TileObject>> objects = new HashMap<>();

	public void track(TileObject object)
	{
		if (object == null)
		{
			return;
		}
		switch (object.getId())
		{
			case LEVER_MOX:
			case LEVER_AGA:
			case LEVER_LYE:
			case STATION_RETORT:
			case STATION_AGITATOR:
			case STATION_ALEMBIC:
			case VESSEL:
			case CONVEYOR:
			case HERB_SPOT_1:
			case HERB_SPOT_2:
			case HERB_SPOT_3:
			case HERB_SPOT_4:
				objects.computeIfAbsent(object.getId(), k -> new ArrayList<>()).add(object);
				break;
			default:
		}
	}

	public void untrack(TileObject object)
	{
		if (object == null)
		{
			return;
		}
		List<TileObject> list = objects.get(object.getId());
		if (list != null)
		{
			list.remove(object);
			if (list.isEmpty())
			{
				objects.remove(object.getId());
			}
		}
	}

	/**
	 * The most recently spawned instance of an id, or null.
	 */
	public TileObject get(int objectId)
	{
		List<TileObject> list = objects.get(objectId);
		return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
	}

	/**
	 * The instance of an id nearest to a point (e.g. the player), or null.
	 */
	public TileObject nearest(int objectId, LocalPoint to)
	{
		List<TileObject> list = objects.get(objectId);
		if (list == null || list.isEmpty())
		{
			return null;
		}
		if (to == null)
		{
			return list.get(list.size() - 1);
		}
		TileObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (TileObject o : list)
		{
			LocalPoint p = o.getLocalLocation();
			int d = p == null ? Integer.MAX_VALUE : p.distanceTo(to);
			if (best == null || d < bestDist)
			{
				best = o;
				bestDist = d;
			}
		}
		return best;
	}

	public void clear()
	{
		objects.clear();
	}

	/**
	 * Station object id for an order modifier (1=Agitator, 2=Retort,
	 * 3=Alembic).
	 */
	public static int stationForModifier(int modifier)
	{
		switch (modifier)
		{
			case 1:
				return STATION_AGITATOR;
			case 2:
				return STATION_RETORT;
			case 3:
				return STATION_ALEMBIC;
			default:
				return 0;
		}
	}
}
