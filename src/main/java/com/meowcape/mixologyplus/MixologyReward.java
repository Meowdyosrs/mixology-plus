package com.meowcape.mixologyplus;

/**
 * Mixology Rewards shop items with their mox/aga/lye prices (wiki). The
 * selected reward's price triple drives the points strategy and every
 * progress figure.
 */
public enum MixologyReward
{
	ALDARIUM("Aldarium", "Aldarium", 80, 60, 90),
	APPRENTICE_PACK("Apprentice potion pack", "Appr. pack", 420, 70, 30),
	ADEPT_PACK("Adept potion pack", "Adept pack", 180, 440, 70),
	EXPERT_PACK("Expert potion pack", "Exp. pack", 410, 320, 480),
	PRESCRIPTION_GOGGLES("Prescription goggles", "Goggles", 8600, 7000, 9350),
	ALCHEMIST_LABCOAT("Alchemist labcoat", "Labcoat", 2250, 2800, 3700),
	ALCHEMIST_PANTS("Alchemist pants", "Pants", 2250, 2800, 3700),
	ALCHEMIST_GLOVES("Alchemist gloves", "Gloves", 2250, 2800, 3700),
	ALCHEMISTS_AMULET("Alchemist's amulet", "Amulet", 6900, 5650, 7400),
	POTION_STORAGE("Potion storage", "Storage", 7750, 6300, 8950),
	REAGENT_POUCH("Reagent pouch", "Pouch", 13800, 11200, 15100),
	CHUGGING_BARREL("Chugging barrel", "Barrel", 17250, 14000, 18600);

	private final String displayName;
	private final String shortName;
	private final int moxCost;
	private final int agaCost;
	private final int lyeCost;

	MixologyReward(String displayName, String shortName, int moxCost, int agaCost, int lyeCost)
	{
		this.displayName = displayName;
		this.shortName = shortName;
		this.moxCost = moxCost;
		this.agaCost = agaCost;
		this.lyeCost = lyeCost;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getShortName()
	{
		return shortName;
	}

	public int getMoxCost()
	{
		return moxCost;
	}

	public int getAgaCost()
	{
		return agaCost;
	}

	public int getLyeCost()
	{
		return lyeCost;
	}

	/**
	 * Cost by resin index (0 mox, 1 aga, 2 lye).
	 */
	public int cost(int resin)
	{
		return resin == 0 ? moxCost : resin == 1 ? agaCost : lyeCost;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
