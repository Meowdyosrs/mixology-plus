package com.meowcape.mixologyplus;

/**
 * The ten Mastering Mixology potions, declared in ORDER VARBIT VALUE order
 * (varbit value = ordinal + 1). WARNING: this order does NOT match item-id
 * order for AAM/AAL and LLM/LLA.
 *
 * Points are the ordered hand-in rewards (mox/aga/lye) and xp is the
 * Herblore experience for depositing it as an order, both from the wiki.
 * The code is the recipe in the order the plugin asks you to pull the
 * levers (same-lever pulls are always next to each other).
 */
public enum MixPotion
{
	MMM("Mammoth-might mix", "MMM", 60, 190, 20, 0, 0, 30011, 30021),
	MMA("Mystic mana amalgam", "MMA", 63, 215, 20, 10, 0, 30012, 30022),
	MML("Marley's moonlight", "MML", 66, 240, 20, 0, 10, 30013, 30023),
	AAA("Alco-augmentator", "AAA", 60, 190, 0, 20, 0, 30014, 30024),
	AAM("Azure aura mix", "AAM", 69, 265, 10, 20, 0, 30016, 30026),
	AAL("Aqualux amalgam", "AAL", 72, 290, 0, 20, 10, 30015, 30025),
	LLL("Liplack liquor", "LLL", 60, 190, 0, 0, 20, 30017, 30027),
	LLM("Megalite liquid", "LLM", 75, 315, 10, 0, 20, 30019, 30029),
	LLA("Anti-leech lotion", "LLA", 78, 340, 0, 10, 20, 30018, 30028),
	MAL("Mixalot", "MAL", 81, 365, 20, 20, 20, 30020, 30030);

	private final String displayName;
	private final String code;
	private final int level;
	private final int xp;
	private final int moxPoints;
	private final int agaPoints;
	private final int lyePoints;
	private final int unfinishedItemId;
	private final int finishedItemId;

	MixPotion(String displayName, String code, int level, int xp, int moxPoints, int agaPoints,
		int lyePoints, int unfinishedItemId, int finishedItemId)
	{
		this.displayName = displayName;
		this.code = code;
		this.level = level;
		this.xp = xp;
		this.moxPoints = moxPoints;
		this.agaPoints = agaPoints;
		this.lyePoints = lyePoints;
		this.unfinishedItemId = unfinishedItemId;
		this.finishedItemId = finishedItemId;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getCode()
	{
		return code;
	}

	public int getLevel()
	{
		return level;
	}

	public int getXp()
	{
		return xp;
	}

	public int getMoxPoints()
	{
		return moxPoints;
	}

	public int getAgaPoints()
	{
		return agaPoints;
	}

	public int getLyePoints()
	{
		return lyePoints;
	}

	public int getUnfinishedItemId()
	{
		return unfinishedItemId;
	}

	public int getFinishedItemId()
	{
		return finishedItemId;
	}

	/**
	 * @param varbitValue the order TYPE varbit value (1-10); 0 = no order
	 */
	public static MixPotion fromVarbit(int varbitValue)
	{
		int idx = varbitValue - 1;
		return idx >= 0 && idx < values().length ? values()[idx] : null;
	}

	/**
	 * Lever / resin index for a recipe letter: 0 mox, 1 aga, 2 lye.
	 */
	public static int componentIndex(char c)
	{
		return c == 'M' ? 0 : c == 'A' ? 1 : 2;
	}

	public static String modifierName(int modifierVarbitValue)
	{
		switch (modifierVarbitValue)
		{
			case 1:
				return "Agitator";
			case 2:
				return "Retort";
			case 3:
				return "Alembic";
			default:
				return "?";
		}
	}
}
