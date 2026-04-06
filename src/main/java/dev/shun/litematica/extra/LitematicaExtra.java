package dev.shun.litematica.extra;

import net.fabricmc.api.ModInitializer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class LitematicaExtra implements ModInitializer {

	public static final String MOD_NAME = "LitematicaExtra";
	public static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void onInitialize() {
		LOGGER.info("{} initializing...", MOD_NAME);

		LibraryLoader.load();

		LOGGER.info("{} initialized!", MOD_NAME);
	}
}