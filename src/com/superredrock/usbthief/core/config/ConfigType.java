package com.superredrock.usbthief.core.config;

import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Configuration value types with serialization/deserialization logic.
 */
public enum ConfigType {
    INT {
        @Override
        public Object get(Preferences prefs, String key, Object defaultValue) {
            return prefs.getInt(key, (Integer) defaultValue);
        }

        @Override
        public void put(Preferences prefs, String key, Object value) {
            prefs.putInt(key, (Integer) value);
        }
    },
    LONG {
        @Override
        public Object get(Preferences prefs, String key, Object defaultValue) {
            return prefs.getLong(key, (Long) defaultValue);
        }

        @Override
        public void put(Preferences prefs, String key, Object value) {
            prefs.putLong(key, (Long) value);
        }
    },
    BOOLEAN {
        @Override
        public Object get(Preferences prefs, String key, Object defaultValue) {
            return prefs.getBoolean(key, (Boolean) defaultValue);
        }

        @Override
        public void put(Preferences prefs, String key, Object value) {
            prefs.putBoolean(key, (Boolean) value);
        }
    },
    STRING {
        @Override
        public Object get(Preferences prefs, String key, Object defaultValue) {
            return prefs.get(key, (String) defaultValue);
        }

        @Override
        public void put(Preferences prefs, String key, Object value) {
            prefs.put(key, (String) value);
        }
    },
    STRING_LIST {
        private static final String DELIMITER = ";";

        @Override
        public Object get(Preferences prefs, String key, Object defaultValue) {
            String str = prefs.get(key, "");
            if (str.isEmpty()) {
                return defaultValue;
            }
            return Arrays.stream(str.split(DELIMITER))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        @Override
        public void put(Preferences prefs, String key, Object value) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            if (list == null || list.isEmpty()) {
                prefs.put(key, "");
            } else {
                prefs.put(key, String.join(DELIMITER, list));
            }
        }
    };

    /**
     * Get value from preferences.
     */
    public abstract Object get(Preferences prefs, String key, Object defaultValue);

    /**
     * Put value to preferences.
     */
    public abstract void put(Preferences prefs, String key, Object value);
}
