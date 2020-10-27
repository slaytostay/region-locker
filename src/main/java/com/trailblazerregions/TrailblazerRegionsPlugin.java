package com.trailblazerregions;

import com.goaltracker.GoalTrackerOverlay;
import com.google.inject.Provides;
import com.regionlocker.RegionLocker;
import com.regionlocker.RegionLockerPlugin;
import com.regionlocker.RegionTypes;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import sun.security.ssl.Debug;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@PluginDescriptor(
        name = "Trailblazer Regions",
        description = "Unlock an entire region based on Trailblazers.",
        tags = {"region", "locker", "chunk", "map", "square"},
        enabledByDefault = false
)
public class TrailblazerRegionsPlugin extends Plugin {
    static final String PLUGIN_NAME = "Trailblazer Regions";
    static final String CONFIG_KEY = "trailblazerregions";

    @Inject
    private TrailblazerRegionsConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    TrailblazerRegionsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TrailblazerRegionsConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        addRegions();
    }

    @Override
    protected void shutDown() throws Exception
    {
        RegionLocker._instance.resetRegions();
        RegionLocker._instance.setRegions(StringToList(TrailblazerRegion.NONE.getRegions()), RegionTypes.UNLOCKED);
        RegionLocker._instance.setConfigRegions();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("trailblazerregions"))
        {
            return;
        }
        addRegions();
    }

    public List<String> StringToList(String s)
    {
        List<String> regs;
        if (s.isEmpty())
            regs = new ArrayList<>();
        else
            regs = new ArrayList<>(Text.fromCSV(s));
        return regs;
    }

    public void addRegions()
    {
        RegionLocker._instance.resetRegions();
        //RegionLocker._instance.setRegions(StringToList(config.dropdownRegions().getRegions()), RegionTypes.UNLOCKED);
        RegionLocker._instance.setConfigRegions();
    }
}
