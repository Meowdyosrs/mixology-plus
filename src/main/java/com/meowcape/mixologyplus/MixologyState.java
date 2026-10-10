package com.meowcape.mixologyplus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;

/**
 * Current mixology situation: points, the picked orders, and the lab
 * guidance the overlays draw. One "bundle" is one unit of the selected
 * reward; owned bundles are the minimum of the per-resin cost ratios,
 * which is what Fastest points mode maximises. Client thread only.
 */
@Singleton
public class MixologyState
{
	/**
	 * One order on the board.
	 */
	public static final class Order
	{
		private final MixPotion potion;
		private final int modifier;
		/**
		 * Reward units gained by depositing this potion alone.
		 */
		private final double gain;
		private final int rank;
		/**
		 * Part of the recommended deposit set.
		 */
		private final boolean make;

		public Order(MixPotion potion, int modifier, double gain, int rank, boolean make)
		{
			this.potion = potion;
			this.modifier = modifier;
			this.gain = gain;
			this.rank = rank;
			this.make = make;
		}

		public MixPotion getPotion()
		{
			return potion;
		}

		public int getModifier()
		{
			return modifier;
		}

		public double getGain()
		{
			return gain;
		}

		public int getRank()
		{
			return rank;
		}

		public boolean isMake()
		{
			return make;
		}
	}

	/**
	 * The single current action, in natural gameplay order.
	 */
	public enum LabAction
	{
		NONE, COLLECT_VESSEL, MIX, USE_STATION, ATTEND_STATION, USE_DIGWEED, DEPOSIT
	}

	private MixologyReward targetReward = MixologyReward.ALDARIUM;

	private int mox;
	private int aga;
	private int lye;

	private List<Order> orders = new ArrayList<>();
	private MixPotion digweedTarget;
	private boolean inLab;

	// ---- lab guidance ----
	private LabAction labAction = LabAction.NONE;
	private int actionStationId;
	private MixPotion mixTarget;
	/**
	 * Lever (0 mox, 1 aga, 2 lye) for each step of the mix target's
	 * recipe, in pull order; empty when nothing to mix.
	 */
	private int[] mixSteps = new int[0];
	/**
	 * Which of mixSteps are already in the mixer.
	 */
	private boolean[] mixStepDone = new boolean[0];
	private int quickActionId;
	/**
	 * The step after the current one, for the next-step preview.
	 */
	private LabAction nextAction = LabAction.NONE;
	private int nextStationId;
	/**
	 * The retort is working or is the next station to use.
	 */
	private boolean retortWanted;

	// ---- digweed ----
	private List<Integer> readyHerbObjectIds = new ArrayList<>();
	private boolean digweedComboReady;
	/**
	 * When the last digweed screen flash started (0 = never).
	 */
	private long flashStartMs;

	// ------------------------------------------------------------------
	// reward maths

	public void setTargetReward(MixologyReward reward)
	{
		if (reward != null && reward != targetReward)
		{
			targetReward = reward;
			// progress is measured in units of the reward, so a new reward
			// needs a fresh rate
			progressSamples.clear();
			minutesToNext = -1;
		}
	}

	// ------------------------------------------------------------------
	// ETA: (time, reward units owned) samples taken whenever points change

	/**
	 * Only the last half hour of play counts towards the rate.
	 */
	private static final long RATE_WINDOW_MS = 30 * 60_000L;
	/**
	 * No points for this long (banking, a break) starts the rate afresh.
	 */
	private static final long BREAK_MS = 5 * 60_000L;
	/**
	 * Wait for this much play and a few deposits before estimating.
	 */
	private static final long MIN_SPAN_MS = 90_000L;
	private static final int MIN_SAMPLES = 3;

	private final ArrayDeque<double[]> progressSamples = new ArrayDeque<>();
	private double minutesToNext = -1;

	public void recordProgressSample(long nowMs)
	{
		double owned = bundles();
		double[] last = progressSamples.peekLast();
		if (last != null && nowMs - last[0] > BREAK_MS)
		{
			progressSamples.clear();
			last = null;
		}
		if (last != null && Math.abs(last[1] - owned) < 1e-12)
		{
			return;
		}
		// spending points drops the total: start measuring again from here
		if (last != null && owned < last[1])
		{
			progressSamples.clear();
		}
		progressSamples.addLast(new double[]{nowMs, owned});
		while (progressSamples.size() > 2 && nowMs - progressSamples.peekFirst()[0] > RATE_WINDOW_MS)
		{
			progressSamples.removeFirst();
		}
		minutesToNext = estimateMinutesToNext();
	}

	/**
	 * Estimated minutes until the next unit of the reward, or -1 while
	 * there is not enough data yet. Worked out once per hand-in, not per
	 * frame; the overlay only reads it.
	 */
	public double getMinutesToNext()
	{
		return minutesToNext;
	}

	private double estimateMinutesToNext()
	{
		if (progressSamples.size() < MIN_SAMPLES)
		{
			return -1;
		}
		double[] first = progressSamples.peekFirst();
		double[] last = progressSamples.peekLast();
		long span = (long) (last[0] - first[0]);
		double gained = last[1] - first[1];
		if (span < MIN_SPAN_MS || gained <= 0)
		{
			return -1;
		}
		double perMinute = gained / (span / 60_000.0);
		double needed = (buyableNow() + 1) - bundles();
		return needed / perMinute;
	}

	public double bundles()
	{
		return bundlesFor(mox, aga, lye);
	}

	public double bundlesFor(int mox, int aga, int lye)
	{
		return Math.min(mox / (double) targetReward.getMoxCost(),
			Math.min(aga / (double) targetReward.getAgaCost(),
				lye / (double) targetReward.getLyeCost()));
	}

	public MixologyReward getTargetReward()
	{
		return targetReward;
	}

	/**
	 * How many of the target reward you can buy right now.
	 */
	public int buyableNow()
	{
		return (int) Math.floor(bundles() + 1e-9);
	}

	/**
	 * Whole-number progress (0-99) towards the next unit of the target
	 * reward: the progress of whichever resin is furthest behind. Rounded
	 * down so it never shows 100% before you can actually buy it.
	 */
	public int percentToNext()
	{
		int pct = (int) Math.floor((bundles() - buyableNow()) * 100.0 + 1e-9);
		return Math.max(0, Math.min(99, pct));
	}

	/**
	 * Points still to come in any resin after the next reward is covered
	 * count at this weight: they go towards the reward after that, but are
	 * not what is holding you back right now.
	 */
	private static final double SURPLUS_WEIGHT = 0.25;

	/**
	 * How much these points move you towards your reward, as a fraction of
	 * one reward's full price (1.0 = enough of every resin for one).
	 * Points of each resin count in full up to what you still need for the
	 * next one, so every resin you are short of is valued, not just the
	 * scarcest; anything beyond that counts at SURPLUS_WEIGHT, up to one
	 * more reward's worth.
	 */
	public double rewardValue(int moxPts, int agaPts, int lyePts)
	{
		int unit = buyableNow() + 1;
		return (resinValue(mox, moxPts, targetReward.getMoxCost(), unit)
			+ resinValue(aga, agaPts, targetReward.getAgaCost(), unit)
			+ resinValue(lye, lyePts, targetReward.getLyeCost(), unit)) / 3.0;
	}

	private static double resinValue(int have, int pts, int cost, int unit)
	{
		int need = Math.max(0, unit * cost - have);
		int now = Math.min(pts, need);
		int later = Math.min(pts - now, cost);
		return (now + SURPLUS_WEIGHT * later) / (double) cost;
	}

	// ------------------------------------------------------------------
	// accessors

	public int getMox()
	{
		return mox;
	}

	public void setMox(int mox)
	{
		this.mox = mox;
	}

	public int getAga()
	{
		return aga;
	}

	public void setAga(int aga)
	{
		this.aga = aga;
	}

	public int getLye()
	{
		return lye;
	}

	public void setLye(int lye)
	{
		this.lye = lye;
	}

	public List<Order> getOrders()
	{
		return orders;
	}

	public void setOrders(List<Order> orders)
	{
		this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
	}

	public MixPotion getDigweedTarget()
	{
		return digweedTarget;
	}

	public void setDigweedTarget(MixPotion digweedTarget)
	{
		this.digweedTarget = digweedTarget;
	}

	public boolean isInLab()
	{
		return inLab;
	}

	public void setInLab(boolean inLab)
	{
		this.inLab = inLab;
	}

	public LabAction getLabAction()
	{
		return labAction;
	}

	public void setLabAction(LabAction labAction)
	{
		this.labAction = labAction;
	}

	public int getActionStationId()
	{
		return actionStationId;
	}

	public void setActionStationId(int actionStationId)
	{
		this.actionStationId = actionStationId;
	}

	public MixPotion getMixTarget()
	{
		return mixTarget;
	}

	public void setMixTarget(MixPotion mixTarget)
	{
		this.mixTarget = mixTarget;
	}

	public int[] getMixSteps()
	{
		return mixSteps;
	}

	public boolean[] getMixStepDone()
	{
		return mixStepDone;
	}

	public void setMixSteps(int[] mixSteps, boolean[] mixStepDone)
	{
		this.mixSteps = mixSteps;
		this.mixStepDone = mixStepDone;
	}

	/**
	 * Lever to pull next (0 mox, 1 aga, 2 lye), or -1.
	 */
	public int nextLever()
	{
		for (int i = 0; i < mixSteps.length; i++)
		{
			if (!mixStepDone[i])
			{
				return mixSteps[i];
			}
		}
		return -1;
	}

	public int getQuickActionId()
	{
		return quickActionId;
	}

	public void setQuickActionId(int quickActionId)
	{
		this.quickActionId = quickActionId;
	}

	public LabAction getNextAction()
	{
		return nextAction;
	}

	public void setNextAction(LabAction nextAction)
	{
		this.nextAction = nextAction;
	}

	public int getNextStationId()
	{
		return nextStationId;
	}

	public void setNextStationId(int nextStationId)
	{
		this.nextStationId = nextStationId;
	}

	public boolean isRetortWanted()
	{
		return retortWanted;
	}

	public void setRetortWanted(boolean retortWanted)
	{
		this.retortWanted = retortWanted;
	}

	public List<Integer> getReadyHerbObjectIds()
	{
		return readyHerbObjectIds;
	}

	public void setReadyHerbObjectIds(List<Integer> readyHerbObjectIds)
	{
		this.readyHerbObjectIds = readyHerbObjectIds;
	}

	public long getFlashStartMs()
	{
		return flashStartMs;
	}

	public void setFlashStartMs(long flashStartMs)
	{
		this.flashStartMs = flashStartMs;
	}

	public boolean isDigweedComboReady()
	{
		return digweedComboReady;
	}

	public void setDigweedComboReady(boolean digweedComboReady)
	{
		this.digweedComboReady = digweedComboReady;
	}
}
