package com.meowcape.mixologyplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A short, soft yellow tint over the game view when a digweed becomes
 * ready to pick: two quick pulses that fade out within about a second.
 * No text, and drawn under the game interface.
 */
@Singleton
public class DigweedScreenFlash extends Overlay
{
	private static final long DURATION_MS = 1100;
	private static final int PULSES = 2;
	private static final int MAX_ALPHA = 70;

	private final Client client;
	private final MixologyState state;
	private final MixologyConfig config;

	@Inject
	private DigweedScreenFlash(Client client, MixologyState state, MixologyConfig config)
	{
		this.client = client;
		this.state = state;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long elapsed = System.currentTimeMillis() - state.getFlashStartMs();
		if (state.getFlashStartMs() == 0 || elapsed < 0 || elapsed >= DURATION_MS || !state.isInLab())
		{
			return null;
		}
		double t = elapsed / (double) DURATION_MS;
		// two sine pulses, fading out over the whole flash
		double pulse = Math.abs(Math.sin(t * Math.PI * PULSES));
		int alpha = (int) Math.round(MAX_ALPHA * pulse * (1.0 - t));
		if (alpha <= 0)
		{
			return null;
		}
		Color c = config.digweedColor();
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
		g.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		return null;
	}
}
