package com.superredrock.usbthief.gui.dailog.config;

import com.superredrock.usbthief.core.config.configs.BlacklistConfig;
import com.superredrock.usbthief.core.config.configs.DeviceScannerConfig;
import com.superredrock.usbthief.core.config.configs.FileCopyConfig;
import com.superredrock.usbthief.core.config.configs.FileFilterConfig;
import com.superredrock.usbthief.core.config.configs.FileWatchConfig;
import com.superredrock.usbthief.core.config.configs.IndexConfig;
import com.superredrock.usbthief.core.config.configs.OverwriteConfig;
import com.superredrock.usbthief.core.config.configs.PathConfig;
import com.superredrock.usbthief.core.config.configs.RateLimitConfig;
import com.superredrock.usbthief.core.config.configs.StatisticsApiConfig;
import com.superredrock.usbthief.core.config.configs.StorageConfig;
import com.superredrock.usbthief.core.config.configs.SuffixFilterConfig;
import com.superredrock.usbthief.core.config.configs.ThreadPoolConfig;
import com.superredrock.usbthief.core.config.configs.UIConfig;
import com.superredrock.usbthief.core.config.configs.WindowConfig;
import com.superredrock.usbthief.gui.I18nManager;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static description of the preferences tree: which groups exist, which
 * categories they contain and how an i18n category key maps onto the category
 * constant used by {@code ConfigSchema}.
 *
 * <p>Extracted from {@code ConfigDialog} where the same nine group definitions
 * were written out twice — once for the initial tree and once for every rebuild
 * (architecture-audit [19]).</p>
 */
final class ConfigCategories {

    /**
     * Each row starts with the group's i18n key and continues with the i18n keys
     * of its category leaves. A leaf key doubles as its own label key
     * ({@code config.category.*}), so every category is listed exactly once.
     */
    private static final String[][] GROUPS = {
        {"config.group.general", "config.category.scanner"},
        {"config.group.file",
            "config.category.fileCopy", "config.category.fileWatch", "config.category.fileFilter",
            "config.category.suffixFilter", "config.category.overwriteStrategy"},
        {"config.group.index", "config.category.index"},
        {"config.group.rateLimit", "config.category.rateLimit"},
        {"config.group.paths", "config.category.paths"},
        {"config.group.ui", "config.category.ui", "config.category.window"},
        {"config.group.security", "config.category.blacklist"},
        {"config.group.storage", "config.category.storage"},
        // UI-14: performance-related thread pool settings belong under "Advanced", not "General"
        {"config.group.advanced", "config.category.threadPool", "config.category.statisticsApi"},
    };

    /** i18n category key to the category constant registered in {@code ConfigSchema}. */
    private static final Map<String, String> CATEGORY_BY_KEY = new HashMap<>();

    static {
        CATEGORY_BY_KEY.put("config.category.threadPool", ThreadPoolConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.scanner", DeviceScannerConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.index", IndexConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.fileCopy", FileCopyConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.fileWatch", FileWatchConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.rateLimit", RateLimitConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.paths", PathConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.ui", UIConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.window", WindowConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.blacklist", BlacklistConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.fileFilter", FileFilterConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.suffixFilter", SuffixFilterConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.storage", StorageConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.statisticsApi", StatisticsApiConfig.CATEGORY);
        CATEGORY_BY_KEY.put("config.category.overwriteStrategy", OverwriteConfig.CATEGORY);
    }

    private ConfigCategories() {
    }

    /**
     * Build a fresh, fully populated tree root. Called for the initial tree and
     * for every rebuild after a search, so both always agree.
     */
    static DefaultMutableTreeNode buildRootNode() {
        I18nManager i18n = I18nManager.getInstance();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (String[] group : GROUPS) {
            String groupName = i18n.getMessage(group[0]);
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupName);
            for (int i = 1; i < group.length; i++) {
                String categoryKey = group[i];
                groupNode.add(new DefaultMutableTreeNode(
                        new CategoryNode(i18n.getMessage(categoryKey), categoryKey, groupName)));
            }
            root.add(groupNode);
        }
        return root;
    }

    /**
     * All category i18n keys, in tree order.
     */
    static List<String> categoryKeys() {
        List<String> keys = new ArrayList<>();
        for (String[] group : GROUPS) {
            for (int i = 1; i < group.length; i++) {
                keys.add(group[i]);
            }
        }
        return keys;
    }

    /**
     * Resolve an i18n category key to the category constant used by
     * {@code ConfigSchema}.
     *
     * @throws IllegalArgumentException when the key does not name a known category
     */
    static String resolveName(String i18nKey) {
        String categoryName = CATEGORY_BY_KEY.get(i18nKey);
        if (categoryName == null) {
            throw new IllegalArgumentException("Unknown category key: " + i18nKey);
        }
        return categoryName;
    }
}
