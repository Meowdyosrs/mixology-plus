package com.meowcape.mixologyplus;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mastering Mixology optimizer: ranks the current orders by how much each
 * potion raises the minimum of the resin-to-cost ratios for the selected
 * shop reward, recommends the digweed target, and tracks goal progress
 * with a session-rate ETA. Reads client-side state only (varbits, varps,
 * the inventory, and the lab's scene objects); nothing is automated.
 */
@PluginDescriptor(
	name = "Mixology Plus",
	description = "Mastering Mixology guide: picks which orders to make, numbers the levers in pull order, and walks you through every station, digweed and deposit step",
	tags = {"mixology", "mastering", "herblore", "minigame", "aldarium", "varlamore", "potion", "orders", "digweed", "resin", "helper", "skilling"}
)
public class MixologyPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(MixologyPlugin.class);
	/**
	 * The walk to the conveyor and the board reroll cost roughly a third of
	 * a potion's worth of time, so a deposit set is scored per
	 * (potions + this). It is what makes a mode weigh one more potion in
	 * this deposit against starting a fresh board.
	 */
	private static final double DEPOSIT_OVERHEAD = 0.35;
	private static final int[] TYPE_VARBITS = {
		VarbitID.MM_LAB_ORDER_1_TYPE, VarbitID.MM_LAB_ORDER_2_TYPE, VarbitID.MM_LAB_ORDER_3_TYPE,
	};
	private static final int[] MODIFIER_VARBITS = {
		VarbitID.MM_LAB_ORDER_1_MODIFIER, VarbitID.MM_LAB_ORDER_2_MODIFIER, VarbitID.MM_LAB_ORDER_3_MODIFIER,
	};
	// one per herb spot, same corner order as MixologyObjects.HERB_SPOTS
	private static final int[] HERB_VARBITS = {
		VarbitID.MM_HERB_READY_1, VarbitID.MM_HERB_READY_2,
		VarbitID.MM_HERB_READY_3, VarbitID.MM_HERB_READY_4,
	};
	private static final int LAB_REGION = 5521;

	@Inject
	private Client client;

	@Inject
	private MixologyConfig config;

	@Inject
	private MixologyState state;

	@Inject
	private DigweedInventoryOverlay digweedOverlay;

	@Inject
	private MixologyLabOverlay labOverlay;

	@Inject
	private MixologyProgressOverlay progressOverlay;

	@Inject
	private DigweedScreenFlash screenFlash;


	@Inject
	private MixologyObjects labObjects;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private final int[] lastSignals = new int[9];
	private boolean signalsPrimed;

	private final List<Integer> inventoryItems = new ArrayList<>();
	private final java.util.Set<Integer> warnedItemIds = new java.util.HashSet<>();
	private int flashStationId;
	private int flashStartProgress;
	private int flashStartSkillshot;
	private int flashWindow;
	private boolean warnedMixerValues;
	private boolean warnedHerbSignals;
	private boolean herbWasReady;
	/**
	 * Game ticks since start-up, for short timeouts.
	 */
	private int tickCount;
	/**
	 * Collect tracking (see trackCollect).
	 */
	private static final int COLLECT_GRACE_TICKS = 8;
	private int vesselTracked;
	private int vesselBaseline;
	private boolean collectedEarly;
	private MixPotion inTransit;
	private int inTransitBaseline;
	private int inTransitUntilTick;
	/**
	 * Where this plugin put the hint arrow, or null if it hasn't.
	 */
	private WorldPoint hintArrowAt;

	private static boolean take(java.util.Map<MixPotion, Integer> counts, MixPotion p)
	{
		Integer have = counts.get(p);
		if (have == null || have <= 0)
		{
			return false;
		}
		counts.put(p, have - 1);
		return true;
	}

	/**
	 * @return the station object id holding this potion (consuming the
	 * entry), or 0.
	 */
	private static int takeStation(List<int[]> stationContents, MixPotion p)
	{
		for (java.util.Iterator<int[]> it = stationContents.iterator(); it.hasNext(); )
		{
			int[] entry = it.next();
			if (entry[1] == p.ordinal())
			{
				it.remove();
				return entry[0];
			}
		}
		return 0;
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(digweedOverlay);
		overlayManager.add(labOverlay);
		overlayManager.add(progressOverlay);
		overlayManager.add(screenFlash);
		signalsPrimed = false;
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(digweedOverlay);
		overlayManager.remove(labOverlay);
		overlayManager.remove(progressOverlay);
		overlayManager.remove(screenFlash);
		clearHintArrow();
		labObjects.clear();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCount++;
		state.setTargetReward(config.targetReward());
		state.setInLab(inLab());

		// Reading at tick boundaries sidesteps the mid-delivery window
		// where all order varbits are momentarily reset to zero.
		int[] signals = new int[9];
		for (int i = 0; i < 3; i++)
		{
			signals[i] = client.getVarbitValue(TYPE_VARBITS[i]);
			signals[3 + i] = client.getVarbitValue(MODIFIER_VARBITS[i]);
		}
		signals[6] = client.getVarpValue(VarPlayerID.MIXOLOGY_MOX_POINTS);
		signals[7] = client.getVarpValue(VarPlayerID.MIXOLOGY_AGA_POINTS);
		signals[8] = client.getVarpValue(VarPlayerID.MIXOLOGY_LYE_POINTS);

		if (!signalsPrimed || !java.util.Arrays.equals(signals, lastSignals))
		{
			System.arraycopy(signals, 0, lastSignals, 0, signals.length);
			signalsPrimed = true;
			recompute(signals);
		}
		computeGuidance();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		inventoryItems.clear();
		ItemContainer container = event.getItemContainer();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					inventoryItems.add(item.getId());
				}
			}
		}
		// react instantly (collect -> next lever guidance, no tick lag)
		computeGuidance();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MixologyConfig.GROUP.equals(event.getGroup()))
		{
			// reward or mode changes re-rank the same signals
			signalsPrimed = false;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// mixer slots, station progress/contents, herbs, skillshots, vessel
		switch (event.getVarbitId())
		{
			case VarbitID.MM_LAB_MIXER_SLOT_0:
			case VarbitID.MM_LAB_MIXER_SLOT_1:
			case VarbitID.MM_LAB_MIXER_SLOT_2:
			case VarbitID.MM_RETORT_PROGRESS:
			case VarbitID.MM_ALEMBIC_PROGRESS:
			case VarbitID.MM_AGITATOR_PROGRESS:
			case VarbitID.MM_HERB_READY_1:
			case VarbitID.MM_HERB_READY_2:
			case VarbitID.MM_HERB_READY_3:
			case VarbitID.MM_HERB_READY_4:
			case VarbitID.MM_LAB_HIT_SKILLSHOT_AGITATOR:
			case VarbitID.MM_LAB_HIT_SKILLSHOT_ALEMBIC:
			case VarbitID.MM_LAB_VESSEL_READY:
			case VarbitID.MM_LAB_AGITATOR_POTION:
			case VarbitID.MM_LAB_RETORT_POTION:
			case VarbitID.MM_LAB_ALEMBIC_POTION:
				computeGuidance();
				break;
			default:
		}
	}

	/**
	 * While the retort is not the station you need, make its left-click do
	 * nothing harmful (the option drops below "Walk here"). Spam-clicking
	 * it after it finishes would otherwise put your NEXT potion in it. You
	 * can still right-click it on purpose.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.protectRetort() || !state.isInLab() || state.isRetortWanted()
			|| event.getIdentifier() != MixologyObjects.STATION_RETORT)
		{
			return;
		}
		event.getMenuEntry().setDeprioritized(true);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		labObjects.track(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		labObjects.untrack(event.getGameObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		labObjects.track(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		labObjects.untrack(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		labObjects.track(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		labObjects.untrack(event.getGroundObject());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
			case LOGIN_SCREEN:
			case HOPPING:
				hintArrowAt = null;
				labObjects.clear();
				state.setQuickActionId(0);
				flashStationId = 0;
				break;
			default:
		}
	}

	/**
	 * Quick-action windows announce themselves with a station VFX; the
	 * window is measured in station progress ticks (agitator 2, alembic 1)
	 * and ends early on a hit (skillshot varbit) or a missed window.
	 */
	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		int id = event.getGraphicsObject().getId();
		if (id == SpotanimID.VFX_MACHINERY_ALCHEMY01_AGITATOR01
			&& client.getVarbitValue(VarbitID.MM_LAB_AGITATOR_POTION) != 0)
		{
			flashStationId = MixologyObjects.STATION_AGITATOR;
			flashStartProgress = client.getVarbitValue(VarbitID.MM_AGITATOR_PROGRESS);
			// the hit varbit may still hold a stale value from a previous
			// success; a hit is a CHANGE from this baseline, not non-zero
			flashStartSkillshot = client.getVarbitValue(VarbitID.MM_LAB_HIT_SKILLSHOT_AGITATOR);
			flashWindow = 2;
		}
		else if (id == SpotanimID.VFX_MACHINERY_ALCHEMY01_ALEMBIC01
			&& client.getVarbitValue(VarbitID.MM_LAB_ALEMBIC_POTION) != 0)
		{
			flashStationId = MixologyObjects.STATION_ALEMBIC;
			flashStartProgress = client.getVarbitValue(VarbitID.MM_ALEMBIC_PROGRESS);
			flashStartSkillshot = client.getVarbitValue(VarbitID.MM_LAB_HIT_SKILLSHOT_ALEMBIC);
			flashWindow = 1;
		}
	}

	/**
	 * Lab guidance: what to mix at the levers, which station to use, and
	 * whether a quick-action window is open. Runs every tick (cheap).
	 */
	private void computeGuidance()
	{
		// vessel
		int rawVesselPotion = client.getVarbitValue(VarbitID.MM_LAB_VESSEL_READY);

		// tally what exists, by form: finished vs unfinished vs in-station
		// vs in-vessel; orders are then covered IN SEQUENCE so duplicate
		// orders of the same potion each claim their own copy.
		java.util.Map<MixPotion, Integer> finished = new java.util.HashMap<>();
		java.util.Map<MixPotion, Integer> unfinished = new java.util.HashMap<>();
		for (int itemId : inventoryItems)
		{
			boolean known = false;
			for (MixPotion p : MixPotion.values())
			{
				if (itemId == p.getFinishedItemId())
				{
					finished.merge(p, 1, Integer::sum);
					known = true;
				}
				else if (itemId == p.getUnfinishedItemId())
				{
					unfinished.merge(p, 1, Integer::sum);
					known = true;
				}
			}
			// surface unmapped mixology-range items (e.g. digweed-boosted
			// variants) so gaps in the id map show up in the log
			if (!known && itemId >= 29971 && itemId <= 30060 && itemId != 30031
				&& !warnedItemIds.contains(itemId))
			{
				warnedItemIds.add(itemId);
				log.debug("unmapped mixology-range item id {} in inventory", itemId);
			}
		}

		// Collecting is not one clean event: the vessel emptying and the
		// potion landing in the inventory can happen in either order, a few
		// ticks apart. Count the potion exactly once throughout, otherwise
		// guidance briefly sees it twice (skips a potion) or nowhere (asks
		// you to mix it again); both are most visible with duplicate orders.
		trackCollect(rawVesselPotion, unfinished);
		int vesselPotion = collectedEarly ? 0 : rawVesselPotion;
		if (inTransit != null)
		{
			unfinished.merge(inTransit, 1, Integer::sum);
		}
		// the mixer still shows the collected mix until the vessel empties
		boolean mixerStale = collectedEarly || inTransit != null;

		// station contents as (stationObjectId, potion) pairs so covering
		// an order also tells us WHICH station is working for it
		List<int[]> stationContents = new ArrayList<>();
		int[][] stationVarbits = {
			{VarbitID.MM_LAB_AGITATOR_POTION, MixologyObjects.STATION_AGITATOR},
			{VarbitID.MM_LAB_RETORT_POTION, MixologyObjects.STATION_RETORT},
			{VarbitID.MM_LAB_ALEMBIC_POTION, MixologyObjects.STATION_ALEMBIC},
		};
		for (int[] sv : stationVarbits)
		{
			MixPotion p = MixPotion.fromVarbit(client.getVarbitValue(sv[0]));
			if (p != null)
			{
				stationContents.add(new int[]{sv[1], p.ordinal()});
			}
		}
		MixPotion vessel = MixPotion.fromVarbit(vesselPotion);

		// cover MAKE orders in ranked sequence
		MixPotion mixTarget = null;
		int stationTarget = 0;
		int inProgressStation = 0;
		boolean vesselCollect = false;
		int makeCount = 0;
		int finishedCovered = 0;
		// stations still to visit, for the next-step preview: potions already
		// unfinished in the inventory first, then ones still to collect/mix
		List<Integer> stationsNow = new ArrayList<>();
		List<Integer> stationsLater = new ArrayList<>();
		for (MixologyState.Order order : state.getOrders())
		{
			if (!order.isMake())
			{
				continue;
			}
			makeCount++;
			MixPotion p = order.getPotion();
			if (take(finished, p))
			{
				finishedCovered++;
				continue;
			}
			int stationId = takeStation(stationContents, p);
			if (stationId != 0)
			{
				if (inProgressStation == 0)
				{
					inProgressStation = stationId;
				}
				continue;
			}
			int orderStation = MixologyObjects.stationForModifier(order.getModifier());
			// unfinished potions already in the inventory are matched to
			// orders BEFORE the one in the vessel: once collected it joins
			// the inventory at the back, so matching it first would swap
			// which order (and station) each copy of a duplicate is for
			if (take(unfinished, p))
			{
				stationsNow.add(orderStation);
				// THIS order's station, so a second identical potion is
				// guided to the second order's (possibly different) station
				if (stationTarget == 0)
				{
					stationTarget = orderStation;
				}
				continue;
			}
			if (vessel == p)
			{
				vessel = null;
				vesselCollect = true;
				stationsLater.add(orderStation);
				continue;
			}
			if (mixTarget == null)
			{
				mixTarget = p;
			}
			stationsLater.add(orderStation);
		}
		state.setMixTarget(mixTarget);

		// single-action queue, in natural gameplay order
		MixologyState.LabAction action;
		int actionStation = 0;
		int busy = busyStation();
		boolean combine = digweedCombineDue();
		if (busy != 0)
		{
			// a station only works while you stay on it, so a potion that
			// is still being processed comes before every other step; the
			// next step only shows once it is back in your inventory
			action = MixologyState.LabAction.ATTEND_STATION;
			actionStation = busy;
		}
		else if (combine)
		{
			// holding the digweed and the potion it doubles: combine them
			// now, before it can be deposited without the bonus
			action = MixologyState.LabAction.USE_DIGWEED;
		}
		else if (vesselCollect)
		{
			action = MixologyState.LabAction.COLLECT_VESSEL;
		}
		else if (mixTarget != null)
		{
			action = MixologyState.LabAction.MIX;
		}
		else if (stationTarget != 0)
		{
			action = MixologyState.LabAction.USE_STATION;
			actionStation = stationTarget;
		}
		else if (inProgressStation != 0)
		{
			action = MixologyState.LabAction.ATTEND_STATION;
			actionStation = inProgressStation;
		}
		else if (makeCount > 0 && finishedCovered == makeCount)
		{
			action = MixologyState.LabAction.DEPOSIT;
		}
		else
		{
			action = MixologyState.LabAction.NONE;
		}
		state.setLabAction(action);

		// Retort misclick guard: the retort is only "wanted" while it is
		// working or it is the next station to insert into
		boolean retortBusy = busy == MixologyObjects.STATION_RETORT;
		state.setRetortWanted(retortBusy
			|| (action == MixologyState.LabAction.USE_STATION && actionStation == MixologyObjects.STATION_RETORT));
		state.setActionStationId(actionStation);
		computeNextStep(action, actionStation, busy, combine, vesselCollect, mixTarget != null,
			stationsNow, stationsLater, makeCount > 0);

		// Lever plan: the exact levers to pull, in order, to turn what is in
		// the mixer now into the target recipe (see LeverPlanner).
		int[] steps = new int[0];
		boolean[] done = new boolean[0];
		if (mixTarget != null)
		{
			// a mix about to be collected empties the mixer, so the next
			// potion is planned from scratch; anything else in the mixer
			// (leftovers, a mix nobody ordered) is really there and counts
			int[] slots = new int[3];
			if (!vesselCollect && !mixerStale)
			{
				int[] slotVarbits = {VarbitID.MM_LAB_MIXER_SLOT_0,
					VarbitID.MM_LAB_MIXER_SLOT_1, VarbitID.MM_LAB_MIXER_SLOT_2};
				for (int i = 0; i < 3; i++)
				{
					int v = client.getVarbitValue(slotVarbits[i]);
					if (v > 3 && !warnedMixerValues)
					{
						warnedMixerValues = true;
						log.debug("unexpected mixer slot value {}", v);
					}
					slots[i] = v >= 1 && v <= 3 ? v : 0;
				}
			}
			int[] pulls = LeverPlanner.plan(slots, mixTarget.getCode());
			// Pastes already in the mixer that the plan keeps count as the
			// first steps, so labels keep their numbers as you pull: after
			// the 1st, the others still read "2nd" and "3rd". (When the
			// plan has to push leftovers out, there is nothing to keep and
			// the count starts from the next pull.)
			int kept = 0;
			for (int v : slots)
			{
				kept += v != 0 ? 1 : 0;
			}
			if (kept + pulls.length != 3)
			{
				kept = 0;
			}
			steps = new int[kept + pulls.length];
			done = new boolean[steps.length];
			int k = 0;
			for (int v : slots)
			{
				if (v != 0 && k < kept)
				{
					steps[k] = v - 1;
					done[k] = true;
					k++;
				}
			}
			System.arraycopy(pulls, 0, steps, kept, pulls.length);
		}
		state.setMixSteps(steps, done);

		updateDigweed();

		// quick-action flash upkeep
		if (flashStationId != 0)
		{
			boolean agitator = flashStationId == MixologyObjects.STATION_AGITATOR;
			int progress = client.getVarbitValue(agitator
				? VarbitID.MM_AGITATOR_PROGRESS : VarbitID.MM_ALEMBIC_PROGRESS);
			int skillshot = client.getVarbitValue(agitator
				? VarbitID.MM_LAB_HIT_SKILLSHOT_AGITATOR : VarbitID.MM_LAB_HIT_SKILLSHOT_ALEMBIC);
			int potion = client.getVarbitValue(agitator
				? VarbitID.MM_LAB_AGITATOR_POTION : VarbitID.MM_LAB_ALEMBIC_POTION);
			if (skillshot != flashStartSkillshot || potion == 0
				|| progress > flashStartProgress + flashWindow
				|| progress < flashStartProgress)
			{
				flashStationId = 0;
			}
		}
		state.setQuickActionId(flashStationId);
	}

	/**
	 * Bonus Herblore XP a station adds on top of the potion's own XP
	 * (wiki): agitator 14 + 2 for its clicks, alembic 14; the retort gives
	 * 2 XP per click while you work it, about 14 for a normal run.
	 */
	private static int stationBonusXp(int modifier)
	{
		switch (modifier)
		{
			case 1:
				return 16;
			case 2:
				return 14;
			case 3:
				return 14;
			default:
				return 0;
		}
	}

	/**
	 * Next-step preview: lays the remaining work out as the sequence the
	 * guidance walks through (station in use, collect, mix + collect, insert
	 * at each station, deposit), finds the current step in it, and keeps
	 * the first later step that happens somewhere else.
	 */
	private void computeNextStep(MixologyState.LabAction action, int actionStation, int busy,
		boolean combine, boolean vesselCollect, boolean mixing, List<Integer> stationsNow,
		List<Integer> stationsLater, boolean anyMake)
	{
		List<Object[]> seq = new ArrayList<>();
		if (busy != 0)
		{
			seq.add(new Object[]{MixologyState.LabAction.ATTEND_STATION, busy});
		}
		if (combine)
		{
			seq.add(new Object[]{MixologyState.LabAction.USE_DIGWEED, 0});
		}
		if (vesselCollect)
		{
			seq.add(new Object[]{MixologyState.LabAction.COLLECT_VESSEL, 0});
		}
		if (mixing)
		{
			seq.add(new Object[]{MixologyState.LabAction.MIX, 0});
			seq.add(new Object[]{MixologyState.LabAction.COLLECT_VESSEL, 0});
		}
		for (int st : stationsNow)
		{
			seq.add(new Object[]{MixologyState.LabAction.USE_STATION, st});
		}
		for (int st : stationsLater)
		{
			seq.add(new Object[]{MixologyState.LabAction.USE_STATION, st});
		}
		if (anyMake)
		{
			seq.add(new Object[]{MixologyState.LabAction.DEPOSIT, 0});
		}

		MixologyState.LabAction next = MixologyState.LabAction.NONE;
		int nextStation = 0;
		int current = -1;
		for (int i = 0; i < seq.size(); i++)
		{
			if (seq.get(i)[0] == action && (int) seq.get(i)[1] == actionStation)
			{
				current = i;
				break;
			}
		}
		if (current >= 0)
		{
			for (int i = current + 1; i < seq.size(); i++)
			{
				MixologyState.LabAction a = (MixologyState.LabAction) seq.get(i)[0];
				int st = (int) seq.get(i)[1];
				// a repeat of a vessel step (collect -> collect) is the same
				// step, but a station appearing again is a real next step:
				// a second potion going into the same station
				boolean repeat = st == 0 && a == action;
				if (!repeat)
				{
					next = a;
					nextStation = st;
					break;
				}
			}
		}
		state.setNextAction(next);
		state.setNextStationId(nextStation);
	}

	/**
	 * Follows one collect from start to finish so the collected potion is
	 * counted exactly once:
	 * <ul>
	 * <li>while the vessel holds potion P, remember how many unfinished P
	 * were in the inventory; if one more turns up while the vessel still
	 * shows P, it is the collected potion arriving early, so the vessel is
	 * treated as empty ({@link #collectedEarly});</li>
	 * <li>if the vessel empties first, P is still on its way: count it as
	 * held ({@link #inTransit}) until it arrives, or for at most
	 * COLLECT_GRACE_TICKS.</li>
	 * </ul>
	 */
	private void trackCollect(int rawVessel, java.util.Map<MixPotion, Integer> unfinished)
	{
		if (rawVessel != 0)
		{
			int count = unfinished.getOrDefault(MixPotion.fromVarbit(rawVessel), 0);
			if (rawVessel != vesselTracked)
			{
				// a new mix is in the vessel
				vesselBaseline = count;
				collectedEarly = false;
			}
			else if (!collectedEarly)
			{
				// using one of these potions meanwhile lowers the baseline,
				// so the collected one still shows up as an increase
				vesselBaseline = Math.min(vesselBaseline, count);
			}
			if (count > vesselBaseline)
			{
				collectedEarly = true;
			}
			vesselTracked = rawVessel;
			inTransit = null;
			return;
		}

		if (vesselTracked != 0)
		{
			// the vessel just emptied
			MixPotion was = MixPotion.fromVarbit(vesselTracked);
			if (!collectedEarly && was != null && unfinished.getOrDefault(was, 0) <= vesselBaseline)
			{
				inTransit = was;
				inTransitBaseline = vesselBaseline;
				inTransitUntilTick = tickCount + COLLECT_GRACE_TICKS;
			}
			vesselTracked = 0;
			collectedEarly = false;
		}
		if (inTransit != null
			&& (unfinished.getOrDefault(inTransit, 0) > inTransitBaseline || tickCount > inTransitUntilTick))
		{
			inTransit = null;
		}
	}

	/**
	 * The station currently holding a potion that is still being processed
	 * (0 = none). The retort is checked first because it needs constant
	 * clicking; you can only work one station at a time anyway.
	 */
	private int busyStation()
	{
		if (client.getVarbitValue(VarbitID.MM_LAB_RETORT_POTION) != 0)
		{
			return MixologyObjects.STATION_RETORT;
		}
		if (client.getVarbitValue(VarbitID.MM_LAB_AGITATOR_POTION) != 0)
		{
			return MixologyObjects.STATION_AGITATOR;
		}
		if (client.getVarbitValue(VarbitID.MM_LAB_ALEMBIC_POTION) != 0)
		{
			return MixologyObjects.STATION_ALEMBIC;
		}
		return 0;
	}

	/**
	 * Digweed: which herb spots have a pickable digweed (world arrow), and
	 * whether you hold the digweed together with its target potion
	 * (inventory arrows).
	 */
	private void updateDigweed()
	{
		// A spot is pickable when its varbit is exactly 1 OR its multiloc
		// impostor is the mature plant (varbit 0 shows the non-pickable
		// young plant, 2 shows nothing; cache-verified). The union
		// tolerates a wrong varbit->corner mapping; log flags disagreement.
		List<Integer> readyHerbs = new ArrayList<>();
		for (int i = 0; i < MixologyObjects.HERB_SPOTS.length; i++)
		{
			int herbId = MixologyObjects.HERB_SPOTS[i];
			if (labObjects.get(herbId) == null)
			{
				continue;
			}
			boolean varbitReady = client.getVarbitValue(HERB_VARBITS[i]) == 1;
			boolean impostorReady = herbImpostorPickable(herbId);
			if (varbitReady || impostorReady)
			{
				readyHerbs.add(herbId);
			}
			if (varbitReady != impostorReady && !warnedHerbSignals)
			{
				warnedHerbSignals = true;
				log.debug("herb spot {} signals disagree: varbit={} impostor={}",
					herbId, varbitReady, impostorReady);
			}
		}
		state.setReadyHerbObjectIds(readyHerbs);
		// a digweed just became pickable: flash the screen and say so in chat
		boolean anyReady = !readyHerbs.isEmpty();
		if (anyReady && !herbWasReady && state.isInLab())
		{
			if (config.digweedFlash())
			{
				state.setFlashStartMs(System.currentTimeMillis());
			}
			if (config.digweedChat())
			{
				announceDigweed();
			}
		}
		herbWasReady = anyReady;
		updateHintArrow(readyHerbs);

		state.setDigweedComboReady(digweedComboReady());
	}

	/**
	 * Holding the digweed together with the potion it should go on
	 * (unfinished or finished, the game takes either).
	 */
	private boolean digweedComboReady()
	{
		MixPotion target = state.getDigweedTarget();
		return target != null && inventoryItems.contains(ItemID.MM_LAB_SPECIAL_HERB)
			&& (inventoryItems.contains(target.getUnfinishedItemId())
			|| inventoryItems.contains(target.getFinishedItemId()));
	}

	/**
	 * Combining is a guidance step only when the potion is one of the orders
	 * being made, so the doubled reward actually gets deposited.
	 */
	private boolean digweedCombineDue()
	{
		if (!digweedComboReady())
		{
			return false;
		}
		MixPotion target = state.getDigweedTarget();
		for (MixologyState.Order o : state.getOrders())
		{
			if (o.isMake() && o.getPotion() == target)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Game message when a digweed spawns, naming the potion to use it on.
	 */
	private void announceDigweed()
	{
		MixPotion target = state.getDigweedTarget();
		String use = target == null ? "" : " Pick it up to use with your next " + target.getDisplayName() + ".";
		String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Digweed spawned.")
			.append(ChatColorType.NORMAL)
			.append(use)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	/**
	 * The game's own hint arrow (the yellow bouncing arrow, also on the
	 * minimap) over a ready digweed. Only ever clears an arrow this plugin
	 * set, so game or other plugins' arrows are left alone.
	 */
	private void updateHintArrow(List<Integer> readyHerbs)
	{
		WorldPoint target = null;
		if (config.digweedArrow() && state.isInLab())
		{
			for (int herbId : readyHerbs)
			{
				TileObject herb = labObjects.get(herbId);
				if (herb != null)
				{
					target = herb.getWorldLocation();
					break;
				}
			}
		}
		if (target != null)
		{
			if (!target.equals(hintArrowAt))
			{
				client.setHintArrow(target);
				hintArrowAt = target;
			}
		}
		else
		{
			clearHintArrow();
		}
	}

	private void clearHintArrow()
	{
		if (hintArrowAt != null)
		{
			client.clearHintArrow();
			hintArrowAt = null;
		}
	}

	private boolean herbImpostorPickable(int herbId)
	{
		ObjectComposition comp = client.getObjectDefinition(herbId);
		if (comp == null || comp.getImpostorIds() == null)
		{
			return false;
		}
		ObjectComposition impostor = comp.getImpostor();
		return impostor != null && impostor.getId() == MixologyObjects.HERB_MATURE;
	}

	private boolean inLab()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return false;
		}
		WorldPoint location = local.getWorldLocation();
		return location.getPlane() == 0 && location.getRegionID() == LAB_REGION;
	}

	private void recompute(int[] signals)
	{
		state.setMox(signals[6]);
		state.setAga(signals[7]);
		state.setLye(signals[8]);
		if (state.isInLab())
		{
			state.recordProgressSample(System.currentTimeMillis());
		}

		// collect the current orders
		List<MixPotion> potions = new ArrayList<>();
		List<Integer> modifiers = new ArrayList<>();
		for (int i = 0; i < 3; i++)
		{
			MixPotion potion = MixPotion.fromVarbit(signals[i]);
			if (potion != null)
			{
				potions.add(potion);
				modifiers.add(signals[3 + i]);
			}
		}

		// Best deposit SET: fulfilling 2 orders in one deposit gives +20%
		// resin, all 3 gives +40%, so evaluate every subset with its
		// bonus, not potions individually.
		// Both modes score a set as (what it earns) / (time it takes), with
		// time = potions made + one conveyor trip (DEPOSIT_OVERHEAD, in
		// potions' worth of time). The board rerolls on every deposit, so
		// the best rate on this board is what compounds.
		int n = potions.size();
		int bestMask = 0;
		double bestMetric = -1;
		double bestGain = 0;
		boolean maxXp = config.strategy() == MixologyConfig.Strategy.MAX_XP;
		boolean perPaste = config.strategy() == MixologyConfig.Strategy.SAVE_PASTES;
		for (int mask = 1; mask < (1 << n); mask++)
		{
			int count = Integer.bitCount(mask);
			double bonus = count == 3 ? 1.4 : count == 2 ? 1.2 : 1.0;
			int dm = 0;
			int da = 0;
			int dl = 0;
			int xp = 0;
			for (int i = 0; i < n; i++)
			{
				if ((mask & (1 << i)) != 0)
				{
					dm += potions.get(i).getMoxPoints();
					da += potions.get(i).getAgaPoints();
					dl += potions.get(i).getLyePoints();
					xp += potions.get(i).getXp() + stationBonusXp(modifiers.get(i));
				}
			}
			// Fastest points: how much of the points still needed for your
			// reward this deposit covers, with the set bonus applied.
			// Maximum XP: the Herblore XP it gives, station bonus included.
			double gain = state.rewardValue((int) (dm * bonus), (int) (da * bonus), (int) (dl * bonus));
			// Most points per paste: every potion uses three pastes, so it is
			// reward progress per potion made, with no time cost at all
			double metric = perPaste ? gain / count : (maxXp ? xp : gain) / (count + DEPOSIT_OVERHEAD);
			// tie-break: fastest points prefers fewer potions; the other
			// modes prefer whichever set also earns more reward progress
			boolean tie = Math.abs(metric - bestMetric) <= 1e-9 && bestMask != 0;
			boolean tieWins = maxXp || perPaste ? gain > bestGain + 1e-12 : count < Integer.bitCount(bestMask);
			if (metric > bestMetric + 1e-9 || (tie && tieWins))
			{
				bestMetric = metric;
				bestGain = gain;
				bestMask = mask;
			}
		}

		List<MixologyState.Order> scored = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			scored.add(new MixologyState.Order(potions.get(i), modifiers.get(i),
				state.rewardValue(potions.get(i).getMoxPoints(), potions.get(i).getAgaPoints(),
					potions.get(i).getLyePoints()), 0,
				(bestMask & (1 << i)) != 0));
		}
		Comparator<MixologyState.Order> byValue = maxXp
			? Comparator.comparingInt((MixologyState.Order o) -> o.getPotion().getXp()).reversed()
			: Comparator.comparingDouble(MixologyState.Order::getGain).reversed();
		scored.sort(Comparator.comparing(MixologyState.Order::isMake).reversed().thenComparing(byValue));
		List<MixologyState.Order> ranked = new ArrayList<>();
		for (int i = 0; i < scored.size(); i++)
		{
			MixologyState.Order o = scored.get(i);
			ranked.add(new MixologyState.Order(o.getPotion(), o.getModifier(), o.getGain(), i, o.isMake()));
		}
		state.setOrders(ranked);

		if (!ranked.isEmpty())
		{
			log.debug("mixology points: mox={} aga={} lye={} bundles={}",
				state.getMox(), state.getAga(), state.getLye(),
				String.format("%.3f", state.bundles()));
			for (MixologyState.Order o : ranked)
			{
				log.debug("mixology order rank {}: {} ({}) gain={} [raw type varbits {},{},{}]",
					o.getRank(), o.getPotion().getDisplayName(),
					MixPotion.modifierName(o.getModifier()),
					String.format("%.4f", o.getGain()),
					signals[0], signals[1], signals[2]);
			}
		}

		// digweed: the potion whose DOUBLED reward adds the most on top of
		// its base reward (ties -> highest total, which favours Mixalot)
		MixPotion bestTarget = null;
		double bestValue = -1;
		for (MixPotion p : MixPotion.values())
		{
			double bonus = state.bundlesFor(
				state.getMox() + 2 * p.getMoxPoints(),
				state.getAga() + 2 * p.getAgaPoints(),
				state.getLye() + 2 * p.getLyePoints())
				- state.bundlesFor(
				state.getMox() + p.getMoxPoints(),
				state.getAga() + p.getAgaPoints(),
				state.getLye() + p.getLyePoints());
			int total = p.getMoxPoints() + p.getAgaPoints() + p.getLyePoints();
			if (bonus > bestValue + 1e-12
				|| (Math.abs(bonus - bestValue) <= 1e-12 && bestTarget != null
				&& total > bestTarget.getMoxPoints() + bestTarget.getAgaPoints() + bestTarget.getLyePoints()))
			{
				bestValue = bonus;
				bestTarget = p;
			}
		}
		state.setDigweedTarget(bestTarget);
		if (bestTarget != null && !ranked.isEmpty())
		{
			log.debug("mixology digweed target: {}", bestTarget.getDisplayName());
		}
	}

	@Provides
	MixologyConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MixologyConfig.class);
	}
}
