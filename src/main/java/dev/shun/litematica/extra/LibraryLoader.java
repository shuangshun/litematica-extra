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
import java.util.*;
import java.nio.file.*;
import java.security.*;

public class LibraryLoader {

    private static boolean loaded = false;
    private static String platform = getPlatformName();
    private static final String arch = getArchName();
    private static final Boolean isWindows = platform.contains("windows");
    private static final Boolean isAndroid = detectAndroid();
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
        Path tempDir = getNativeLibDir();
        String resourcePath = "native/" + platform + "-" + arch + "/" + libFileName;

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
                    if (!isWindows) {
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

    private static String getPlatformName() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac")) {
            return "macos";
        } else if (osName.contains("linux")) {
            return "linux";
        } else {
            throw new RuntimeException("Unsupported operating system: " + osName);
        }
    }

    private static String getArchName() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            return "x64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            return "arm64";
        } else if (osArch.contains("x86") || osArch.contains("i386") || osArch.contains("i686")) {
            return "x86";
        } else {
            throw new RuntimeException("Unsupported arch: " + osArch);
        }
    }

    private static boolean detectAndroid() {
        String[] launcherEnvKeys = {"FCL_NATIVEDIR", "POJAV_NATIVEDIR", "MOD_ANDROID_RUNTIME", "FCL_VERSION_CODE"};
        for (String key : launcherEnvKeys) {
            String val = System.getenv(key);
            if (val != null && !val.isEmpty()) {
                return true;
            }
        }

        String androidRoot = System.getenv("ANDROID_ROOT");
        String androidData = System.getenv("ANDROID_DATA");
        if ((androidRoot != null && !androidRoot.isEmpty()) ||
                (androidData != null && !androidData.isEmpty())) {
            return true;
        }

        try {
            if (new File("/system/build.prop").exists()) {
                return true;
            }
        } catch (Exception ignored) {}

        String vendor = System.getProperty("java.vendor", "").toLowerCase();
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        return vendor.contains("android") || vmName.contains("dalvik") || vmName.contains("art");
    }

    private static Path getNativeLibDir() {
        List<Path> candidates = resolveTempRootCandidates();

        for (Path candidate : candidates) {
            if (candidate == null) continue;

            try {
                Path dir = candidate.resolve("litematica_extra");
                Files.createDirectories(dir);

                Path testFile = dir.resolve(".write_test");
                Files.write(testFile, new byte[0]);
                Files.deleteIfExists(testFile);

                LOGGER.debug("Using native library directory: {}", dir);
                return dir;
            } catch (Exception e) {
                LOGGER.trace("Cannot use directory {}: {}", candidate, e.getMessage());
            }
        }

        throw new RuntimeException("No writable directory found for native library extraction");
    }

    private static List<Path> resolveTempRootCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        if (isAndroid) {
            platform = "android";
            addCandidate(candidates, System.getenv("FCL_NATIVEDIR"));
            addCandidate(candidates, System.getenv("POJAV_NATIVEDIR"));
            addCandidate(candidates, System.getenv("MOD_ANDROID_RUNTIME"));
        }

        addCandidate(candidates, FabricLoader.getInstance().getGameDir().toString());

        addCandidate(candidates, System.getProperty("java.io.tmpdir"));

        addCandidate(candidates, System.getProperty("user.home"));
        return List.copyOf(candidates);
    }

    private static void addCandidate(LinkedHashSet<Path> candidates, String path) {
        if (path != null && !path.isEmpty()) {
            try {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    candidates.add(p.toAbsolutePath().normalize());
                }
            } catch (Exception ignored) {}
        }
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