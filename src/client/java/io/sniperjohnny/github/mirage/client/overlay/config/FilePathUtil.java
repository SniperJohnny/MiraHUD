package io.sniperjohnny.github.mirage.client.overlay.config;

import io.sniperjohnny.github.mirage.Mirage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Robust file-path resolution utility.
 * <p>
 * Tries multiple strategies to turn a potentially-truncated or short-name
 * path into a real, absolute path that actually exists on disk.
 */
public final class FilePathUtil {

    private FilePathUtil() {} // utility class

    /**
     * Resolve a path to its absolute, canonical, real form.
     * <ol>
     *   <li>Try the path as-is (resolved to absolute)</li>
     *   <li>Try {@link Path#toRealPath()} (follows symlinks, resolves case)</li>
     *   <li>Try {@link java.io.File#getCanonicalPath()} (legacy fallback)</li>
     *   <li>If the file doesn't exist at the path, extract the filename
     *       and search the user's home directory (shallow, then deep)</li>
     * </ol>
     *
     * @param rawPath the path as provided by the file picker or config
     * @return the resolved absolute path, or the original if all attempts fail
     */
    public static String resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return rawPath;

        // 1. If the path already works as-is (resolved to absolute), return it
        Path p;
        try {
            p = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            // Invalid path characters — skip to search by filename
            return searchByFilename(rawPath);
        }
        if (Files.exists(p)) {
            return toRealOrAbsolute(p);
        }

        // 2. Try legacy canonical path (handles Windows 8.3 short names)
        try {
            String canonical = new java.io.File(rawPath).getCanonicalPath();
            Path cp = Paths.get(canonical);
            if (Files.exists(cp)) {
                Mirage.LOGGER.info("FilePathUtil: canonical path resolved: {} -> {}", rawPath, canonical);
                return toRealOrAbsolute(cp);
            }
        } catch (Exception ignored) {}

        // 3. Extract filename and search common locations
        return searchByFilename(rawPath);
    }

    /** Fall back to searching by filename when the raw path doesn't exist. */
    private static String searchByFilename(String rawPath) {
        Path p;
        try {
            p = Paths.get(rawPath);
        } catch (Exception e) {
            return rawPath;
        }
        String fileName = p.getFileName() != null ? p.getFileName().toString() : "";
        if (!fileName.isEmpty() && fileName.contains(".")) {
            String found = searchForFile(fileName);
            if (found != null) {
                Mirage.LOGGER.info("FilePathUtil: found by search: {} -> {}", rawPath, found);
                return found;
            }
        }
        Mirage.LOGGER.warn("FilePathUtil: could not resolve path: {}", rawPath);
        return rawPath;
    }

    /** Convert a Path to its real path, or absolute path if real fails. */
    private static String toRealOrAbsolute(Path p) {
        try {
            return p.toRealPath().toString();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize().toString();
        }
    }

    /**
     * Search for a file by name starting from the user's home directory.
     * First does a shallow search (common video folders), then a deeper search.
     */
    private static String searchForFile(String fileName) {
        String userHome = System.getProperty("user.home");
        if (userHome == null) return null;

        Path home = Paths.get(userHome);

        // Shallow search: common media folders
        String[] shallowDirs = {"Videos", "Video", "Movies", "Desktop", "Downloads", "Documents"};
        for (String dir : shallowDirs) {
            Path found = findFile(home.resolve(dir), fileName, 2, 500);
            if (found != null) return found.toString();
        }

        // Deeper search from home (limited depth)
        Path found = findFile(home, fileName, 4, 2000);
        if (found != null) return found.toString();

        return null;
    }

    /**
     * Walk a directory tree looking for a file by exact name match.
     * @param root     starting directory
     * @param name     file name to match
     * @param maxDepth maximum directory depth
     * @param maxFiles maximum files to scan before giving up
     * @return the found path, or null
     */
    private static Path findFile(Path root, String name, int maxDepth, int maxFiles) {
        if (!Files.exists(root) || !Files.isDirectory(root)) return null;

        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream
                    .limit(maxFiles)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
