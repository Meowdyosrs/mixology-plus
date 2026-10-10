package com.meowcape.mixologyplus;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.client.ui.FontManager;

/**
 * RuneLite's RuneScape fonts are pixel fonts: they are only sharp at their
 * native 16px size (or whole multiples of it) with text antialiasing off.
 * Deriving other sizes, or asking Java for a synthetic bold, is what makes
 * overlay text look blurry, so every overlay gets its fonts from here.
 */
final class OverlayFonts
{
	private static final float NATIVE_SIZE = 16f;

	private OverlayFonts()
	{
	}

	/**
	 * The regular RuneScape font at the given overlay scale (100 = native).
	 */
	static Font regular(int scalePercent)
	{
		return sized(FontManager.getRunescapeFont(), scalePercent);
	}

	/**
	 * The real bold RuneScape font (not a synthetic bold).
	 */
	static Font bold(int scalePercent)
	{
		return sized(FontManager.getRunescapeBoldFont(), scalePercent);
	}

	/**
	 * The small RuneScape font (for secondary text).
	 */
	static Font small(int scalePercent)
	{
		return sized(FontManager.getRunescapeSmallFont(), scalePercent);
	}

	/**
	 * Native size for 100%; otherwise the nearest whole pixel size, so the
	 * glyphs still land on the pixel grid.
	 */
	private static Font sized(Font base, int scalePercent)
	{
		if (scalePercent == 100)
		{
			return base;
		}
		return base.deriveFont((float) Math.round(NATIVE_SIZE * scalePercent / 100f));
	}

	/**
	 * Crisp text: no antialiasing at whole multiples of the native size,
	 * where the pixel font is designed to render, and light smoothing only
	 * at in-between scales where unsmoothed glyphs would look jagged.
	 */
	static void applyTextHints(Graphics2D g, int scalePercent)
	{
		boolean pixelPerfect = scalePercent % 100 == 0;
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, pixelPerfect
			? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
			: RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
	}
}
