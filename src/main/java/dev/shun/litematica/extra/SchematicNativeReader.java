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

import static fi.dy.masa.litematica.schematic.LitematicaSchematic.MINECRAFT_DATA_VERSION;
import dev.shun.litematica.extra.nbt.*;
import dev.shun.litematica.extra.util.Schema;
import static dev.shun.litematica.extra.LitematicaExtra.LOGGER;

import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.*;

public class SchematicNativeReader {

    private static final int TARGET_DATA_VERSION = Schema.SCHEMA_1_20_04.getDataVersion();

    public enum NativeOp {
        CONVERT_V7_TO_V6(1),
        SORT_FIELDS(2),
        ERASE_FIELDS(3);

        private final int code;

        NativeOp(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static native byte[] nativeExecute(
            ByteBuffer nbtData,
            int[] ops,
            long[] paramBlocks, byte[] paramData
    );

    public static byte[] readAndConvertSchematic(
            Path path,
            boolean compress, boolean sort,
            Map<String, Boolean> fieldsToErase
    ) {
        byte[] compressedData;
        try {
            compressedData = Files.readAllBytes(path);
        } catch (Exception e) {
            LOGGER.error("Failed to read schematic: ", e);
            return null;
        }

        byte[] processedData = convertAndProcessSchematic(compressedData, sort, fieldsToErase);
        if (processedData == null) {
            return null;
        }

        if (compress) {
            try {
                return compressGzip(processedData);
            } catch (IOException e) {
                LOGGER.error("Failed to compress schematic: ", e);
                return null;
            }
        } else {
            return processedData;
        }
    }

    private static byte[] convertAndProcessSchematic(
            byte[] schematicData,
            boolean sort,
            Map<String, Boolean> fieldsToErase
    ) {
        if (schematicData == null || schematicData.length < 10) {
            return null;
        }

        try {
            byte[] rawNbt = isGzipCompressed(schematicData)
                    ? decompressGzip(schematicData)
                    : schematicData;

            Integer version = readVersion(rawNbt);
            boolean needConvert = (MINECRAFT_DATA_VERSION <= TARGET_DATA_VERSION)
                    && (version == null || version > 6);

            boolean needErase = (fieldsToErase != null && !fieldsToErase.isEmpty());

            if (!needConvert && !sort && !needErase) return rawNbt;

            int bufferCapacity = needConvert
                    ? (int) (rawNbt.length * 1.2)
                    : rawNbt.length;
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(bufferCapacity);
            directBuffer.put(rawNbt);
            directBuffer.flip();

            List<NativeOp> opsList = new ArrayList<>(3);
            if (needConvert) opsList.add(NativeOp.CONVERT_V7_TO_V6);
            if (sort) opsList.add(NativeOp.SORT_FIELDS);
            if (needErase) opsList.add(NativeOp.ERASE_FIELDS);

            int[] ops = opsList.stream().mapToInt(NativeOp::getCode).toArray();
            long[] paramBlocks = new long[opsList.size()];

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            int blockIndex = 0;
            for (NativeOp op : opsList) {
                if (op == NativeOp.ERASE_FIELDS) {
                    encodeEraseFieldsParams(bos, fieldsToErase, paramBlocks, blockIndex);
                }
                blockIndex++;
            }

            byte[] paramData = bos.size() > 0 ? bos.toByteArray() : new byte[0];

            byte[] result = nativeExecute(directBuffer, ops, paramBlocks, paramData);
            if (result == null) {
                byte[] processedData = new byte[directBuffer.remaining()];
                directBuffer.get(processedData);
                return processedData;
            } else {
                return result;
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to conversion schematic: ", e);
            return null;
        }
    }

    private static void encodeEraseFieldsParams(
            ByteArrayOutputStream bos,
            Map<String, Boolean> fieldsToErase,
            long[] paramBlocks, int blockIndex
    ) {
        try {
            // starting offset
            int offset = bos.size();

            bos.write(intToBytes(fieldsToErase.size()));
            for (Map.Entry<String, Boolean> entry : fieldsToErase.entrySet()) {
                String name = entry.getKey();
                Boolean mode = entry.getValue();
                byte[] nameBytes = toModifiedUtf8(name);

                // 1=REMOVE, 0=CLEAR
                bos.write(mode ? 1 : 0);
                bos.write((nameBytes.length >> 8) & 0xFF);
                bos.write(nameBytes.length & 0xFF);
                bos.write(nameBytes);
            }

            // operation parameter length
            int length = bos.size() - offset;
            if (paramBlocks != null) {
                paramBlocks[blockIndex] = ((long) offset << 32) | (length & 0xFFFFFFFFL);
            }
        } catch (IOException ignored) {}
    }

    private static byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    private static byte[] toModifiedUtf8(String s) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeUTF(s);
        } catch (IOException ignored) {}

        byte[] data = bos.toByteArray();
        // writeUTF: 2-byte length
        return Arrays.copyOfRange(data, 2, data.length);
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

    private static boolean isGzipCompressed(byte[] data) {
        return data != null && data.length >= 2 && data[0] == (byte) 0x1F && data[1] == (byte) 0x8B;
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

    private static byte[] compressGzip(byte[] data) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gos = new GZIPOutputStream(bos)
        ) {
            gos.write(data);
            gos.finish();
            return bos.toByteArray();
        }
    }
}