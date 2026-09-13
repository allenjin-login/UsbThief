package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.worker.CopyResult;
import com.superredrock.usbthief.worker.ReportExporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export menu item, its file chooser and every column of the generated report are user-facing
 * text: a key missing from one bundle silently degrades to the raw key. Each bundle file is read
 * directly rather than through {@code ResourceBundle}, whose fallback to the default bundle would
 * hide exactly that omission.
 */
class ExportReportI18nTest {

    /** The five shipped bundles, in the same order as the default/en/zh/ja/de naming scheme. */
    private static final List<String> BUNDLES = List.of(
            "messages.properties",
            "messages_en.properties",
            "messages_zh.properties",
            "messages_ja.properties",
            "messages_de.properties");

    private static final List<String> UI_KEYS = List.of(
            "menu.action.exportReport",
            "export.dialog.title",
            "export.dialog.filter",
            "export.dialog.defaultName",
            "export.success.title",
            "export.success.message",
            "export.empty.title",
            "export.empty.message",
            "export.failed.title",
            "export.failed.message");

    @Test
    void everyBundleTranslatesTheExportUiText() throws IOException {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            List<String> missing = new ArrayList<>();
            for (String key : UI_KEYS) {
                if (!isTranslated(properties, key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), bundle + " is missing " + missing);
        }
    }

    @Test
    void everyBundleTranslatesEveryReportColumn() throws IOException {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (String key : ReportExporter.COLUMN_KEYS) {
                assertTrue(isTranslated(properties, key), bundle + " is missing column key " + key);
            }
        }
    }

    @Test
    void everyBundleTranslatesEveryCopyStatus() throws IOException {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (CopyResult result : CopyResult.values()) {
                String key = ReportExporter.statusKey(result);
                assertTrue(isTranslated(properties, key), bundle + " is missing status key " + key);
            }
        }
    }

    /**
     * The header the menu action builds is exactly {@link ReportExporter#COLUMN_KEYS}; this checks
     * the two lists cannot drift by resolving every key the way the UI does.
     */
    @Test
    void theColumnKeysResolveToDistinctTitles() throws IOException {
        Properties properties = load("messages.properties");
        List<String> titles = new ArrayList<>();
        for (String key : ReportExporter.COLUMN_KEYS) {
            titles.add(properties.getProperty(key));
        }
        assertTrue(ReportExporter.COLUMN_KEYS.size() == titles.size());
        assertTrue(titles.stream().distinct().count() == titles.size(),
                "two columns share a title: " + titles);
    }

    @Test
    void theSuccessMessageReportsACountAndAPath() throws IOException {
        Properties properties = load("messages.properties");
        String message = properties.getProperty("export.success.message");
        assertTrue(message.contains("{0}"), "the record count must be shown");
        assertTrue(message.contains("{1}"), "the target file must be shown");
    }

    private static boolean isTranslated(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value != null && !value.isBlank() && !value.equals(key);
    }

    private static Properties load(String fileName) throws IOException {
        String text = Files.readString(
                Path.of("src", "com", "superredrock", "usbthief", "gui", fileName),
                StandardCharsets.UTF_8);
        // messages_zh.properties carries a UTF-8 BOM; strip it so the first key is not corrupted.
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        assertFalse(properties.isEmpty(), fileName + " did not parse into any property");
        return properties;
    }
}
