package com.regionlocker;

import net.runelite.client.plugins.gpu.GpuPlugin;
import net.runelite.client.util.OSType;
import java.io.InputStream;
import java.util.Scanner;
import java.util.function.Function;

public class RegionLockerGpuPlugin extends GpuPlugin
{
	// Never gets called
	@Override
	public Function<String, String> getResourceLoader()
	{
		String glVersionHeader;
		if (OSType.getOSType() == OSType.Linux) {
			glVersionHeader = "#version 420\n#extension GL_ARB_compute_shader : require\n#extension GL_ARB_shader_storage_buffer_object : require\n";
		} else {
			glVersionHeader = "#version 430\n";
		}

		return (s) -> {
			if (s.endsWith(".glsl")) {
				System.out.println("Shader path: " + getClass().getResource(s).getPath());
				System.out.println("Shader file: " + getClass().getResource(s).getFile());
				return inputStreamToString(getClass().getResourceAsStream(s));
			} else {
				return s.equals("version_header") ? glVersionHeader : "";
			}
		};
	}

	private String inputStreamToString(InputStream in) {
		Scanner scanner = (new Scanner(in)).useDelimiter("\\A");
		return scanner.next();
	}
}
