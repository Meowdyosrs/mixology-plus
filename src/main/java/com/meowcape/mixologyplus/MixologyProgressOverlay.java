package com.meowcape.mixologyplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small box with progress towards the next unit of the target reward,
 * e.g. "Aldarium 22%" over a thin bar, with a rough time estimate
 * underneath from the rate your points have been coming in. Movable like any RuneLite overlay
 * (hold Alt and drag).
 */
@Singleton
public class MixologyProgressOverlay extends Overlay
{
	private static final Color BG = new Color(20, 24, 20, 200);
	private static final Color BAR_BG = new Color(60, 60, 60);
	private static final Color BAR_FILL = new Color(40, 180, 80);
	private static final Color ETA_TEXT = new Color(200, 200, 200);
	private static final int PAD = 5;
	private static final int BAR_H = 4;

	private final MixologyState state;
	private final MixologyConfig config;

	@Inject
	private MixologyProgressOverlay(MixologyState state, MixologyConfig config)
	{
		this.state = state;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showProgress() || !state.isInLab())
		{
			return null;
		}
		int pct = state.percentToNext();
		String text = state.getTargetReward().getDisplayName() + " " + pct + "%";
		int ready = state.buyableNow();
		if (ready > 0)
		{
			text += "  (" + ready + " ready)";
		}

		String eta = etaText(state.getMinutesToNext());

		OverlayFonts.applyTextHints(g, 100);
		g.setFont(OverlayFonts.small(100));
		FontMetrics sfm = g.getFontMetrics();
		g.setFont(OverlayFonts.regular(100));
		FontMetrics fm = g.getFontMetrics();
		int width = Math.max(fm.stringWidth(text), sfm.stringWidth(eta)) + PAD * 2;
		int textH = fm.getAscent();
		int etaH = sfm.getAscent();
		int height = PAD + textH + 3 + BAR_H + 3 + etaH + PAD;

		g.setColor(BG);
		g.fillRect(0, 0, width, height);

		int y = PAD + textH;
		g.setColor(Color.BLACK);
		g.drawString(text, PAD + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, PAD, y);

		int barY = y + 3;
		int barW = width - PAD * 2;
		g.setColor(BAR_BG);
		g.fillRect(PAD, barY, barW, BAR_H);
		g.setColor(BAR_FILL);
		g.fillRect(PAD, barY, barW * pct / 100, BAR_H);

		int etaY = barY + BAR_H + 3 + etaH;
		g.setFont(OverlayFonts.small(100));
		g.setColor(Color.BLACK);
		g.drawString(eta, PAD + 1, etaY + 1);
		g.setColor(ETA_TEXT);
		g.drawString(eta, PAD, etaY);

		return new Dimension(width, height);
	}

	/**
	 * "Next in ~12m" / "Next in ~1h 05m", or a waiting message until the
	 * rate has been measured.
	 */
	private static String etaText(double minutes)
	{
		if (minutes < 0)
		{
			return "Next in: measuring...";
		}
		long m = Math.max(1, Math.round(minutes));
		if (m >= 100 * 60)
		{
			return "Next in 99h+";
		}
		return m >= 60
			? String.format("Next in ~%dh %02dm", m / 60, m % 60)
			: "Next in ~" + m + "m";
	}
}
