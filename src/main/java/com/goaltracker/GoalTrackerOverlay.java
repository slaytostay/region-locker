/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2019, Slay to Stay, <https://github.com/slaytostay>
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
package com.goaltracker;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import com.regionlocker.RegionLocker;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

public class GoalTrackerOverlay extends Overlay
{
	private static final int REGION_SIZE = 1 << 6;
	// Bitmask to return first coordinate in region
	private static final int REGION_TRUNCATE = ~0x3F;

	@Inject
	private Client client;

	@Inject
	private GoalTrackerPlugin plugin;

	@Inject
	private GoalTrackerConfig config;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private GoalTrackerOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.HIGHEST);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.enableTooltip() || config.drawMapOverlay())
			drawRegionOverlay(graphics);

		return null;
	}

	private void drawRegionOverlay(Graphics2D graphics)
	{
		Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);

		if (map == null) return;

		WorldMap worldMap = client.getWorldMap();
		float pixelsPerTile = worldMap.getWorldMapZoom();
		Rectangle worldMapRect = map.getBounds();
		graphics.setClip(worldMapRect);

		int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
		int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

		Point worldMapPosition = worldMap.getWorldMapPosition();

		// Offset in tiles from anchor sides
		int yTileMin = worldMapPosition.getY() - heightInTiles / 2;
		int xRegionMin = (worldMapPosition.getX() - widthInTiles / 2) & REGION_TRUNCATE;
		int xRegionMax = ((worldMapPosition.getX() + widthInTiles / 2) & REGION_TRUNCATE) + REGION_SIZE;
		int yRegionMin = (yTileMin & REGION_TRUNCATE);
		int yRegionMax = ((worldMapPosition.getY() + heightInTiles / 2) & REGION_TRUNCATE) + REGION_SIZE;
		int regionPixelSize = (int) Math.ceil(REGION_SIZE * pixelsPerTile);

		Point mousePos = client.getMouseCanvasPosition();

		for (int x = xRegionMin; x < xRegionMax; x += REGION_SIZE)
		{
			for (int y = yRegionMin; y < yRegionMax; y += REGION_SIZE)
			{

				int yTileOffset = -(yTileMin - y);
				int xTileOffset = x + widthInTiles / 2 - worldMapPosition.getX();

				int xPos = ((int) (xTileOffset * pixelsPerTile)) + (int) worldMapRect.getX();
				int yPos = (worldMapRect.height - (int) (yTileOffset * pixelsPerTile)) + (int) worldMapRect.getY();
				// Offset y-position by a single region to correct for drawRect starting from the top
				yPos -= regionPixelSize;


				int regionId = ((x >> 6) << 8) | (y >> 6);
				Rectangle regionRect = new Rectangle(xPos, yPos, regionPixelSize, regionPixelSize);

				Goal[] goals = plugin.getGoals().stream().filter(g -> g.getChunk() == regionId).toArray(Goal[]::new);

				Goal[] reqs = new Goal[0];
				if (!RegionLocker.hasRegion(regionId))
				{
					reqs = plugin.getGoals().stream()
						.filter(g -> g
							.getRequirements().stream()
							.anyMatch(r -> r.getName().equals(Integer.toString(regionId))))
						.toArray(Goal[]::new);
				}

				if (config.enableTooltip() && plugin.isHotkeyPressed() && regionRect.contains(mousePos.getX(), mousePos.getY()))
				{
					final String tooltip = buildTooltip(goals, reqs);

					if (!tooltip.isEmpty())
					{
						tooltipManager.add(new Tooltip(tooltip));
					}
				}

				if (config.drawMapOverlay())
				{
					Color color;
					if (reqs.length > 0)
					{
						color = config.requiredChunkColor();
					}
					else if (goals.length > 0)
					{
						if (Arrays.stream(goals).allMatch(Goal::isCompleted))
						{
							if (config.hideCompletedGoals())
							{
								continue;
							}
							color = config.completedColor();
						}
						else if (Arrays.stream(goals).anyMatch(Goal::isBlocked))
						{
							color = config.blockedColor();
						}
						else
						{
							color = config.inProgressColor();
						}
					}
					else
					{
						continue;
					}

					graphics.setColor(color);
					graphics.drawRect(xPos + 1, yPos + 1, regionPixelSize - 2, regionPixelSize - 2);
				}
			}
		}
	}

	private String buildTooltip(Goal[] goals, Goal[] reqs)
	{
		String title = "Goals:</br>";
		StringBuilder sb = textFromGoals(title, goals);

		String reqsTitle = "Chunk required for:</br>";
		StringBuilder reqsSb = textFromGoals(reqsTitle, reqs);

		return sb.append(reqsSb).toString();
	}

	private StringBuilder textFromGoals(String title, Goal[] goals)
	{
		StringBuilder sb = new StringBuilder();
		for (final Goal goal : goals)
		{
			Color color;
			if (Arrays.stream(goals).allMatch(Goal::isCompleted))
			{
				color = config.completedColor();
			}
			else if (Arrays.stream(goals).anyMatch(Goal::isBlocked))
			{
				color = config.blockedColor();
			}
			else
			{
				color = config.inProgressColor();
			}

			sb.append(ColorUtil.wrapWithColorTag(goal.getName(), color) + "</br>");
		}
		if (!sb.toString().isEmpty()) sb.insert(0, title);
		return sb;
	}
}
