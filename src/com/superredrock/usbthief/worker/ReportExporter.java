package com.superredrock.usbthief.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Renders copy history as a CSV report and writes it to disk.
 *
 * <p>Formatting and I/O are deliberately separate: {@link #toCsv(List, List, Function)} is a pure
 * function (records in, text out) so the escaping and layout rules are unit-testable without
 * touching the file system, and {@link #writeToFile(Path, String)} is the only method that
 * performs I/O.</p>
 *
 * <p>The output follows RFC 4180 - {@code CRLF} record separators and double-quote escaping for
 * fields containing a comma, a quote or a line break - and is written as UTF-8 with a BOM, so
 * Excel on Windows detects the encoding instead of mangling non-ASCII labels and paths.</p>
 *
 * <p>Column headers are supplied by the caller (localised by the UI layer) rather than hard-coded
 * here, which keeps this class free of any dependency on the GUI package.</p>
 *
 * <p><b>Not implemented on purpose:</b> spreadsheet formula injection. A file named
 * {@code =HYPERLINK(...)} on a plugged-in drive ends up in the report verbatim, so Excel may
 * evaluate it when the report is opened. The usual countermeasure is to prefix such values with
 * an apostrophe, which would silently alter the file name the operator is trying to read.
 * Output here stays faithful to the RFC 4180 rule and to the source data; sanitising the report
 * for spreadsheet consumption is a product decision, not a formatting one.</p>
 */
public final class ReportExporter {

    /** RFC 4180 field separator. */
    public static final char DELIMITER = ',';
    /** RFC 4180 record separator. */
    public static final String RECORD_SEPARATOR = "\r\n";
    /** UTF-8 byte order mark, emitted once at the start of the file for Excel compatibility. */
    public static final String UTF8_BOM = "\uFEFF";
    /** Suggested file extension for exported reports. */
    public static final String FILE_EXTENSION = ".csv";

    /**
     * Localisation keys of the report columns, in the exact order {@link #fields} emits them.
     * Declared here so the header row and the data rows cannot drift apart unnoticed; the UI
     * resolves them through its own bundle, this class never looks them up.
     */
    public static final List<String> COLUMN_KEYS = List.of(
            "export.column.time",
            "export.column.volume",
            "export.column.sourceFile",
            "export.column.destination",
            "export.column.sizeBytes",
            "export.column.durationMs",
            "export.column.speed",
            "export.column.status");

    /** Prefix of the localisation key holding the display text of a {@link CopyResult}. */
    public static final String STATUS_KEY_PREFIX = "export.status.";

    /**
     * @param result a copy outcome
     * @return the localisation key holding that outcome's display text
     */
    public static String statusKey(CopyResult result) {
        return STATUS_KEY_PREFIX + (result == null ? "" : result.name().toLowerCase(Locale.ROOT));
    }

    private ReportExporter() {
    }

    /**
     * Escapes one field for RFC 4180 output.
     *
     * <p>A field is wrapped in double quotes when it contains the delimiter, a double quote or a
     * line break; embedded double quotes are doubled. Everything else is passed through
     * unchanged so ordinary values stay readable in the raw file.</p>
     *
     * @param value the raw field, {@code null} is treated as an empty field
     * @return the field as it must appear between delimiters
     */
    public static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuoting = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == DELIMITER || c == '"' || c == '\n' || c == '\r') {
                needsQuoting = true;
                break;
            }
        }
        if (!needsQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * Renders the header row followed by one row per record.
     *
     * @param entries      the records to export, may be empty
     * @param header       column titles, one per column; already localised by the caller
     * @param statusLabels maps a copy result to its display text
     * @return the complete CSV document, without the BOM
     */
    public static String toCsv(List<CopyHistoryEntry> entries, List<String> header,
                               Function<CopyResult, String> statusLabels) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, header);
        if (entries != null) {
            for (CopyHistoryEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                appendRow(csv, fields(entry, statusLabels));
            }
        }
        return csv.toString();
    }

    /**
     * Renders the report with the enum name as the status text.
     *
     * @param entries the records to export, may be empty
     * @param header  column titles, one per column
     * @return the complete CSV document, without the BOM
     */
    public static String toCsv(List<CopyHistoryEntry> entries, List<String> header) {
        return toCsv(entries, header, result -> result != null ? result.name() : "");
    }

    /**
     * @param target the file to write
     * @param csv    the CSV document, as returned by {@link #toCsv(List, List)}
     * @throws IOException if the file cannot be written
     */
    public static void writeToFile(Path target, String csv) throws IOException {
        String body = csv == null ? "" : csv;
        Files.write(target, (UTF8_BOM + body).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Renders and writes the report in one step.
     *
     * @param target  the file to write
     * @param entries the records to export
     * @param header  column titles, one per column
     * @return the number of data rows written (header excluded)
     * @throws IOException if the file cannot be written
     */
    public static int export(Path target, List<CopyHistoryEntry> entries, List<String> header)
            throws IOException {
        return export(target, entries, header, result -> result != null ? result.name() : "");
    }

    /**
     * Renders and writes the report in one step, with localised status text.
     *
     * @param target       the file to write
     * @param entries      the records to export
     * @param header       column titles, one per column
     * @param statusLabels maps a copy result to its display text
     * @return the number of data rows written (header excluded)
     * @throws IOException if the file cannot be written
     */
    public static int export(Path target, List<CopyHistoryEntry> entries, List<String> header,
                             Function<CopyResult, String> statusLabels) throws IOException {
        writeToFile(target, toCsv(entries, header, statusLabels));
        return entries == null ? 0 : entries.size();
    }

    /**
     * Builds the fields of one record: time, volume, source file, destination, size, duration,
     * average speed, status.
     *
     * @param entry        the record to render
     * @param statusLabels maps the copy result to its display text
     * @return the raw (unescaped) field values
     */
    static List<String> fields(CopyHistoryEntry entry, Function<CopyResult, String> statusLabels) {
        List<String> fields = new ArrayList<>(8);
        fields.add(entry.formattedTimestamp());
        fields.add(entry.volumeLabel());
        fields.add(entry.sourceFileName());
        fields.add(entry.destinationPath());
        fields.add(Long.toString(entry.sizeBytes()));
        fields.add(Long.toString(entry.durationMillis()));
        fields.add(formatSpeed(entry.megabytesPerSecond()));
        fields.add(statusLabels != null ? statusLabels.apply(entry.result()) : "");
        return fields;
    }

    /**
     * Formats a throughput value with a dot as the decimal separator regardless of the JVM
     * default locale - a comma would silently split the column in a CSV reader.
     *
     * @param megabytesPerSecond throughput in MB/s
     * @return the value with two decimals
     */
    public static String formatSpeed(double megabytesPerSecond) {
        return String.format(Locale.ROOT, "%.2f", megabytesPerSecond);
    }

    private static void appendRow(StringBuilder csv, List<String> fields) {
        List<String> row = fields != null ? fields : List.of();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                csv.append(DELIMITER);
            }
            csv.append(escapeCsv(row.get(i)));
        }
        csv.append(RECORD_SEPARATOR);
    }
}
