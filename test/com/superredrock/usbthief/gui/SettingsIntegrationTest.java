package com.superredrock.usbthief.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Settings menu items, including Storage Management.
 * Tests that menu items are properly integrated and open dialogs.
 */
class SettingsIntegrationTest {

    /**
     * Test that MainFrame can be created and has the Config menu.
     */
    @Test
    void mainFrameHasConfigMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            I18NManager i18n = I18NManager.getInstance();
            // Need to make frame visible for JMenuBar to be accessible
            frame.setVisible(false);

            // Get the menu bar - MainFrame doesn't use setJMenuBar(), so we need to search components
            JMenuBar menuBar = findMenuBar(frame);
            assertNotNull(menuBar, "Menu bar should exist");

            // Find the Config menu using i18n
            JMenu configMenu = findMenu(menuBar, i18n.getMessage("menu.config"));
            assertNotNull(configMenu, "Config menu should exist");

            frame.dispose();
        });
    }

    /**
     * Test that Storage Management menu item exists in Config menu.
     */
    @Test
    void storageManagementMenuItemExists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            I18NManager i18n = I18NManager.getInstance();

            // Need to make frame visible for JMenuBar to be accessible
            frame.setVisible(false);

            JMenuBar menuBar = findMenuBar(frame);
            JMenu configMenu = findMenu(menuBar, i18n.getMessage("menu.config"));

            assertNotNull(configMenu, "Config menu should exist");

            // Find the Storage Management menu item
            JMenuItem storageItem = null;
            for (int i = 0; i < configMenu.getItemCount(); i++) {
                JMenuItem item = configMenu.getItem(i);
                if (item != null && item.getText().equals(i18n.getMessage("menu.config.storageManagement"))) {
                    storageItem = item;
                    break;
                }
            }

            assertNotNull(storageItem,
                "Storage Management menu item should exist in Config menu");

            // Verify the item has an action listener
            assertTrue(storageItem.getActionListeners().length > 0,
                "Storage Management item should have an action listener");

            frame.dispose();
        });
    }

    /**
     * Test that other Config menu items still exist (regression test).
     */
    @Test
    void otherConfigMenuItemsExist() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            I18NManager i18n = I18NManager.getInstance();

            // Need to make frame visible for JMenuBar to be accessible
            frame.setVisible(false);

            JMenuBar menuBar = findMenuBar(frame);
            JMenu configMenu = findMenu(menuBar, i18n.getMessage("menu.config"));

            assertNotNull(configMenu, "Config menu should exist");

            // Verify expected menu items exist
            assertNotNull(findMenuItem(configMenu, i18n.getMessage("menu.config.preferences")),
                "Preferences item should exist");
            assertNotNull(findMenuItem(configMenu, i18n.getMessage("menu.config.clearCache")),
                "Clear Cache item should exist");
            assertNotNull(findMenuItem(configMenu, i18n.getMessage("menu.config.clearStats")),
                "Clear Statistics item should exist");
            assertNotNull(findMenuItem(configMenu, i18n.getMessage("menu.config.storageManagement")),
                "Storage Management item should exist");

            frame.dispose();
        });
    }

    /**
     * Test that Config menu structure is intact (check separators and order).
     */
    @Test
    void configMenuStructureIsCorrect() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            I18NManager i18n = I18NManager.getInstance();

            // Need to make frame visible for JMenuBar to be accessible
            frame.setVisible(false);

            JMenuBar menuBar = findMenuBar(frame);
            JMenu configMenu = findMenu(menuBar, i18n.getMessage("menu.config"));

            assertNotNull(configMenu, "Config menu should exist");

            // Verify Storage Management is before the separator
            int storageIndex = findMenuItemIndex(configMenu, i18n.getMessage("menu.config.storageManagement"));
            int separatorIndex = findSeparatorIndex(configMenu, storageIndex);

            assertTrue(storageIndex > -1, "Storage Management item should exist");
            assertTrue(separatorIndex > -1, "Separator should exist after Storage Management");
            assertTrue(separatorIndex > storageIndex,
                "Separator should come after Storage Management");

            frame.dispose();
        });
    }

    /**
     * Helper method to find the JMenuBar in the frame.
     * MainFrame doesn't use setJMenuBar(), so we need to search the component hierarchy.
     */
    private JMenuBar findMenuBar(JFrame frame) {
        // Search in the NORTH component where MainFrame adds the menu bar
        for (Component comp : frame.getContentPane().getComponents()) {
            if (comp instanceof JMenuBar) {
                return (JMenuBar) comp;
            }
        }
        return null;
    }

    /**
     * Helper method to find a menu by text.
     */
    private JMenu findMenu(JMenuBar menuBar, String text) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && menu.getText().equals(text)) {
                return menu;
            }
        }
        return null;
    }

    /**
     * Helper method to find a menu item by text.
     */
    private JMenuItem findMenuItem(JMenu menu, String text) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && item.getText().equals(text)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Helper method to find the index of a menu item by text.
     */
    private int findMenuItemIndex(JMenu menu, String text) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && item.getText().equals(text)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Helper method to find a separator index after a given start index.
     * Note: In JMenu, separators are often stored as null entries.
     */
    private int findSeparatorIndex(JMenu menu, int startIndex) {
        for (int i = startIndex + 1; i < menu.getItemCount(); i++) {
            // In JMenu, separator entries are null
            if (menu.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }
}
