package com.superredrock.usbthief.gui.dailog.config;

/**
 * Leaf entry of the preferences tree: a localized display name, the i18n key
 * identifying the configuration category and the localized name of the group it
 * is shown under.
 *
 * <p>Extracted verbatim from {@code ConfigDialog} (architecture-audit [19]).</p>
 */
public class CategoryNode {

    private final String displayName;
    private final String i18nKey;
    private final String groupName;

    public CategoryNode(String displayName, String i18nKey, String groupName) {
        this.displayName = displayName;
        this.i18nKey = i18nKey;
        this.groupName = groupName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getI18nKey() {
        return i18nKey;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isLeaf() {
        return true;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
