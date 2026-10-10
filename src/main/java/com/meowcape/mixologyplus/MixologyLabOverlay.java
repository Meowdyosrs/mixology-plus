package com.meowcape.mixologyplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * In-world lab guidance: lever outlines with remaining pull counts for
 * the next mix, the target station, the quick-action flash, and the
 * ready vessel.
 */
@Singleton
public class MixologyLabOverlay extends Overlay
{
	private static final int LABEL_HEIGHT = 120;
	private static final int PLAYER_LABEL_HEIGHT = 240;
	private static final int STATION_LABEL_HEIGHT = 300;
	private static final int SAME_STATION_EXTRA_HEIGHT = 80;

	private final Client client;
	private final MixologyState state;
	private final MixologyConfig config;
	private final MixologyObjects objects;
	private final ModelOutlineRenderer outliner;

	@Inject
	private MixologyLabOverlay(Client client, MixologyState state, MixologyConfig config,
		MixologyObjects objects, ModelOutlineRenderer outliner)
	{
		this.client = client;
		this.state = state;
		this.config = config;
		this.objects = objects;
		this.outliner = outliner;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!state.isInLab())
		{
			return null;
		}

		// pickable digweed despawns in ~20s, so it draws alongside
		// whatever else is on screen, never queued behind it
		if (config.digweedHighlight())
		{
			for (int herbId : state.getReadyHerbObjectIds())
			{
				TileObject herb = objects.get(herbId);
				if (herb != null)
				{
					outliner.drawOutline(herb, 3, config.digweedColor(), 2);
					Color c = config.digweedColor();
					drawLabel(graphics, herb, "Collect", new Color(c.getRed(), c.getGreen(), c.getBlue()));
				}
			}
		}

		// the quick-action window is a two-tick reflex cue: while open it
		// IS the action, overriding whatever the queue was showing
		if (config.guideQuickAction() && state.getQuickActionId() != 0)
		{
			TileObject station = objects.get(state.getQuickActionId());
			if (station != null)
			{
				outliner.drawOutline(station, 5, config.quickActionColor(), 2);
				drawLabel(graphics, station, "Click!", config.quickActionColor(), STATION_LABEL_HEIGHT);
				return null;
			}
		}

		if (config.showNextStep())
		{
			drawNextStep(graphics);
		}

		// otherwise exactly ONE action on screen, in gameplay order
		switch (state.getLabAction())
		{
			case COLLECT_VESSEL:
			{
				TileObject vessel = objects.get(MixologyObjects.VESSEL);
				if (vessel != null)
				{
					outliner.drawOutline(vessel, 3, config.vesselColor(), 2);
					drawLabel(graphics, vessel, "Collect", config.vesselColor());
				}
				break;
			}
			case MIX:
			{
				if (config.guideLevers())
				{
					int next = state.nextLever();
					drawLever(graphics, MixologyObjects.LEVER_MOX, 0, next, config.moxColor());
					drawLever(graphics, MixologyObjects.LEVER_AGA, 1, next, config.agaColor());
					drawLever(graphics, MixologyObjects.LEVER_LYE, 2, next, config.lyeColor());
				}
				break;
			}
			case USE_STATION:
			{
				if (config.guideStations())
				{
					TileObject station = objects.get(state.getActionStationId());
					if (station != null)
					{
						outliner.drawOutline(station, 3, config.stationColor(), 2);
						drawLabel(graphics, station, "Insert", config.stationColor(), STATION_LABEL_HEIGHT);
					}
				}
				break;
			}
			case ATTEND_STATION:
			{
				if (config.guideStations())
				{
					TileObject station = objects.get(state.getActionStationId());
					if (station != null)
					{
						if (state.getActionStationId() == MixologyObjects.STATION_RETORT)
						{
							// the retort speeds up with every click, so keep
							// clicking it while it works; a fast blink says
							// "keep going" rather than "click once"
							boolean on = (System.currentTimeMillis() / 300) % 2 == 0;
							Color spam = config.spamClickColor();
							outliner.drawOutline(station, on ? 5 : 3, spam, 2);
							drawLabel(graphics, station, "Spam click!", on ? spam : spam.darker(), STATION_LABEL_HEIGHT);
						}
						else
						{
							// agitator / alembic: stay here until it finishes;
							// the quick-action flash takes over in click windows
							outliner.drawOutline(station, 3, config.stationColor(), 2);
							drawLabel(graphics, station, "Working...", config.stationColor(), STATION_LABEL_HEIGHT);
						}
					}
				}
				break;
			}
			case USE_DIGWEED:
			{
				// the step happens in the inventory (both items are outlined
				// there); say it above the player so it can't be missed
				MixPotion target = state.getDigweedTarget();
				Player player = client.getLocalPlayer();
				if (target != null && player != null && player.getLocalLocation() != null)
				{
					Color c = config.digweedColor();
					String text = "Use digweed on " + target.getDisplayName();
					graphics.setFont(OverlayFonts.bold(config.overlayScale()));
					OverlayFonts.applyTextHints(graphics, config.overlayScale());
					Point at = Perspective.getCanvasTextLocation(client, graphics, player.getLocalLocation(), text, PLAYER_LABEL_HEIGHT);
					if (at != null)
					{
						graphics.setColor(Color.BLACK);
						graphics.drawString(text, at.getX() + 1, at.getY() + 1);
						graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
						graphics.drawString(text, at.getX(), at.getY());
					}
				}
				break;
			}
			case DEPOSIT:
			{
				// the belt is one object id repeated per segment; the one
				// you deposit at is the segment nearest to you
				Player player = client.getLocalPlayer();
				TileObject conveyor = objects.nearest(MixologyObjects.CONVEYOR,
					player == null ? null : player.getLocalLocation());
				if (conveyor != null)
				{
					outliner.drawOutline(conveyor, 3, config.depositColor(), 2);
					drawLabel(graphics, conveyor, "Deposit", config.depositColor());
				}
				break;
			}
			default:
		}
		return null;
	}

	/**
	 * Labels a lever with the steps it is still used for, keeping their
	 * place in the recipe ("2nd", "1st, 2nd"), or a countdown ("3x", "2x",
	 * "1x") for single-ingredient potions. The lever to
	 * pull next gets a thick, pulsing outline; levers needed later are
	 * drawn in a dimmer colour.
	 */
	private void drawLever(Graphics2D graphics, int objectId, int lever, int nextLever, Color color)
	{
		int[] steps = state.getMixSteps();
		boolean[] done = state.getMixStepDone();
		List<Integer> mine = new ArrayList<>();
		for (int i = 0; i < steps.length; i++)
		{
			if (steps[i] == lever && !done[i])
			{
				mine.add(i + 1);
			}
		}
		if (mine.isEmpty())
		{
			return;
		}
		// single-ingredient potions (all Mox, Aga or Lye) just count down:
		// 3x, 2x, 1x; everything else names the steps in order
		MixPotion target = state.getMixTarget();
		String code = target == null ? "" : target.getCode();
		boolean singleIngredient = code.length() == 3 && code.chars().distinct().count() == 1;
		String order = singleIngredient ? mine.size() + "x" : stepLabel(mine);
		TileObject object = objects.get(objectId);
		if (object == null)
		{
			return;
		}
		if (lever == nextLever)
		{
			// gentle pulse so the next lever reads at a glance
			double phase = (System.currentTimeMillis() % 1000) / 1000.0;
			int alpha = 170 + (int) (85 * Math.abs(Math.sin(phase * Math.PI)));
			Color pulse = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
			outliner.drawOutline(object, 6, pulse, 2);
			drawLabel(graphics, object, order, color);
		}
		else
		{
			Color faded = new Color(color.getRed(), color.getGreen(), color.getBlue(), 110);
			outliner.drawOutline(object, 2, faded, 2);
			drawLabel(graphics, object, order, dim(color));
		}
	}

	/**
	 * An opaque, greyed version of a lever colour for "later" labels.
	 */
	private static Color dim(Color c)
	{
		return new Color((c.getRed() + 160) / 2, (c.getGreen() + 160) / 2, (c.getBlue() + 160) / 2);
	}

	/**
	 * A faint outline and "Next: ..." label on where the step after the
	 * current one happens. Drawn before the current step so the current
	 * highlight always sits on top.
	 */
	private void drawNextStep(Graphics2D graphics)
	{
		TileObject target;
		String text;
		switch (state.getNextAction())
		{
			case COLLECT_VESSEL:
				target = objects.get(MixologyObjects.VESSEL);
				text = "Next: Collect";
				break;
			case MIX:
			{
				int lever = state.nextLever();
				target = objects.get(lever == 1 ? MixologyObjects.LEVER_AGA
					: lever == 2 ? MixologyObjects.LEVER_LYE : MixologyObjects.LEVER_MOX);
				text = "Next: Levers";
				break;
			}
			case USE_STATION:
				target = objects.get(state.getNextStationId());
				text = "Next: Insert";
				break;
			case DEPOSIT:
			{
				Player player = client.getLocalPlayer();
				target = objects.nearest(MixologyObjects.CONVEYOR,
					player == null ? null : player.getLocalLocation());
				text = "Next: Deposit";
				break;
			}
			default:
				return;
		}
		if (target == null)
		{
			return;
		}
		Color c = config.nextStepColor();
		outliner.drawOutline(target, 2, c, 2);
		// labels stay opaque: see-through pixel text looks muddy
		Color label = new Color(c.getRed(), c.getGreen(), c.getBlue());
		boolean station = state.getNextAction() == MixologyState.LabAction.USE_STATION;
		int height = station ? STATION_LABEL_HEIGHT : LABEL_HEIGHT;
		// same station as the current step (a second potion for it): stack
		// the "Next:" label above the current one instead of on top of it
		if (station && state.getNextStationId() == state.getActionStationId())
		{
			height += SAME_STATION_EXTRA_HEIGHT;
		}
		drawLabel(graphics, target, text, dim(label), height);
	}

	/**
	 * Step numbers as one readable label, e.g. "3rd" or "1st, 2nd".
	 */
	static String stepLabel(List<Integer> stepNumbers)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < stepNumbers.size(); i++)
		{
			if (i > 0)
			{
				sb.append(", ");
			}
			sb.append(ordinal(stepNumbers.get(i)));
		}
		return sb.toString();
	}

	private static String ordinal(int n)
	{
		return n == 1 ? "1st" : n == 2 ? "2nd" : n == 3 ? "3rd" : n + "th";
	}

	private void drawLabel(Graphics2D graphics, TileObject object, String text, Color color)
	{
		drawLabel(graphics, object, text, color, LABEL_HEIGHT);
	}

	/**
	 * @param height how far above the ground to place the text, in local
	 * units; stations use a taller value so the text clears the game's own
	 * progress bar instead of sitting on top of it
	 */
	private void drawLabel(Graphics2D graphics, TileObject object, String text, Color color, int height)
	{
		// set the font first: the label is centred using its metrics
		graphics.setFont(OverlayFonts.bold(config.overlayScale()));
		OverlayFonts.applyTextHints(graphics, config.overlayScale());
		Point location = Perspective.getCanvasTextLocation(
			client, graphics, object.getLocalLocation(), text, height);
		if (location == null)
		{
			return;
		}
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, location.getX() + 1, location.getY() + 1);
		graphics.setColor(color);
		graphics.drawString(text, location.getX(), location.getY());
	}
}
