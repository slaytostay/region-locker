package com.gpu;

import com.regionlocker.RegionLocker;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;

import static org.lwjgl.opengl.GL33C.*;

@Slf4j
public class RegionLockerAddon
{
	@Inject
	private Client client;

	private static final int LOCKED_REGIONS_SIZE = 16;
	private final int[] loadedLockedRegions = new int[LOCKED_REGIONS_SIZE];

	private boolean isValid;
	private int glProgram;
	private int uniUseGray;
	private int uniUseHardBorder;
	private int uniGrayAmount;
	private int uniGrayColor;
	private int uniBaseX;
	private int uniBaseY;
	private int uniLockedRegions;

	public void reset()
	{
		isValid = false;
		glProgram = 0;
	}

	public void beforeRender(int glProgram)
	{
		if (client.getGameState().getState() < GameState.LOADING.getState())
		{
			return;
		}

		if (this.glProgram != glProgram)
		{
			this.glProgram = glProgram;
			uniUseGray = glGetUniformLocation(glProgram, "region_locker_useGray");
			uniUseHardBorder = glGetUniformLocation(glProgram, "region_locker_useHardBorder");
			uniGrayAmount = glGetUniformLocation(glProgram, "region_locker_configGrayAmount");
			uniGrayColor = glGetUniformLocation(glProgram, "region_locker_configGrayColor");
			uniBaseX = glGetUniformLocation(glProgram, "region_locker_baseX");
			uniBaseY = glGetUniformLocation(glProgram, "region_locker_baseY");
			uniLockedRegions = glGetUniformLocation(glProgram, "region_locker_lockedRegions");
			isValid = uniUseGray != -1;
			checkGLErrors();
		}

		if (isValid)
		{
			updateUniforms();
		}

		checkGLErrors();
	}

	private void updateUniforms()
	{
		var vw = client.getTopLevelWorldView();
		if (vw == null)
		{
			return;
		}

		// Get the currently bound program, so we can restore the state later if needed
		int currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
		if (currentProgram != glProgram)
		{
			glUseProgram(glProgram);
		}

		glUniform1i(uniUseHardBorder, RegionLocker.hardBorder ? 1 : 0);
		glUniform1f(uniGrayAmount, RegionLocker.grayAmount / 255f);
		glUniform4f(uniGrayColor,
			RegionLocker.grayColor.getRed() / 255f,
			RegionLocker.grayColor.getGreen() / 255f,
			RegionLocker.grayColor.getBlue() / 255f,
			RegionLocker.grayColor.getAlpha() / 255f
		);

		var mapRegions = vw.getMapRegions();

		boolean isUnlockedInstance = false;
		if (vw.isInstance())
		{
			if (mapRegions != null)
			{
				for (int region : mapRegions)
				{
					if (RegionLocker.hasRegion(region))
					{
						isUnlockedInstance = true;
						break;
					}
				}
			}
		}

		if (!RegionLocker.renderLockedRegions || isUnlockedInstance)
		{
			glUniform1i(uniUseGray, 0);
		}
		else
		{
			glUniform1i(uniUseGray, 1);
			glUniform1i(uniBaseX, vw.getBaseX() * 128);
			glUniform1i(uniBaseY, vw.getBaseY() * 128);

			Arrays.fill(loadedLockedRegions, 0);
			if (mapRegions != null)
			{
				for (int i = 0; i < mapRegions.length; i++)
				{
					int region = mapRegions[i];

					if (RegionLocker.invertShader && !RegionLocker.hasRegion(region) || !RegionLocker.invertShader && RegionLocker.hasRegion(region))
					{
						loadedLockedRegions[i] = region;
					}
				}
			}

			glUniform1iv(uniLockedRegions, loadedLockedRegions);
		}

		// Restore the previous state
		if (glProgram != currentProgram)
		{
			glUseProgram(currentProgram);
		}
	}

	private void checkGLErrors()
	{
		int error;
		while ((error = glGetError()) != GL_NO_ERROR)
		{
			log.error("glGetError: {}", error);
		}
	}
}
