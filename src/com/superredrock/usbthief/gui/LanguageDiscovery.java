package com.superredrock.usbthief.gui;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discovers available language packs by scanning for messages_*.properties files.
 * Supports both development mode (file system) and packaged JAR mode.
 */
public class LanguageDiscovery {

    private static final Logger logger = Logger.getLogger(LanguageDiscovery.class.getName());
    private static final Pattern BUNDLE_PATTERN = Pattern.compile("^messages_([a-z]{2})(?:_([A-Z]{2}))?\\.properties$");
    private static final Pattern JAR_ENTRY_PATTERN = Pattern.compile("^com/superredrock/usbthief/gui/messages_([a-z]{2})(?:_([A-Z]{2}))?\\.properties$");

    /**
     * Discover all available languages from the classpath resource directory.
     * Works in both development mode and packaged JAR mode.
     */
    public static List<LanguageInfo> discoverLanguages() {
        List<LanguageInfo> languages = new ArrayList<>();
        LanguageConfig config = new LanguageConfig();

        // Try development mode first (file system)
        if (tryDevMode(languages)) {
            logger.fine("Discovered languages from file system");
        } else {
            // Try JAR mode
            tryJarMode(languages);
        }

        // Always include default (English) language
        boolean hasEnglish = languages.stream()
                .anyMatch(lang -> lang.locale().getLanguage().equals("en"));
        if (!hasEnglish) {
            languages.add(new LanguageInfo(Locale.ENGLISH));
        }

        return config.applyConfig(languages);
    }

    /**
     * Try to discover languages from file system (development mode).
     * @return true if successful
     */
    private static boolean tryDevMode(List<LanguageInfo> languages) {
        try {
            Path guiPath = Path.of("src/com/superredrock/usbthief/gui");
            if (Files.exists(guiPath)) {
                Files.walkFileTree(guiPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String fileName = file.getFileName().toString();
                        Matcher matcher = BUNDLE_PATTERN.matcher(fileName);

                        if (matcher.matches()) {
                            String language = matcher.group(1);
                            String country = matcher.group(2);

                            Locale locale = country != null
                                    ? new Locale(language, country)
                                    : new Locale(language);

                            LanguageInfo info = new LanguageInfo(locale);
                            languages.add(info);
                            logger.fine("Discovered language from file system: " + locale);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
                return !languages.isEmpty();
            }
        } catch (IOException e) {
            logger.fine("File system discovery failed (expected in JAR mode): " + e.getMessage());
        }
        return false;
    }

    /**
     * Try to discover languages from JAR file (packaged mode).
     */
    private static void tryJarMode(List<LanguageInfo> languages) {
        try {
            // Get the JAR location
            URL jarUrl = LanguageDiscovery.class.getProtectionDomain().getCodeSource().getLocation();
            logger.fine("CodeSource location: " + jarUrl);

            Path jarPath;
            try {
                jarPath = Path.of(jarUrl.toURI());
            } catch (URISyntaxException e) {
                // Handle Windows file URLs with spaces
                jarPath = Path.of(jarUrl.getPath());
            }

            logger.fine("JAR path: " + jarPath);

            if (Files.isRegularFile(jarPath) && jarPath.toString().endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        Matcher matcher = JAR_ENTRY_PATTERN.matcher(name);

                        if (matcher.matches()) {
                            String language = matcher.group(1);
                            String country = matcher.group(2);

                            Locale locale = country != null
                                    ? new Locale(language, country)
                                    : new Locale(language);

                            LanguageInfo info = new LanguageInfo(locale);
                            languages.add(info);
                            logger.fine("Discovered language from JAR: " + locale);
                        }
                    }
                }
            } else if (Files.isDirectory(jarPath)) {
                // Running from classes directory (Maven target/classes)
                Path guiDir = jarPath.resolve("com/superredrock/usbthief/gui");
                if (Files.exists(guiDir)) {
                    try (var stream = Files.list(guiDir)) {
                        stream.filter(Files::isRegularFile)
                                .forEach(file -> {
                                    String fileName = file.getFileName().toString();
                                    Matcher matcher = BUNDLE_PATTERN.matcher(fileName);

                                    if (matcher.matches()) {
                                        String language = matcher.group(1);
                                        String country = matcher.group(2);

                                        Locale locale = country != null
                                                ? new Locale(language, country)
                                                : new Locale(language);

                                        LanguageInfo info = new LanguageInfo(locale);
                                        languages.add(info);
                                        logger.fine("Discovered language from classes directory: " + locale);
                                    }
                                });
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to discover languages from JAR: " + e.getMessage());
        }
    }

    /**
     * Discover languages and return as locale list.
     */
    public static List<Locale> discoverLocales() {
        return discoverLanguages().stream()
                .map(LanguageInfo::locale)
                .collect(Collectors.toList());
    }
}
