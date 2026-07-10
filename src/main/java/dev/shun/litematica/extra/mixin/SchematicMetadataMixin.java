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
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.nbt.NbtCompound;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import dev.shun.litematica.extra.api.ISchematicMetadata;

@Mixin(value = SchematicMetadata.class, remap = false)
public abstract class SchematicMetadataMixin implements ISchematicMetadata {

    @Unique private int schematicVersion;
    @Unique private int minecraftDataVersion;
    @Unique private String fileType;

    @Override
    public int getSchematicVersion() { return schematicVersion; }
    @Override
    public void setSchematicVersion(int v) { this.schematicVersion = v; }

    @Override
    public int getMinecraftDataVersion() { return minecraftDataVersion; }
    @Override
    public void setMinecraftDataVersion(int v) { this.minecraftDataVersion = v; }

    @Override
    public String getFileType() { return fileType; }
    @Override
    public void setFileType(String type) { this.fileType = type; }

    // 在 writeToNBT 时保存版本信息（可选）
    @ModifyReturnValue(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private NbtCompound onWriteToNBT(NbtCompound nbt) {
        if (this.schematicVersion > 0) {
            nbt.putInt("SchematicVersion", this.schematicVersion);
        }
        if (this.minecraftDataVersion > 0) {
            nbt.putInt("MinecraftDataVersion", this.minecraftDataVersion);
        }
        return nbt;
    }
}