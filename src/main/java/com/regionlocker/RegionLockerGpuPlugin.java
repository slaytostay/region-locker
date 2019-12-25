package com.regionlocker;

import net.runelite.api.Client;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.gpu.GpuPlugin;
import net.runelite.client.util.OSType;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.Scanner;
import java.util.function.Function;

@PluginDescriptor(
		name = "Region GPU",
		description = "Utilizes the GPU for the Region Locker plugin",
		enabledByDefault = false,
		tags = {"fog", "draw distance", "chunk", "locker", "chunklite"}
)
public class RegionLockerGpuPlugin extends GpuPlugin
{
	@Inject
	private Client client;

	private final int[] loadedLockedRegions = new int[12];

	private int uniUseGray;
	private int uniUseHardBorder;
	private int uniGrayAmount;
	private int uniGrayColor;
	private int uniBaseX;
	private int uniBaseY;
	private int uniLockedRegions;

	@Override
	protected void initUniforms()
	{
		super.initUniforms();
		uniUseGray = gl.glGetUniformLocation(glProgram, "useGray");
		uniUseHardBorder = gl.glGetUniformLocation(glProgram, "useHardBorder");
		uniGrayAmount = gl.glGetUniformLocation(glProgram, "configGrayAmount");
		uniGrayColor = gl.glGetUniformLocation(glProgram, "configGrayColor");
		uniBaseX = gl.glGetUniformLocation(glProgram, "baseX");
		uniBaseY = gl.glGetUniformLocation(glProgram, "baseY");
		uniLockedRegions = gl.glGetUniformLocation(glProgram, "lockedRegions");
	}

	private boolean instanceRegionUnlocked()
	{
		for (int i = 0; i < client.getMapRegions().length; i++)
		{
			int region = client.getMapRegions()[i];
			if (RegionLocker.hasRegion(region)) return true;
		}
		return false;
	}

	private void createLockedRegions()
	{
		int bx, by;
		bx = client.getBaseX() * 128;
		by = client.getBaseY() * 128;

		for (int i = 0; i < loadedLockedRegions.length; i++)
		{
			loadedLockedRegions[i] = 0;
		}

		for (int i = 0; i < client.getMapRegions().length; i++)
		{
			int region = client.getMapRegions()[i];
			if (RegionLocker.hasRegion(region))
			{
				loadedLockedRegions[i] = region;
			}
		}

		gl.glUniform1i(uniBaseX, bx);
		gl.glUniform1i(uniBaseY, by);
		gl.glUniform1iv(uniLockedRegions, 12, loadedLockedRegions, 0);
	}

	@Override
	protected void setUniforms()
	{
		gl.glUniform1i(uniUseHardBorder, RegionLocker.hardBorder ? 1 : 0);
		gl.glUniform1f(uniGrayAmount, RegionLocker.grayAmount / 255f);
		gl.glUniform4f(uniGrayColor, RegionLocker.grayColor.getRed() / 255f, RegionLocker.grayColor.getGreen() / 255f, RegionLocker.grayColor.getBlue() / 255f, RegionLocker.grayColor.getAlpha() / 255f);
		if (!RegionLocker.renderLockedRegions || (client.isInInstancedRegion() && instanceRegionUnlocked()))
		{
			gl.glUniform1i(uniUseGray, 0);
		}
		else
		{
			gl.glUniform1i(uniUseGray, 1);
			createLockedRegions();
		}
	}

	@Override
	public Function<String, String> getResourceLoader()
	{
		String glVersionHeader;
		if (OSType.getOSType() == OSType.Linux)
		{
			glVersionHeader = "#version 420\n#extension GL_ARB_compute_shader : require\n#extension GL_ARB_shader_storage_buffer_object : require\n";
		}
		else
		{
			glVersionHeader = "#version 430\n";
		}

		return (s) ->
		{
			if (s.equals("frag.glsl") || s.equals("geom.glsl") || s.equals("vert.glsl"))
			{
				return inputStreamToString(getClass().getResourceAsStream(s));
			}
			else if (s.endsWith(".glsl"))
			{
				return inputStreamToString(GpuPlugin.class.getResourceAsStream(s));
			}
			else
			{
				return s.equals("version_header") ? glVersionHeader : "";
			}
		};
	}

	private String inputStreamToString(InputStream in)
	{
		Scanner scanner = (new Scanner(in)).useDelimiter("\\A");
		return scanner.next();
	}
}
