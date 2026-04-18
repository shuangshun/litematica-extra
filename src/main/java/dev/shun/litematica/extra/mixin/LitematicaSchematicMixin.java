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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.*;
import net.minecraft.util.FixedBufferInputStream;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import dev.shun.litematica.extra.nbt.*;
import dev.shun.litematica.extra.SchematicNativeReader;
import static dev.shun.litematica.extra.LitematicaExtra.LOGGER;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

@Mixin(LitematicaSchematic.class)
public abstract class LitematicaSchematicMixin {

    @Inject(method = "readNbtFromFile(Ljava/io/File;)Lnet/minecraft/nbt/NbtCompound;", at = @At("HEAD"), cancellable = true)
    private static void onReadNbtFromFile(File file, CallbackInfoReturnable<NbtCompound> cir) {
        if (file == null || !file.getName().endsWith(".litematic")) {
            return;
        }

        if (!file.exists() || !file.canRead()) {
            return;
        }

        try {
            byte[] compressedData = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            byte[] processedData = SchematicNativeReader.convertSchematicIfNeeded(compressedData);

            if (processedData != null) {
                NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(processedData)));
                cir.setReturnValue(nbt);
                cir.cancel();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read schematic file: {}", file.getAbsolutePath(), e);
        }
    }

    @Inject(method = "readMetadataFromFile(Ljava/io/File;Ljava/lang/String;)Lfi/dy/masa/litematica/schematic/SchematicMetadata;", at = @At("HEAD"), cancellable = true)
    private static void onReadMetadataFromFile(File dir, String fileName, CallbackInfoReturnable<SchematicMetadata> cir) {
        if (dir == null || fileName == null || !fileName.endsWith(".litematic")) {
            return;
        }

        File file = new File(dir, fileName);
        if (!file.exists() || !file.canRead()) {
            cir.setReturnValue(null);
            cir.cancel();
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             GZIPInputStream gis = new GZIPInputStream(fis);
             FixedBufferInputStream fbis = new FixedBufferInputStream(gis)
        ) {
            NbtStreamScanner scanner = new NbtStreamScanner(fbis);
            NbtScanResult result = scanner.scan("Version", "Metadata");

            Integer version = result.getInt("Version");
            if (version == null || version < 1) {
                cir.setReturnValue(null);
                cir.cancel();
                return;
            }

            NbtCompound metadataNbt = result.getCompound("Metadata");
            if (metadataNbt == null) {
                cir.setReturnValue(null);
                cir.cancel();
                return;
            }

            SchematicMetadata metadata = new SchematicMetadata();
            metadata.readFromNBT(metadataNbt);
            cir.setReturnValue(metadata);
            cir.cancel();

        } catch (IOException e) {
            LOGGER.error("Failed to read metadata from schematic: {}", file.getAbsolutePath(), e);
            cir.setReturnValue(null);
            cir.cancel();
        }
    }
}