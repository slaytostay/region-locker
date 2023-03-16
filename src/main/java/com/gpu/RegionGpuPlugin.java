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

import com.regionlocker.RegionLocker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.gpu.GpuPlugin;
import net.runelite.client.plugins.gpu.Shader;
import net.runelite.client.plugins.gpu.template.Template;
import net.runelite.client.util.OSType;
import org.lwjgl.opengl.GL43C;

@PluginDescriptor(
	name = "Region GPU",
	description = "GPU plugin with unique shader for locked chunks",
	enabledByDefault = false,
	tags = {"fog", "draw distance", "chunk", "locker"},
	conflicts = "GPU",
	loadInSafeMode = false
)
@Slf4j
public class RegionGpuPlugin extends GpuPlugin
{
	private static final int LOCKED_REGIONS_SIZE = 16;

	static final String LINUX_VERSION_HEADER =
		"#version 420\n" +
			"#extension GL_ARB_compute_shader : require\n" +
			"#extension GL_ARB_shader_storage_buffer_object : require\n" +
			"#extension GL_ARB_explicit_attrib_location : require\n";
	static final String WINDOWS_VERSION_HEADER = "#version 430\n";

	static final Shader PROGRAM = new Shader()
		.add(GL43C.GL_VERTEX_SHADER, "vert.glsl")
		.add(GL43C.GL_GEOMETRY_SHADER, "geom.glsl")
		.add(GL43C.GL_FRAGMENT_SHADER, "frag.glsl");

	private final int[] loadedLockedRegions = new int[LOCKED_REGIONS_SIZE];

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private int uniUseGray;
	private int uniUseHardBorder;
	private int uniGrayAmount;
	private int uniGrayColor;
	private int uniBaseX;
	private int uniBaseY;
	private int uniLockedRegions;

	private int glProgram;

	@Override
	protected void startUp()
	{
		super.startUp();

		clientThread.invoke(() ->
		{
			try
			{
				replaceSceneShader();
			}
			catch (Exception ex)
			{
				log.error("Failed to start region locker GPU plugin", ex);
			}
		});
	}

	private void replaceSceneShader() throws Exception
	{
		String versionHeader = OSType.getOSType() == OSType.Linux ? LINUX_VERSION_HEADER : WINDOWS_VERSION_HEADER;
		Template template = new Template()
			.add(key -> "version_header".equals(key) ? versionHeader : null)
			.addInclude(RegionGpuPlugin.class)
			.addInclude(GpuPlugin.class);

		// Compile a modified version of the scene shader
		glProgram = PROGRAM.compile(template);
		Field glProgramField = GpuPlugin.class.getDeclaredField("glProgram");
		glProgramField.setAccessible(true);
		glProgramField.set(this, glProgram);

		// Reinitialize GPU uniforms after replacing the scene shader
		Method initUniformsMethod = GpuPlugin.class.getDeclaredMethod("initUniforms");
		initUniformsMethod.setAccessible(true);
		initUniformsMethod.invoke(this);

		// Initialize region locker-specific uniforms
		uniUseGray = GL43C.glGetUniformLocation(glProgram, "useGray");
		uniUseHardBorder = GL43C.glGetUniformLocation(glProgram, "useHardBorder");
		uniGrayAmount = GL43C.glGetUniformLocation(glProgram, "configGrayAmount");
		uniGrayColor = GL43C.glGetUniformLocation(glProgram, "configGrayColor");
		uniBaseX = GL43C.glGetUniformLocation(glProgram, "baseX");
		uniBaseY = GL43C.glGetUniformLocation(glProgram, "baseY");
		uniLockedRegions = GL43C.glGetUniformLocation(glProgram, "lockedRegions");
	}

	@Override
	public void draw(int overlayColor)
	{
		// Update region locker-specific uniforms
		GL43C.glUseProgram(glProgram);
		GL43C.glUniform1i(uniUseHardBorder, RegionLocker.hardBorder ? 1 : 0);
		GL43C.glUniform1f(uniGrayAmount, RegionLocker.grayAmount / 255f);
		GL43C.glUniform4f(uniGrayColor, RegionLocker.grayColor.getRed() / 255f, RegionLocker.grayColor.getGreen() / 255f, RegionLocker.grayColor.getBlue() / 255f, RegionLocker.grayColor.getAlpha() / 255f);
		if (!RegionLocker.renderLockedRegions || (client.isInInstancedRegion() && instanceRegionUnlocked()))
		{
			GL43C.glUniform1i(uniUseGray, 0);
		}
		else
		{
			GL43C.glUniform1i(uniUseGray, 1);
			createLockedRegions();
		}

		// Return to regular GPU plugin draw call
		super.draw(overlayColor);
	}

	private boolean instanceRegionUnlocked()
	{
		if (client.getMapRegions() != null && client.getMapRegions().length > 0 && (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING))
		{
			for (int i = 0; i < client.getMapRegions().length; i++)
			{
				int region = client.getMapRegions()[i];
				if (RegionLocker.hasRegion(region)) return true;
			}
		}
		return false;
	}

	private void createLockedRegions()
	{
		int bx, by;
		bx = client.getBaseX() * 128;
		by = client.getBaseY() * 128;

		Arrays.fill(loadedLockedRegions, 0);

		if (client.getMapRegions() != null && client.getMapRegions().length > 0 && (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING))
		{
			for (int i = 0; i < client.getMapRegions().length; i++)
			{
				int region = client.getMapRegions()[i];

				if(RegionLocker.invertShader && !RegionLocker.hasRegion(region))
				{
					loadedLockedRegions[i] = region;
				}
				else if (!RegionLocker.invertShader && RegionLocker.hasRegion(region))
				{
					loadedLockedRegions[i] = region;
				}
			}
		}

		GL43C.glUniform1i(uniBaseX, bx);
		GL43C.glUniform1i(uniBaseY, by);
		GL43C.glUniform1iv(uniLockedRegions, loadedLockedRegions);
	}
}
