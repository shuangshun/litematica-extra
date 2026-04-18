/*
 * This file is part of the Litematica Extra project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  shuangshun and contributors
 *
 * Litematica Extra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Litematica Extra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Litematica Extra.  If not, see <https://www.gnu.org/licenses/>.
 */

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