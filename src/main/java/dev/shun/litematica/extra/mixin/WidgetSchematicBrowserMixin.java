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

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.gui.DrawContext;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import static fi.dy.masa.litematica.schematic.LitematicaSchematic.MINECRAFT_DATA_VERSION;
import dev.shun.litematica.extra.util.Schema;
import dev.shun.litematica.extra.api.ISchematicMetadata;

import java.io.File;
import java.util.Map;

@Mixin(value = WidgetSchematicBrowser.class, remap = false)
public abstract class WidgetSchematicBrowserMixin {

    @Final @Shadow(remap = false) protected Map<File, SchematicMetadata> cachedMetadata;

    @Shadow @Final protected GuiSchematicBrowserBase parent;

    @Inject(method = "drawSelectedSchematicInfo", at = @At("TAIL"), remap = false)
    private void onDrawSelectedSchematicInfo(
            @Nullable DirectoryEntry entry, DrawContext drawContext, CallbackInfo ci,
            @Local(name = "x") int x, @Local(name = "y") int y
    ) {
        if (entry == null) return;

        File file = new File(entry.getDirectory(), entry.getName());
        SchematicMetadata meta = cachedMetadata.get(file);
        if (meta == null) return;

        ISchematicMetadata ext = ISchematicMetadata.of(meta);
        int version = ext.getSchematicVersion();
        int dataVersion = ext.getMinecraftDataVersion();
        String fileType = ext.getFileType();

        if (version <= 0) return;

        String versionStr;
        if ("litematic".equals(fileType)) {
            versionStr = StringUtils.translate("litematica-extra.gui.label.schematic_info.version", version);
        } else if ("schem".equals(fileType)) {
            versionStr = StringUtils.translate("litematica-extra.gui.label.schematic_info.sponge_version", version);
        } else if ("nbt".equals(fileType)) {
            versionStr = StringUtils.translate("litematica-extra.gui.label.schematic_info.vanilla_version");
        } else {
            versionStr = "Version: " + version;
        }

        int textColor = 0xC0C0C0C0;
        this.parent.drawString(drawContext, versionStr, x, y, textColor);
        y += 12;

        Schema schema = Schema.getSchemaByDataVersion(dataVersion);
        if (schema != null) {
            String dataStrKey = "litematica-extra.gui.label.schematic_info.schema";
            if (dataVersion - MINECRAFT_DATA_VERSION > 100) {
                if (version == 6) {
                    dataStrKey = "litematica-extra.gui.label.schematic_info.schema.convert";
                } else {
                    dataStrKey = "litematica-extra.gui.label.schematic_info.schema.newer";
                }
            }
            String dataStr = StringUtils.translate(dataStrKey, schema.getString(), dataVersion);
            this.parent.drawString(drawContext, dataStr, x, y, textColor);
        }
    }
}