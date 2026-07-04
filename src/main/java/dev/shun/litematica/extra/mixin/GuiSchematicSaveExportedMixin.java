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

import fi.dy.masa.malilib.interfaces.IStringConsumer;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.litematica.gui.GuiSchematicSaveExported;
import dev.shun.litematica.extra.mixin.accessor.*;
import dev.shun.litematica.extra.data.V6ModeState;
import dev.shun.litematica.extra.SchematicNativeReader;
import static dev.shun.litematica.extra.LitematicaExtra.LOGGER;

import java.io.File;
import java.nio.file.*;
import java.util.*;

@Mixin(targets = "fi.dy.masa.litematica.gui.GuiSchematicSaveExported$ButtonListener")
public abstract class GuiSchematicSaveExportedMixin {

    @Final
    @Shadow(remap = false)
    private GuiSchematicSaveExported gui;

    @Inject(method = "actionPerformedWithButton", at = @At("HEAD"), cancellable = true, remap = false)
    private void onActionPerformed(ButtonBase button, int mouseButton, CallbackInfo ci) {
        if (!V6ModeState.isActive()) return;

        GuiSchematicSaveBaseAccessor baseAccessor = (GuiSchematicSaveBaseAccessor) gui;
        GuiSchematicSaveExportedAccessor exportedAccessor = (GuiSchematicSaveExportedAccessor) gui;
        GuiListBaseAccessor listAccessor = (GuiListBaseAccessor) gui;
        WidgetFileBrowserBase listWidget = (WidgetFileBrowserBase) listAccessor.invokeGetListWidget();

        String fileName = baseAccessor.invokeGetTextFieldText();
        File inDir = exportedAccessor.getDirSource();
        String inFile = exportedAccessor.getInputFileName();
        File outDir = listWidget.getCurrentDirectory();
        boolean override = GuiBase.isShiftDown();
        boolean ignoreEntities = baseAccessor.getCheckboxIgnoreEntities().isChecked();

        if (convertToV6(inDir, inFile, outDir, fileName, ignoreEntities, override, gui)) {
            gui.addMessage(MessageType.SUCCESS, "litematica-extra.message.litematic_downgrade_exported_as", fileName);
            listWidget.refreshEntries();
        } else {
            gui.addMessage(MessageType.ERROR, "litematica-extra.error.schematic_conversion.litematic_to_litematica.failed_to_downgrade_litematic");
        }
        ci.cancel();
    }

    @Unique
    private boolean convertToV6(File inDir, String inFile, File outDir, String outFileName, boolean ignoreEntities, boolean override, IStringConsumer feedback) {
        String targetName = outFileName;
        if (!targetName.endsWith(".litematic")) {
            targetName += ".litematic";
        }
        Path sourcePath = inDir.toPath().resolve(inFile);
        Path targetPath = outDir.toPath().resolve(targetName);

        if (Files.exists(targetPath) && !override) {
            feedback.setString(StringUtils.translate("litematica-extra.error.schematic_write_to_file_failed.exists", targetPath));
            return false;
        }

        try {
            Map<String, Boolean> fieldsToErase = new HashMap<>();
            if (ignoreEntities) {
                fieldsToErase.put("*/Regions/*/Entities", true);
            }

            byte[] processedData = SchematicNativeReader.readAndConvertSchematic(sourcePath,
                    true, true, fieldsToErase
            );

            if (processedData != null) {
                Files.write(targetPath, processedData);
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to downgrade: ", e);
            return false;
        }
    }
}