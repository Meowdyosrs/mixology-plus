package com.meowcape.mixologyplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Works out which levers to pull, in order, to get a recipe into the
 * mixer.
 *
 * The mixer holds up to three pastes, filling slot 0, 1, 2 in order, and
 * the potion is decided by which three pastes it holds, in any order. When
 * it is already full, a new paste pushes the OLDEST one out. So leftovers
 * from an earlier mix can't simply be skipped as "already done": whether a
 * leftover survives depends on how many pulls come after it. The planner
 * simulates the mixer and returns the shortest sequence that ends with
 * exactly the recipe.
 */
final class LeverPlanner
{
	private LeverPlanner()
	{
	}

	/**
	 * @param slots  mixer slot values, oldest first: 0 empty, 1 mox, 2 aga,
	 *               3 lye
	 * @param recipe recipe code such as "MAL"
	 * @return levers to pull in order (0 mox, 1 aga, 2 lye); empty when
	 * the mixer already holds the recipe
	 */
	static int[] plan(int[] slots, String recipe)
	{
		List<Integer> mixer = new ArrayList<>();
		for (int v : slots)
		{
			if (v >= 1 && v <= 3)
			{
				mixer.add(v - 1);
			}
		}
		int[] want = counts(recipe);

		// the natural plan first: keep what fits, pull the rest in recipe
		// order; used whenever it really works out, so a clean mixer always
		// gets the familiar order
		int[] natural = naturalPlan(mixer, recipe);
		if (natural != null && matches(simulate(mixer, natural), want))
		{
			return natural;
		}

		// otherwise the shortest sequence that ends with the recipe; three
		// pulls always suffice, since they replace everything
		for (int len = 1; len <= 3; len++)
		{
			int[] found = search(mixer, want, new int[len], 0, recipe);
			if (found != null)
			{
				return found;
			}
		}
		return natural != null ? natural : new int[0];
	}

	/**
	 * Recipe pastes not already covered by what is in the mixer, in recipe
	 * order; null if the mixer has no room for them without pushing pastes
	 * out.
	 */
	private static int[] naturalPlan(List<Integer> mixer, String recipe)
	{
		int[] have = new int[3];
		for (int c : mixer)
		{
			have[c]++;
		}
		List<Integer> pulls = new ArrayList<>();
		for (char ch : recipe.toCharArray())
		{
			int c = MixPotion.componentIndex(ch);
			if (have[c] > 0)
			{
				have[c]--;
			}
			else
			{
				pulls.add(c);
			}
		}
		if (mixer.size() + pulls.size() > 3)
		{
			return null;
		}
		return pulls.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Depth-first search over pull sequences of a fixed length. Levers are
	 * tried in recipe order, so among equally short plans the one closest
	 * to the recipe's own order wins.
	 */
	private static int[] search(List<Integer> mixer, int[] want, int[] seq, int pos, String recipe)
	{
		if (pos == seq.length)
		{
			return matches(simulate(mixer, seq), want) ? seq.clone() : null;
		}
		for (int lever : leverOrder(recipe))
		{
			seq[pos] = lever;
			int[] found = search(mixer, want, seq, pos + 1, recipe);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	/**
	 * The recipe's levers first (in recipe order), then any others.
	 */
	private static int[] leverOrder(String recipe)
	{
		List<Integer> order = new ArrayList<>();
		for (char ch : recipe.toCharArray())
		{
			int c = MixPotion.componentIndex(ch);
			if (!order.contains(c))
			{
				order.add(c);
			}
		}
		for (int c = 0; c < 3; c++)
		{
			if (!order.contains(c))
			{
				order.add(c);
			}
		}
		return order.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * The mixer after these pulls: each paste goes on the end, and when it
	 * is already full the oldest drops out.
	 */
	static List<Integer> simulate(List<Integer> mixer, int[] pulls)
	{
		List<Integer> out = new ArrayList<>(mixer);
		for (int lever : pulls)
		{
			if (out.size() == 3)
			{
				out.remove(0);
			}
			out.add(lever);
		}
		return out;
	}

	private static boolean matches(List<Integer> mixer, int[] want)
	{
		if (mixer.size() != 3)
		{
			return false;
		}
		int[] have = new int[3];
		for (int c : mixer)
		{
			have[c]++;
		}
		return Arrays.equals(have, want);
	}

	private static int[] counts(String recipe)
	{
		int[] c = new int[3];
		for (char ch : recipe.toCharArray())
		{
			c[MixPotion.componentIndex(ch)]++;
		}
		return c;
	}
}
