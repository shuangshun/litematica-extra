package dev.shun.litematica.extra;

import net.fabricmc.loader.api.FabricLoader;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.nio.file.*;

public class LibraryLoader {
    private static final Logger LOGGER = LogManager.getLogger(LitematicaExtra.MOD_NAME + "/LibraryLoader");

    private static boolean loaded = false;
    private static final String LIBRARY_NAME = "Litematic_V7_To_V6_DynamicLibrary";

    public static synchronized void load() {
        if (loaded) return;

        try {
            String libFileName = System.mapLibraryName(LIBRARY_NAME);
            File libFile = extractNativeLibrary(libFileName);

            LOGGER.debug("Loading native library: {}", libFileName);

            System.load(libFile.getAbsolutePath());
            loaded = true;

            LOGGER.info("Native library loaded successfully: {}", libFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to load native library", e);
            throw new RuntimeException("Native library loading failed", e);
        }
    }

    private static File extractNativeLibrary(String libFileName) throws IOException {
        String resourcePath = "native/" + libFileName;

        Path tempDir = FabricLoader.getInstance().getGameDir().resolve("litematica_extra");
        Files.createDirectories(tempDir);

        File libFile = tempDir.resolve(libFileName).toFile();

        try (InputStream is = LibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Native library not found in resources: " + resourcePath);
            }

            if (!libFile.exists()) {
                LOGGER.debug("Extracting {} to {}", resourcePath, libFile.getAbsolutePath());

                File tempFile = File.createTempFile(libFileName, ".tmp", tempDir.toFile());
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }

                Files.move(tempFile.toPath(), libFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                // Linux/macOS
                String osName = System.getProperty("os.name").toLowerCase();
                if (!osName.contains("win")) {
                    if (!libFile.setExecutable(true)) {
                        LOGGER.warn("Failed to set executable permission for {}", libFile.getAbsolutePath());
                    }
                }
            }
        }

        return libFile;
    }

//    public static boolean isLoaded() {
//        return loaded;
//    }
}