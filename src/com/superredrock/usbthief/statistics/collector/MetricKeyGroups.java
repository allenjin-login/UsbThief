package com.superredrock.usbthief.statistics.collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups persisted metric keys by their per-entry prefix, for example {@code vs.3.} or
 * {@code dh.12.}.
 *
 * <p>Collectors used to call {@code store.keys()} once per entry and scan the full key array with
 * {@code startsWith} each time, which made persistence O(entries x keys). Fetching the keys once and
 * grouping them in memory keeps the number of backing-store round trips independent of the number
 * of entries.</p>
 */
final class MetricKeyGroups {

    private MetricKeyGroups() {
    }

    /**
     * Groups every key that starts with {@code keyPrefix} under its entry prefix.
     *
     * @param keys    the full key set, fetched once by the caller
     * @param keyPrefix common prefix of the collector, for example {@code "vs."}
     * @return map from entry prefix (including the trailing dot) to the keys below it
     */
    static Map<String, List<String>> byEntryPrefix(Set<String> keys, String keyPrefix) {
        Map<String, List<String>> groups = new HashMap<>();
        for (String key : keys) {
            if (!key.startsWith(keyPrefix)) {
                continue;
            }
            int entryDot = key.indexOf('.', keyPrefix.length());
            if (entryDot < 0 || !allDigits(key, keyPrefix.length(), entryDot)) {
                continue;
            }
            String entryPrefix = key.substring(0, entryDot + 1);
            groups.computeIfAbsent(entryPrefix, prefix -> new ArrayList<>()).add(key);
        }
        return groups;
    }

    /**
     * Groups keys by the numeric entry index instead of by the textual prefix, so callers can look
     * up "all keys of entry 7" without scanning again.
     *
     * @param keys      the full key set, fetched once by the caller
     * @param keyPrefix common prefix of the collector, for example {@code "vs."}
     * @return map from entry index to the keys below it
     */
    static Map<Integer, List<String>> byEntryIndex(Set<String> keys, String keyPrefix) {
        Map<Integer, List<String>> groups = new HashMap<>();
        for (String key : keys) {
            if (!key.startsWith(keyPrefix)) {
                continue;
            }
            int entryDot = key.indexOf('.', keyPrefix.length());
            if (entryDot < 0 || !allDigits(key, keyPrefix.length(), entryDot)) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(key.substring(keyPrefix.length(), entryDot));
            } catch (NumberFormatException e) {
                continue;
            }
            groups.computeIfAbsent(index, entryIndex -> new ArrayList<>()).add(key);
        }
        return groups;
    }

    private static boolean allDigits(String key, int from, int to) {
        if (from >= to) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
