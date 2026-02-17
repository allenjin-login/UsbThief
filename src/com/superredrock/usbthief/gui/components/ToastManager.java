package com.superredrock.usbthief.gui.components;

import com.superredrock.usbthief.gui.I18NManager;

import javax.swing.*;
import java.awt.*;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * Manages toast notifications for the application.
 * Handles toast queuing, stacking, and positioning.
 */
public class ToastManager {

    private static final Logger logger = Logger.getLogger(ToastManager.class.getName());
    private static volatile ToastManager INSTANCE;

    private final Deque<Toast> activeToasts = new LinkedList<>();
    private static final int TOAST_MARGIN = 10;

    private ToastManager() {
        // Singleton
    }

    /**
     * Get the singleton instance.
     *
     * @return the ToastManager instance
     */
    public static synchronized ToastManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ToastManager();
        }
        return INSTANCE;
    }

    /**
     * Show a success toast notification.
     *
     * @param parent  the parent window
     * @param message the message to display
     */
    public static void showSuccess(Window parent, String message) {
        getInstance().showToast(parent, Toast.Type.SUCCESS, message);
    }

    /**
     * Show an error toast notification.
     *
     * @param parent  the parent window
     * @param message the message to display
     */
    public static void showError(Window parent, String message) {
        getInstance().showToast(parent, Toast.Type.ERROR, message);
    }

    /**
     * Show a warning toast notification.
     *
     * @param parent  the parent window
     * @param message the message to display
     */
    public static void showWarning(Window parent, String message) {
        getInstance().showToast(parent, Toast.Type.WARNING, message);
    }

    /**
     * Show an info toast notification.
     *
     * @param parent  the parent window
     * @param message the message to display
     */
    public static void showInfo(Window parent, String message) {
        getInstance().showToast(parent, Toast.Type.INFO, message);
    }

    /**
     * Show a toast with i18n message key.
     *
     * @param parent  the parent window
     * @param type    the toast type
     * @param i18nKey the i18n message key
     * @param args    optional arguments for message formatting
     */
    public static void showI18n(Window parent, Toast.Type type, String i18nKey, Object... args) {
        String message = I18NManager.getInstance().getMessage(i18nKey, args);
        getInstance().showToast(parent, type, message);
    }

    /**
     * Show a toast notification.
     *
     * @param parent  the parent window
     * @param type    the toast type
     * @param message the message to display
     */
    public void showToast(Window parent, Toast.Type type, String message) {
        showToast(parent, type, message, 3000);
    }

    /**
     * Show a toast notification with custom duration.
     *
     * @param parent   the parent window
     * @param type     the toast type
     * @param message  the message to display
     * @param duration display duration in milliseconds
     */
    public void showToast(Window parent, Toast.Type type, String message, int duration) {
        SwingUtilities.invokeLater(() -> {
            // Calculate stack offset
            int offset = calculateStackOffset();

            // Create and show toast
            Toast toast = new Toast(parent, type, message, duration);
            toast.setVerticalOffset(offset);
            
            // Track active toast
            activeToasts.addLast(toast);

            // Show with animation
            toast.show();

            logger.fine("Showing toast: " + type + " - " + message);
        });
    }

    /**
     * Calculate the vertical offset for stacking toasts.
     *
     * @return the offset from the bottom of the window
     */
    private int calculateStackOffset() {
        int offset = 0;
        for (Toast toast : activeToasts) {
            offset += toast.getToastHeight();
        }
        return offset;
    }

    /**
     * Clear all active toasts.
     */
    public void clearAll() {
        SwingUtilities.invokeLater(() -> {
            for (Toast toast : activeToasts) {
                toast.dismiss();
            }
            activeToasts.clear();
        });
    }
}
