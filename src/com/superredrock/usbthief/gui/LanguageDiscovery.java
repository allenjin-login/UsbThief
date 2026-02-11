package com.superredrock.usbthief.gui;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discovers available language packs by scanning for messages_*.properties files.
 */
public class LanguageDiscovery {

    private static final Logger logger = Logger.getLogger(LanguageDiscovery.class.getName());
    private static final Pattern BUNDLE_PATTERN = Pattern.compile("^messages_([a-z]{2})(?:_([A-Z]{2}))?\\.properties$");

    /**
     * Discover all available languages from the classpath resource directory.
     */
    public static List<LanguageInfo> discoverLanguages() {
        List<LanguageInfo> languages = new ArrayList<>();
        LanguageConfig config = new LanguageConfig();

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
                            logger.fine("Discovered language: " + locale);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            logger.warning("Failed to discover languages: " + e.getMessage());
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
     * Discover languages and return as locale list.
     */
    public static List<Locale> discoverLocales() {
        return discoverLanguages().stream()
                .map(LanguageInfo::locale)
                .collect(Collectors.toList());
    }
}
