package com.meowcape.mixologyplus;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MixologyPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MixologyPlugin.class);
		RuneLite.main(args);
	}
}
