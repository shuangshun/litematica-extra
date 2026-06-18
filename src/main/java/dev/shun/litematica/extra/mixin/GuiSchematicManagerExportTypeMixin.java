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

import fi.dy.masa.litematica.gui.GuiSchematicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import dev.shun.litematica.extra.data.V6ModeState;

@Mixin(targets = "fi.dy.masa.litematica.gui.GuiSchematicManager$ExportType")
public abstract class GuiSchematicManagerExportTypeMixin implements IConfigOptionListEntry {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetDisplayName(CallbackInfoReturnable<String> cir) {
        GuiSchematicManager.ExportType self = (GuiSchematicManager.ExportType) (Object) this;
        if (self == GuiSchematicManager.ExportType.VANILLA && V6ModeState.isActive()) {
            cir.setReturnValue(StringUtils.translate("litematica-extra.gui.label.schematic_manager.export_type.v6_litematic"));
        }
    }

    @Inject(method = "cycle", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCycle(boolean forward, CallbackInfoReturnable<IConfigOptionListEntry> cir) {
        GuiSchematicManager.ExportType self = (GuiSchematicManager.ExportType) (Object) this;

        // SCHEMATIC -> VANILLA -> V6
        if (self == GuiSchematicManager.ExportType.SCHEMATIC) {
            if (forward) {
                // SCHEMATIC -> VANILLA
                V6ModeState.setActive(false);
                cir.setReturnValue(GuiSchematicManager.ExportType.VANILLA);
            } else {
                // SCHEMATIC <- V6
                V6ModeState.setActive(true);
                cir.setReturnValue(GuiSchematicManager.ExportType.VANILLA);
            }
        } else if (self == GuiSchematicManager.ExportType.VANILLA) {
            if (forward) {
                if (V6ModeState.isActive()) {
                    // V6 -> SCHEMATIC
                    V6ModeState.setActive(false);
                    cir.setReturnValue(GuiSchematicManager.ExportType.SCHEMATIC);
                } else {
                    // VANILLA -> V6
                    V6ModeState.setActive(true);
                    cir.setReturnValue(GuiSchematicManager.ExportType.VANILLA);
                }
            } else {
                if (V6ModeState.isActive()) {
                    // V6 -> VANILLA
                    V6ModeState.setActive(false);
                    cir.setReturnValue(GuiSchematicManager.ExportType.VANILLA);
                } else {
                    // VANILLA -> SCHEMATIC
                    cir.setReturnValue(GuiSchematicManager.ExportType.SCHEMATIC);
                }
            }
        }
    }

    @Inject(method = "fromStringStatic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onFromStringStatic(String name, CallbackInfoReturnable<GuiSchematicManager.ExportType> cir) {
        if ("V6_LITEMATIC".equalsIgnoreCase(name)) {
            V6ModeState.setActive(true);
            cir.setReturnValue(GuiSchematicManager.ExportType.VANILLA);
        } else if ("VANILLA".equalsIgnoreCase(name)) {
            V6ModeState.setActive(false);
        } else if ("SCHEMATIC".equalsIgnoreCase(name)) {
            V6ModeState.setActive(false);
        }
    }
}