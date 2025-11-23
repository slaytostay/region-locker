/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gpu;

import com.google.inject.Provides;
import com.gpu.runelite.GpuPlugin;
import com.gpu.runelite.GpuPluginConfig;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Region Locker GPU",
	description = "GPU plugin with unique shader for locked chunks",
	tags = {"fog", "draw distance", "chunk", "locker"},
	conflicts = "GPU",
	loadInSafeMode = false,
	configName = "RegionGpuPlugin"
)
public class RegionLockerGpuPlugin extends Plugin
{
	// The idea with this was to create a minimal wrapper around the GpuPlugin,
	// but there is unfortunately no way other than copying the entire plugin atm.
	// This at least makes the plugin easier to maintain, as there are minimal
	// edits to the copied source files.
	// Changes made to the original:
	// - Package renames
	// - Remove the @PluginDescriptor from GpuPlugin
	// - Make GpuPlugin#glProgram public
	// - Rename GpuPluginConfig#GROUP to provide separate configs
	// - Slight modifications to vert.glsl & frag.glsl

	// Define a wrapper class to make startUp and shutDown accessible
	private static class GpuPluginWrapper extends GpuPlugin
	{
		void start()
		{
			super.startUp();
		}

		void stop()
		{
			super.shutDown();
		}
	}

	// This is region locker's own
	@Provides
	GpuPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GpuPluginConfig.class);
	}

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private GpuPluginWrapper gpuPlugin;

	@Inject
	private RegionLockerAddon addon;

	@Override
	protected void startUp()
	{
		clientThread.invoke(() -> {
			gpuPlugin.start();
			eventBus.register(gpuPlugin);
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(() -> {
			gpuPlugin.stop();
			eventBus.unregister(gpuPlugin);
			addon.reset();
		});
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		try
		{
			// Update region locker's uniforms
			addon.beforeRender(GpuPlugin.glProgram);
		}
		catch (Throwable ex)
		{
			log.error("Error updating region locker uniforms", ex);
		}
	}
}
