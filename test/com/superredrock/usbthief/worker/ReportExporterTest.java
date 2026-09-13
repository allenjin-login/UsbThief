package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV contract of the copy report: RFC 4180 escaping, the one-header-row-plus-one-row-per-record
 * layout, the empty history case and the UTF-8 BOM Excel needs to read Chinese text.
 */
class ReportExporterTest {

    private static final List<String> HEADER = List.of(
            "Time", "Volume", "Source", "Destination", "Size", "Duration", "Speed", "Status");

    private static CopyHistoryEntry entry(String volume, String source, String destination,
                                          long size, long duration, CopyResult result) {
        return new CopyHistoryEntry(1_700_000_000_000L, volume, source, destination, size, duration, result);
    }

    // === RFC 4180 escaping ===

    @Test
    void plainValuesAreNotQuoted() {
        assertEquals("report.csv", ReportExporter.escapeCsv("report.csv"));
        assertEquals("C:\\out\\a.txt", ReportExporter.escapeCsv("C:\\out\\a.txt"));
    }

    @Test
    void valuesContainingTheDelimiterAreQuoted() {
        assertEquals("\"a,b\"", ReportExporter.escapeCsv("a,b"));
    }

    @Test
    void embeddedQuotesAreDoubledAndTheFieldIsQuoted() {
        assertEquals("\"say \"\"hi\"\"\"", ReportExporter.escapeCsv("say \"hi\""));
        assertEquals("\"\"\"\"", ReportExporter.escapeCsv("\""));
    }

    @Test
    void valuesContainingLineBreaksAreQuoted() {
        assertEquals("\"line1\nline2\"", ReportExporter.escapeCsv("line1\nline2"));
        assertEquals("\"line1\r\nline2\"", ReportExporter.escapeCsv("line1\r\nline2"));
        assertEquals("\"trailing\r\"", ReportExporter.escapeCsv("trailing\r"));
    }

    @Test
    void chineseTextIsPassedThroughUnchanged() {
        assertEquals("我的U盘", ReportExporter.escapeCsv("我的U盘"));
        assertEquals("\"报告,最终版.txt\"", ReportExporter.escapeCsv("报告,最终版.txt"));
    }

    @Test
    void nullAndEmptyBecomeEmptyFields() {
        assertEquals("", ReportExporter.escapeCsv(null));
        assertEquals("", ReportExporter.escapeCsv(""));
    }

    // === Layout ===

    @Test
    void csvStartsWithASingleHeaderRow() {
        String csv = ReportExporter.toCsv(List.of(), HEADER);
        assertEquals(String.join(",", HEADER) + ReportExporter.RECORD_SEPARATOR, csv);
    }

    @Test
    void emptyHistoryExportsOnlyTheHeader() {
        String csv = ReportExporter.toCsv(List.of(), HEADER);
        assertEquals(2, csv.split(ReportExporter.RECORD_SEPARATOR, -1).length,
                "header row plus the terminating separator, no data rows");
        assertFalse(csv.contains("\n\n"));
    }

    @Test
    void rowsAreSeparatedByCrlf() {
        String csv = ReportExporter.toCsv(
                List.of(entry("A", "a.txt", "C:\\o\\a.txt", 1, 1, CopyResult.SUCCESS),
                        entry("B", "b.txt", "C:\\o\\b.txt", 2, 2, CopyResult.FAIL)),
                HEADER);
        String[] lines = csv.split(ReportExporter.RECORD_SEPARATOR, -1);
        assertEquals(4, lines.length, "header + 2 records + trailing separator");
        assertTrue(lines[0].startsWith("Time,"));
    }

    @Test
    void eachRecordProducesTheEightDocumentedColumns() {
        CopyHistoryEntry e = new CopyHistoryEntry(
                1_700_000_000_000L, "KINGSTON", "a.txt", "C:\\out\\a.txt",
                1048576L, 1000L, CopyResult.SUCCESS);

        String dataRow = ReportExporter.toCsv(List.of(e), HEADER)
                .split(ReportExporter.RECORD_SEPARATOR)[1];
        String[] columns = dataRow.split(",");

        assertEquals(8, columns.length);
        assertEquals(e.formattedTimestamp(), columns[0]);
        assertEquals("KINGSTON", columns[1]);
        assertEquals("a.txt", columns[2]);
        assertEquals("C:\\out\\a.txt", columns[3]);
        assertEquals("1048576", columns[4]);
        assertEquals("1000", columns[5]);
        assertEquals("1.00", columns[6], "one MiB in one second is 1 MB/s");
        assertEquals("SUCCESS", columns[7]);
    }

    @Test
    void statusColumnUsesTheSuppliedLabels() {
        CopyHistoryEntry failed = entry("A", "a.txt", "C:\\o\\a.txt", 1, 1, CopyResult.FAIL);
        String csv = ReportExporter.toCsv(List.of(failed), HEADER, result -> "失败");
        assertTrue(csv.endsWith("失败" + ReportExporter.RECORD_SEPARATOR));
    }

    @Test
    void headerAndRecordFieldsHaveTheSameWidth() {
        assertEquals(ReportExporter.COLUMN_KEYS.size(),
                ReportExporter.fields(entry("A", "a.txt", "C:\\o\\a.txt", 1, 1, CopyResult.SUCCESS),
                        CopyResult::name).size(),
                "the UI header row and the data rows must describe the same columns");
    }

    @Test
    void everyCopyResultHasAStatusKey() {
        for (CopyResult result : CopyResult.values()) {
            assertEquals("export.status." + result.name().toLowerCase(Locale.ROOT),
                    ReportExporter.statusKey(result));
        }
        assertEquals("export.status.", ReportExporter.statusKey(null));
    }

    // === Locale independence ===

    @Test
    void speedUsesADotEvenWhenTheJvmLocaleUsesAComma() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1.50", ReportExporter.formatSpeed(1.5),
                    "a comma here would silently split the speed into two columns");
        } finally {
            Locale.setDefault(original);
        }
    }

    // === File output ===

    @Test
    void writtenFileStartsWithTheUtf8Bom(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("report.csv");
        ReportExporter.writeToFile(target, ReportExporter.toCsv(List.of(), HEADER));

        byte[] bytes = Files.readAllBytes(target);
        assertTrue(bytes.length >= 3);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
    }

    @Test
    void writtenReportRoundTripsAsUtf8WithChineseContent(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("报告.csv");
        CopyHistoryEntry e = entry("我的U盘", "报告,最终版.txt", "C:\\目标\\报告.txt", 2048L, 500L, CopyResult.FAIL);

        int written = ReportExporter.export(target, List.of(e), HEADER, result -> result.name());

        assertEquals(1, written);
        String text = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(ReportExporter.UTF8_BOM), "Excel needs the BOM to detect UTF-8");
        assertTrue(text.contains("我的U盘"));
        assertTrue(text.contains("\"报告,最终版.txt\""), "a comma in the name must be quoted");
        assertTrue(text.contains("C:\\目标\\报告.txt"));
    }

    @Test
    void emptyHistoryStillWritesAHeaderOnlyFile(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("empty.csv");

        int written = ReportExporter.export(target, List.of(), HEADER);

        assertEquals(0, written);
        String text = Files.readString(target, StandardCharsets.UTF_8);
        assertEquals(ReportExporter.UTF8_BOM + String.join(",", HEADER) + ReportExporter.RECORD_SEPARATOR,
                text);
    }

    @Test
    void exportToleratesANullRecordList(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("null.csv");
        assertEquals(0, ReportExporter.export(target, null, HEADER));
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("Status"));
    }
}
