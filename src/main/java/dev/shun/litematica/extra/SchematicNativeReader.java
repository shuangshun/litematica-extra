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