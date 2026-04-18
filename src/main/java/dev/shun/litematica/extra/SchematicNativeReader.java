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

import net.minecraft.util.FixedBufferInputStream;

import dev.shun.litematica.extra.nbt.*;
import static dev.shun.litematica.extra.LitematicaExtra.LOGGER;

import java.util.zip.*;
import java.io.*;

public class SchematicNativeReader {

    public static native byte[] V7_To_V6(byte[] rawNbtData);

    public static byte[] convertSchematicIfNeeded(byte[] compressedInput) {
        if (compressedInput == null || compressedInput.length < 10) {
            return null;
        }

        try {
            byte[] rawNbt = decompressGzip(compressedInput);

            Integer version = readVersion(rawNbt);
            if (version != null && version <= 6) {
                return rawNbt;
            }

            return V7_To_V6(rawNbt);

        } catch (Exception e) {
            LOGGER.error("Schematic conversion failed", e);
            return null;
        }
    }

    private static Integer readVersion(byte[] data) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             FixedBufferInputStream fbis = new FixedBufferInputStream(bis)
        ) {

            NbtStreamScanner scanner = new NbtStreamScanner(fbis);
            NbtScanResult result = scanner.scan("Version");

            return result.getInt("Version");

        } catch (EOFException e) {
            return null;
        }
    }

    private static byte[] decompressGzip(byte[] data) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bis);
             FixedBufferInputStream fbis = new FixedBufferInputStream(gis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fbis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }

//    private static byte[] compressGzip(byte[] data) throws IOException {
//        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
//             GZIPOutputStream gos = new GZIPOutputStream(bos)
//        ) {
//            gos.write(data);
//            gos.finish();
//            return bos.toByteArray();
//        }
//    }
}