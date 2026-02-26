package com.superredrock.usbthief.gui;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class I18NManager {

    private static final Logger logger = Logger.getLogger(I18NManager.class.getName());
    private static final String BUNDLE_NAME = "com.superredrock.usbthief.gui.messages";

    private static volatile I18NManager INSTANCE;
    private Locale currentLocale;
    private ResourceBundle resourceBundle;
    private final CopyOnWriteArrayList<LocaleChangeListener> listeners = new CopyOnWriteArrayList<>();
    private List<LanguageInfo> availableLanguages;

    public interface LocaleChangeListener {
        void onLocaleChanged(Locale newLocale);
    }

    private I18NManager() {
        this.availableLanguages = LanguageDiscovery.discoverLanguages();
        this.currentLocale = loadSavedLocale();
        loadResourceBundle();
    }

    /**
     * Load saved locale from LanguageConfig, or use system default.
     */
    private Locale loadSavedLocale() {
        LanguageConfig config = new LanguageConfig();
        String savedLanguage = config.getDefaultLanguage();
        if (savedLanguage != null && !savedLanguage.isEmpty()) {
            Locale savedLocale = parseLocale(savedLanguage);
            if (savedLocale != null) {
                logger.info("Using saved language preference: " + savedLanguage);
                return savedLocale;
            }
        }
        logger.info("Using system default locale: " + Locale.getDefault());
        return Locale.getDefault();
    }

    /**
     * Parse locale string (e.g., "zh_CN") to Locale object.
     */
    private Locale parseLocale(String localeStr) {
        String[] parts = localeStr.split("_");
        return switch (parts.length) {
            case 1 -> new Locale(parts[0]);
            case 2 -> new Locale(parts[0], parts[1]);
            case 3 -> new Locale(parts[0], parts[1], parts[2]);
            default -> null;
        };
    }

    public List<LanguageInfo> getAvailableLanguages() {
        return new java.util.ArrayList<>(availableLanguages);
    }

    public void refreshAvailableLanguages() {
        this.availableLanguages = LanguageDiscovery.discoverLanguages();
        logger.info("Refreshed available languages: " + availableLanguages.size());
        notifyLanguageListChanged();
    }

    private final CopyOnWriteArrayList<LanguageListChangeListener> languageListListeners = new CopyOnWriteArrayList<>();

    public interface LanguageListChangeListener {
        void onLanguageListChanged(List<LanguageInfo> languages);
    }

    public void addLanguageListChangeListener(LanguageListChangeListener listener) {
        if (listener != null && !languageListListeners.contains(listener)) {
            languageListListeners.add(listener);
        }
    }

    public void removeLanguageListChangeListener(LanguageListChangeListener listener) {
        languageListListeners.remove(listener);
    }

    private void notifyLanguageListChanged() {
        for (LanguageListChangeListener listener : languageListListeners) {
            try {
                listener.onLanguageListChanged(new ArrayList<>(availableLanguages));
            } catch (Exception e) {
                logger.warning("Error notifying language list change listener: " + e.getMessage());
            }
        }
    }

    public static I18NManager getInstance() {
        if (INSTANCE == null) {
            synchronized (I18NManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new I18NManager();
                }
            }
        }
        return INSTANCE;
    }

    private void loadResourceBundle() {
        try {
            resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
            logger.info("Loaded resource bundle for locale: " + currentLocale);
            String testValue = resourceBundle.getString("main.title");
            logger.info("Test message 'main.title': " + testValue);
        } catch (MissingResourceException e) {
            logger.severe("Failed to load resource bundle: " + e.getMessage());
            resourceBundle = null;
        }
    }

    public void addLocaleChangeListener(LocaleChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeLocaleChangeListener(LocaleChangeListener listener) {
        listeners.remove(listener);
    }

    public void setLocale(Locale locale) {
        if (!this.currentLocale.equals(locale)) {
            Locale oldLocale = this.currentLocale;
            this.currentLocale = locale;
            loadResourceBundle();
            logger.info("Locale changed from " + oldLocale + " to " + locale);
            notifyLocaleChanged();
        }
    }

    private void notifyLocaleChanged() {
        for (LocaleChangeListener listener : listeners) {
            try {
                listener.onLocaleChanged(currentLocale);
            } catch (Exception e) {
                logger.warning("Error notifying locale change listener: " + e.getMessage());
            }
        }
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public String getMessage(String key) {
        if (resourceBundle == null) {
            logger.warning("Resource bundle not loaded, returning key: " + key);
            return "!" + key + "!";
        }
        try {
            String value = resourceBundle.getString(key);
            if (key.equals("main.title") || key.equals("tab.events")) {
                logger.fine("getMessage(" + key + ") = " + value);
            }
            return value;
        } catch (MissingResourceException e) {
            logger.warning("Missing resource for key: " + key);
            return "!" + key + "!";
        }
    }

    public String getMessage(String key, Object... args) {
        String message = getMessage(key);
        if (args.length == 0) {
            return message;
        }
        return MessageFormat.format(message, args);
    }
}
