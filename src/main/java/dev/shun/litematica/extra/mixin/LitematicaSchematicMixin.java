package dev.shun.litematica.extra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.*;
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
                NbtCompound nbt;

                if (processedData.length >= 2
                        && (processedData[0] & 0xFF) == 0x1F
                        && (processedData[1] & 0xFF) == 0x8B
                ) {
                    nbt = NbtIo.readCompressed(new ByteArrayInputStream(processedData));
                } else {
                    nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(processedData)));
                }

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
             GZIPInputStream gis = new GZIPInputStream(fis)
        ) {
            NbtStreamReader reader = new NbtStreamReader(gis);
            NbtScanResult result = reader.scanRootFields("Version", "Metadata");

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