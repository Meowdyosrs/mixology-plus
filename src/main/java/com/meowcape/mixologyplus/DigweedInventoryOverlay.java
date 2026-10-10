package com.meowcape.mixologyplus;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * When you hold the digweed and the potion it should go on, both items get
 * a steady outline in the inventory, so it is clear which two to use on
 * each other.
 */
@Singleton
public class DigweedInventoryOverlay extends WidgetItemOverlay
{
	private static final int DIGWEED_ITEM_ID = ItemID.MM_LAB_SPECIAL_HERB;

	private final MixologyState state;
	private final MixologyConfig config;
	private final ItemManager itemManager;

	@Inject
	private DigweedInventoryOverlay(MixologyState state, MixologyConfig config, ItemManager itemManager)
	{
		this.state = state;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		MixPotion target = state.getDigweedTarget();
		// always shown while combining is the current step; otherwise only
		// if the highlight setting is on
		boolean isStep = state.getLabAction() == MixologyState.LabAction.USE_DIGWEED;
		if (!(isStep || config.digweedUseHighlight()) || !state.isInLab() || target == null || !state.isDigweedComboReady())
		{
			return;
		}
		boolean match = itemId == DIGWEED_ITEM_ID
			|| itemId == target.getUnfinishedItemId()
			|| itemId == target.getFinishedItemId();
		if (!match)
		{
			return;
		}
		// outline the item's own shape (like the ground items / inventory
		// tags style) rather than a box around the slot
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.digweedColor());
		if (outline == null)
		{
			return;
		}
		Rectangle b = widgetItem.getCanvasBounds();
		graphics.drawImage(outline, b.x, b.y, null);
	}
}
