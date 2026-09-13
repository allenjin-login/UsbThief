package com.superredrock.usbthief;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-21 regression guard: the production source tree must ship exactly one entry point.
 *
 * <p>The interactive {@code UsbTesting} tool used to live in {@code src/} and was therefore
 * bundled (and launchable) in the shipped module. It now lives in {@code test/}. This test keeps
 * it that way: any new {@code main} method under {@code src/} fails the build until it is moved
 * to {@code test/} or consciously added to the allow-list below.</p>
 */
class SourceTreeHygieneTest {

    private static final Pattern MAIN_DECLARATION =
            Pattern.compile("\\bstatic\\s+void\\s+main\\s*\\(");

    /** The only main method allowed in the production source tree, relative to {@code src/}. */
    private static final Path APP_ENTRY_POINT =
            Path.of("com", "superredrock", "usbthief", "Main.java");

    @Test
    void productionSourceTreeDeclaresOnlyTheAppEntryPoint() throws IOException {
        Path src = Path.of("src");
        assertTrue(Files.isDirectory(src),
                "expected to run from the project root, but " + src.toAbsolutePath() + " is not a directory");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(src)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path file : javaFiles) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith("*") || trimmed.startsWith("//")) {
                        continue;
                    }
                    if (MAIN_DECLARATION.matcher(trimmed).find()) {
                        offenders.add(src.relativize(file).toString());
                        break;
                    }
                }
            }
        }

        assertEquals(List.of(APP_ENTRY_POINT.toString()), offenders,
                "only Main may define main() under src/; test/tool entry points belong in test/");
    }
}
