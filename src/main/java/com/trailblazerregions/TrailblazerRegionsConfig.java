package com.trailblazerregions;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("trailblazerregions")
public interface TrailblazerRegionsConfig extends Config {
    @ConfigItem(
            keyName = "dropdownRegions",
            name = "Current Region",
            description = "Show all region map limitations",
            position = 0
    )
    default TrailblazerRegion dropdownRegions()
    {
        return TrailblazerRegion.NONE;
    }
}
