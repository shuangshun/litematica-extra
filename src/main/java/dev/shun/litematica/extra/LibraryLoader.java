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

import net.fabricmc.loader.api.FabricLoader;

import static dev.shun.litematica.extra.LitematicaExtra.LOGGER;

import java.io.*;
import java.nio.file.*;
import java.security.*;

public class LibraryLoader {

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

            byte[] expectedHash = calculateHash(is);

            boolean needExtract = true;
            if (libFile.exists()) {
                byte[] existingHash;
                try (FileInputStream fis = new FileInputStream(libFile)) {
                    existingHash = calculateHash(fis);
                }

                if (MessageDigest.isEqual(expectedHash, existingHash)) {
                    needExtract = false;
                    LOGGER.debug("Native library already exists and hash matches: {}", libFile.getAbsolutePath());
                } else {
                    LOGGER.debug("Native library hash mismatch, re-extracting: {}", libFile.getAbsolutePath());
                }
            }

            if (needExtract) {
                LOGGER.debug("Extracting {} to {}", resourcePath, libFile.getAbsolutePath());

                try (InputStream resourceIs = LibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (resourceIs == null) {
                        throw new FileNotFoundException("Native library not found in resources: " + resourcePath);
                    }

                    File tempFile = File.createTempFile(libFileName, ".tmp", tempDir.toFile());
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = resourceIs.read(buffer)) > -1) {
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
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Hash algorithm not available", e);
        }

        return libFile;
    }

    private static byte[] calculateHash(InputStream is) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            md.update(buffer, 0, len);
        }
        return md.digest();
    }

//    public static boolean isLoaded() {
//        return loaded;
//    }
}