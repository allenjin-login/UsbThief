package com.superredrock.usbthief.gui.dailog.filter;

import com.superredrock.usbthief.gui.I18nManager;
import com.superredrock.usbthief.gui.theme.ThemeManager;

import javax.swing.JLabel;
import java.awt.Color;

/**
 * The "this filter will silently skip files" banner at the top of the basic
 * filter page (UI-06). Hidden while no skipping rule is active, so the warning is
 * impossible to miss whenever one is.
 *
 * <p>Extracted from {@code FilterConfigDialog} (architecture-audit [19]).</p>
 */
class FilterWarningBanner extends JLabel {

    private static final I18nManager i18n = I18nManager.getInstance();

    /** Warning colour that stays readable on both the light and the dark theme. */
    private static final Color WARNING_LIGHT = new Color(0xB45309);

    private static final String HTML_OPEN = "<html>";

    FilterWarningBanner() {
        setForeground(ThemeManager.getInstance().isDarkTheme()
            ? ThemeManager.ACCENT_WARNING : WARNING_LIGHT);
        setVisible(false);
    }

    /**
     * Render the warning for the rules that are currently active.
     *
     * @param sizeText human readable size limit, or {@code null} when the size
     *                 filter is disabled
     * @param timeText human readable time window, or {@code null} when the time
     *                 filter is disabled
     */
    void update(String sizeText, String timeText) {
        StringBuilder html = new StringBuilder(HTML_OPEN);
        if (sizeText != null) {
            html.append(i18n.getMessage("filter.warning.size", sizeText));
        }
        if (timeText != null) {
            if (html.length() > HTML_OPEN.length()) {
                html.append("<br>");
            }
            html.append(i18n.getMessage("filter.warning.time", timeText));
        }
        if (html.length() == HTML_OPEN.length()) {
            setVisible(false);
            setText("");
        } else {
            setText(html.append("</html>").toString());
            setVisible(true);
        }
        if (getParent() != null) {
            getParent().revalidate();
        }
    }
}
