package com.regionlocker;

import com.goaltracker.GoalTrackerPlugin;
import com.gpu.RegionLockerGpuPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RegionLockerPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RegionLockerPlugin.class, RegionLockerGpuPlugin.class, GoalTrackerPlugin.class);
		RuneLite.main(args);
	}
}
