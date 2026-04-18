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

package dev.shun.litematica.extra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(TaskPasteSchematicPerChunkCommand.class)
public class TaskPasteSchematicPerChunkCommandMixin {

	@Unique
	private final Set<UUID> processedEntities = new HashSet<>();

	@Inject(method = "summonEntity", at = @At("HEAD"), cancellable = true)
	private void onSummonEntityHead(Entity entity, CallbackInfo ci) {
		UUID entityId = entity.getUuid();

		// Check if the entity has been pasted
		if (entityId != null && processedEntities.contains(entityId)) {
			ci.cancel();
		}
	}

	@Inject(method = "summonEntity", at = @At("TAIL"))
	private void onSummonEntityTail(Entity entity, CallbackInfo ci) {
		UUID entityId = entity.getUuid();

		// Record the UUID of the pasted entity
		if (entityId != null) {
			processedEntities.add(entityId);
		}
	}
}

