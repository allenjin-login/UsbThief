package com.superredrock.usbthief.gui;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class I18NManager {

    private static final Logger logger = LogManager.getLogger(I18NManager.class);
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
                logger.info("Using saved language preference: {}", savedLanguage);
                return savedLocale;
            }
        }
        logger.info("Using system default locale: {}", Locale.getDefault());
        return Locale.getDefault();
    }

    /**
     * Parse locale string (e.g., "zh_CN") to Locale object.
     */
    private Locale parseLocale(String localeStr) {
        String[] parts = localeStr.split("_");
        return switch (parts.length) {
            case 1 -> Locale.of(parts[0]);
            case 2 -> Locale.of(parts[0], parts[1]);
            case 3 -> Locale.of(parts[0], parts[1], parts[2]);
            default -> null;
        };
    }

    public List<LanguageInfo> getAvailableLanguages() {
        return new java.util.ArrayList<>(availableLanguages);
    }

    public void refreshAvailableLanguages() {
        this.availableLanguages = LanguageDiscovery.discoverLanguages();
        logger.info("Refreshed available languages: {}", availableLanguages.size());
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
                logger.warn("Error notifying language list change listener: {}", e);
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
            logger.debug("Loaded resource bundle for locale: {}", currentLocale);
        } catch (MissingResourceException e) {
            logger.error("Failed to load resource bundle: {}", e);
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
            logger.info("Locale changed from {} to {}", oldLocale, locale);
            notifyLocaleChanged();
        }
    }

    private void notifyLocaleChanged() {
        for (LocaleChangeListener listener : listeners) {
            try {
                listener.onLocaleChanged(currentLocale);
            } catch (Exception e) {
                logger.warn("Error notifying locale change listener: {}", e);
            }
        }
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public String getMessage(String key) {
        if (resourceBundle == null) {
            logger.warn("Resource bundle not loaded, returning key: {}", key);
            return "!" + key + "!";
        }
        try {
            String value = resourceBundle.getString(key);
            return value;
        } catch (MissingResourceException e) {
            logger.warn("Missing resource for key: {}", key);
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
