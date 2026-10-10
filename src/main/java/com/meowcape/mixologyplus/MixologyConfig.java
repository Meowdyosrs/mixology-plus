package com.meowcape.mixologyplus;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(MixologyConfig.GROUP)
public interface MixologyConfig extends Config
{
	String GROUP = "mixologyplus";

	enum Strategy
	{
		FASTEST_POINTS("Fastest points"),
		SAVE_PASTES("Most points per paste"),
		MAX_XP("Maximum XP");

		private final String label;

		Strategy(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "strategy",
		name = "Mode",
		description = "Fastest points: the orders that get you to your target reward quickest. "
			+ "Most points per paste: the orders that get the most out of your pastes, ignoring time (skips weak potions more often). "
			+ "Maximum XP: the orders that give the most Herblore XP for your time",
		position = 0
	)
	default Strategy strategy()
	{
		return Strategy.FASTEST_POINTS;
	}

	@ConfigItem(
		keyName = "targetReward",
		name = "Target reward",
		description = "The shop reward you are saving for. Fastest points mode picks orders to suit its mox/aga/lye price",
		position = 1
	)
	default MixologyReward targetReward()
	{
		return MixologyReward.ALDARIUM;
	}

	@Range(min = 75, max = 250)
	@ConfigItem(
		keyName = "overlayScale",
		name = "Label size %",
		description = "Size of the in-world labels (100 = default, sharpest at 100 or 200)",
		position = 2
	)
	default int overlayScale()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "guideLevers",
		name = "Lever guidance",
		description = "Number the levers in pull order (1st, 2nd, 3rd) and make the next one to pull stand out",
		position = 3
	)
	default boolean guideLevers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guideStations",
		name = "Station guidance",
		description = "Highlight the station needed by the next unprocessed potion in your inventory",
		position = 4
	)
	default boolean guideStations()
	{
		return true;
	}

	@ConfigItem(
		keyName = "protectRetort",
		name = "Retort misclick guard",
		description = "When the Retort isn't the station you need, its left-click becomes Walk here, so spam-clicking after it finishes "
			+ "can't put your next potion in it. Right-click still works",
		position = 5
	)
	default boolean protectRetort()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guideQuickAction",
		name = "Quick-action flash",
		description = "Flash the agitator/alembic green when the speed-boost click window is open",
		position = 6
	)
	default boolean guideQuickAction()
	{
		return true;
	}

	@ConfigItem(
		keyName = "digweedHighlight",
		name = "Digweed highlight",
		description = "Outline a digweed plant with \"Collect\" above it while it is ready to pick",
		position = 7
	)
	default boolean digweedHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "digweedArrow",
		name = "Digweed waypoint arrow",
		description = "Point the game's hint arrow (also shown on the minimap) at a digweed while it is ready to pick",
		position = 8
	)
	default boolean digweedArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "digweedChat",
		name = "Digweed chat message",
		description = "Post a game message when a digweed spawns, saying which potion to use it on",
		position = 9
	)
	default boolean digweedChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "digweedFlash",
		name = "Digweed screen flash",
		description = "Briefly flash the screen yellow when a digweed is ready to pick",
		position = 10
	)
	default boolean digweedFlash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "digweedUseHighlight",
		name = "Digweed use highlight",
		description = "When you hold the digweed and the potion it should go on, outline both in your inventory so you can use one on the other",
		position = 11
	)
	default boolean digweedUseHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showProgress",
		name = "Show reward progress",
		description = "A small box showing how far you are towards your target reward, e.g. \"Aldarium 22%\"",
		position = 12
	)
	default boolean showProgress()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNextStep",
		name = "Show next step",
		description = "Faintly outline where you will go after the current step, with a \"Next:\" label, so you can head there early",
		position = 13
	)
	default boolean showNextStep()
	{
		return true;
	}

	@ConfigSection(
		name = "Colours",
		description = "Colours of the in-world highlights and their labels",
		position = 20,
		closedByDefault = true
	)
	String COLOURS = "colours";

	@Alpha
	@ConfigItem(keyName = "moxColor", name = "Mox lever", description = "Mox lever highlight", section = COLOURS, position = 21)
	default Color moxColor()
	{
		return new Color(90, 130, 255);
	}

	@Alpha
	@ConfigItem(keyName = "agaColor", name = "Aga lever", description = "Aga lever highlight", section = COLOURS, position = 22)
	default Color agaColor()
	{
		return new Color(60, 200, 80);
	}

	@Alpha
	@ConfigItem(keyName = "lyeColor", name = "Lye lever", description = "Lye lever highlight", section = COLOURS, position = 23)
	default Color lyeColor()
	{
		return new Color(235, 70, 70);
	}

	@Alpha
	@ConfigItem(keyName = "vesselColor", name = "Collect (vessel)", description = "Mixing vessel highlight when a mix is ready to collect", section = COLOURS, position = 24)
	default Color vesselColor()
	{
		return Color.WHITE;
	}

	@Alpha
	@ConfigItem(keyName = "stationColor", name = "Station", description = "Station to insert the potion into, and stations working on their own", section = COLOURS, position = 25)
	default Color stationColor()
	{
		return new Color(255, 0, 255);
	}

	@Alpha
	@ConfigItem(keyName = "spamClickColor", name = "Retort spam click", description = "Retort highlight while it needs clicking", section = COLOURS, position = 26)
	default Color spamClickColor()
	{
		return new Color(0, 255, 60);
	}

	@Alpha
	@ConfigItem(keyName = "quickActionColor", name = "Quick-action click", description = "Agitator / alembic flash during the speed-boost click window", section = COLOURS, position = 27)
	default Color quickActionColor()
	{
		return new Color(0, 255, 60);
	}

	@Alpha
	@ConfigItem(keyName = "depositColor", name = "Deposit", description = "Conveyor belt highlight when it's time to deposit", section = COLOURS, position = 28)
	default Color depositColor()
	{
		return new Color(0, 255, 60);
	}

	@Alpha
	@ConfigItem(keyName = "nextStepColor", name = "Next step", description = "Faint outline and label for the next-step preview", section = COLOURS, position = 30)
	default Color nextStepColor()
	{
		return new Color(255, 255, 255, 130);
	}

	@Alpha
	@ConfigItem(keyName = "digweedColor", name = "Digweed", description = "Digweed plant outline, the screen flash, and the digweed / potion outline in your inventory", section = COLOURS, position = 29)
	default Color digweedColor()
	{
		return new Color(255, 220, 60);
	}
}
