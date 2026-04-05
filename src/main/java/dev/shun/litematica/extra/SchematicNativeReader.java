package dev.shun.litematica.extra;

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
            Integer version = readVersion(compressedInput);

            if (version != null && version <= 6) {
                return compressedInput;
            }

            byte[] rawNbt = decompressGzip(compressedInput);
            byte[] convertedRaw = V7_To_V6(rawNbt);

            if (convertedRaw == null) {
                return null;
            }

            return convertedRaw;

        } catch (Exception e) {
            LOGGER.error("Schematic conversion failed", e);
            return null;
        }
    }

    private static Integer readVersion(byte[] data) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bis)
        ) {

            NbtStreamReader reader = new NbtStreamReader(gis);
            NbtScanResult result = reader.scanRootFields("Version");

            return result.getInt("Version");

        } catch (EOFException e) {
            return null;
        }
    }

    private static byte[] decompressGzip(byte[] data) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gis.read(buffer)) > 0) {
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