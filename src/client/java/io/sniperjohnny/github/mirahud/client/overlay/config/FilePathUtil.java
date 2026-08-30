package io.sniperjohnny.github.mirahud.client.overlay.config;

import io.sniperjohnny.github.mirahud.MiraHUD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class FilePathUtil {

    private FilePathUtil() {}

    public static String resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return rawPath;

        Path p;
        try {
            p = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return searchByFilename(rawPath);
        }
        if (Files.exists(p)) {
            return toRealOrAbsolute(p);
        }

        try {
            String canonical = new java.io.File(rawPath).getCanonicalPath();
            Path cp = Paths.get(canonical);
            if (Files.exists(cp)) {
                MiraHUD.LOGGER.info("FilePathUtil: canonical path resolved: {} -> {}", rawPath, canonical);
                return toRealOrAbsolute(cp);
            }
        } catch (Exception ignored) {}

        return searchByFilename(rawPath);
    }

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
                MiraHUD.LOGGER.info("FilePathUtil: found by search: {} -> {}", rawPath, found);
                return found;
            }
        }
        MiraHUD.LOGGER.warn("FilePathUtil: could not resolve path: {}", rawPath);
        return rawPath;
    }

    private static String toRealOrAbsolute(Path p) {
        try {
            return p.toRealPath().toString();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize().toString();
        }
    }

    private static String searchForFile(String fileName) {
        String userHome = System.getProperty("user.home");
        if (userHome == null) return null;

        Path home = Paths.get(userHome);

        String[] shallowDirs = {"Videos", "Video", "Movies", "Desktop", "Downloads", "Documents"};
        for (String dir : shallowDirs) {
            Path found = findFile(home.resolve(dir), fileName, 2, 500);
            if (found != null) return found.toString();
        }

        Path found = findFile(home, fileName, 4, 2000);
        if (found != null) return found.toString();

        return null;
    }

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
