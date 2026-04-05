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

