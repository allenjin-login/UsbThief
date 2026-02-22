package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.ConfigSchema;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.worker.CopyTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Dialog for configuring rate limit settings.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Real-time speed statistics display with history chart</li>
 *   <li>Configuration for auto mode and load-based percentages</li>
 *   <li>Support for hot language switching</li>
 * </ul>
 *
 * @since 2026-02-21
 */
public class RateLimitConfigDialog extends JDialog implements I18NManager.LocaleChangeListener {

    private static final Logger logger = Logger.getLogger(RateLimitConfigDialog.class.getName());
    private static final I18NManager i18n = I18NManager.getInstance();
    private final ConfigManager configManager = ConfigManager.getInstance();

    // Speed history for chart (stores last 60 samples = ~1 minute)
    private static final int SPEED_HISTORY_SIZE = 60;
    private final double[] speedHistory = new double[SPEED_HISTORY_SIZE];
    private int speedHistoryIndex = 0;
    private double maxRecordedSpeed = 0.0;

    // Stats panel components
    private JLabel currentSpeedLabel;
    private JLabel maxSpeedLabel;
    private SpeedChartPanel speedChartPanel;
    private JButton resetButton;

    // Config panel components
    private JCheckBox autoModeCheckBox;
    private JSpinner baseRateLimitSpinner;
    private JLabel baseRateLimitLabel;
    private JSlider lowPercentSlider;
    private JLabel lowPercentValueLabel;
    private JSlider mediumPercentSlider;
    private JLabel mediumPercentValueLabel;
    private JSlider highPercentSlider;
    private JLabel highPercentValueLabel;

    // Button panel
    private JButton saveButton;
    private JButton cancelButton;
    private JButton applyButton;

    // Borders for i18n refresh
    private TitledBorder statsBorder;
    private TitledBorder configBorder;

    // Update timer for refreshing stats
    private Timer updateTimer;

    // Store listeners for proper cleanup
    private final ChangeListener sliderChangeListener;

    /**
     * Creates a new rate limit configuration dialog.
     *
     * @param parent parent frame
     */
    public RateLimitConfigDialog(JFrame parent) {
        super(parent, i18n.getMessage("ratelimit.dialog.title"), true);

        // Initialize slider change listener
        sliderChangeListener = _ -> updateSliderLabels();

        setSize(700, 500);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        // Register for locale changes
        i18n.addLocaleChangeListener(this);

        // Create main content with horizontal split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createStatsPanel(), createConfigPanel());
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.5);

        // Create button panel
        JPanel buttonPanel = createButtonPanel();

        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Load current settings
        loadSettings();

        // Start update timer
        startUpdateTimer();
    }

    /**
     * Creates the statistics panel (left side).
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        statsBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR),
                i18n.getMessage("ratelimit.stats.currentSpeed"),
                TitledBorder.LEFT,
                TitledBorder.TOP
        );
        panel.setBorder(BorderFactory.createCompoundBorder(
                statsBorder,
                new EmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Current speed display
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel currentSpeedTitle = new JLabel(i18n.getMessage("ratelimit.stats.currentSpeed") + ":");
        currentSpeedTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        currentSpeedTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        panel.add(currentSpeedTitle, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        currentSpeedLabel = new JLabel("0.00 MB/s");
        currentSpeedLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        currentSpeedLabel.setForeground(ThemeManager.ACCENT_PRIMARY);
        panel.add(currentSpeedLabel, gbc);

        // Max speed display
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.weightx = 0;
        JLabel maxSpeedTitle = new JLabel(i18n.getMessage("ratelimit.stats.maxSpeed") + ":");
        maxSpeedTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        maxSpeedTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        panel.add(maxSpeedTitle, gbc);

        gbc.gridx = 2; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        maxSpeedLabel = new JLabel("0.00 MB/s");
        maxSpeedLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        maxSpeedLabel.setForeground(ThemeManager.ACCENT_SUCCESS);
        panel.add(maxSpeedLabel, gbc);

        // Reset button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        gbc.weightx = 0;
        resetButton = new JButton(i18n.getMessage("ratelimit.stats.resetButton"));
        resetButton.addActionListener(_ -> resetStats());
        panel.add(resetButton, gbc);

        // Speed chart
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        speedChartPanel = new SpeedChartPanel();
        panel.add(speedChartPanel, gbc);

        return panel;
    }

    /**
     * Creates the configuration panel (right side).
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        configBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR),
                i18n.getMessage("ratelimit.config.title"),
                TitledBorder.LEFT,
                TitledBorder.TOP
        );
        panel.setBorder(BorderFactory.createCompoundBorder(
                configBorder,
                new EmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 8, 10, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Auto mode checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        autoModeCheckBox = new JCheckBox(i18n.getMessage("ratelimit.config.autoMode"));
        autoModeCheckBox.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        autoModeCheckBox.setForeground(ThemeManager.TEXT_PRIMARY);
        autoModeCheckBox.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        panel.add(autoModeCheckBox, gbc);

        // Base rate limit spinner
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.weightx = 0;
        baseRateLimitLabel = new JLabel(i18n.getMessage("ratelimit.config.baseRateLimit"));
        baseRateLimitLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        baseRateLimitLabel.setForeground(ThemeManager.TEXT_SECONDARY);
        panel.add(baseRateLimitLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        baseRateLimitSpinner = new JSpinner(new SpinnerNumberModel(0L, 0L, Long.MAX_VALUE, 1024 * 1024L));
        baseRateLimitSpinner.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        baseRateLimitSpinner.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        baseRateLimitSpinner.setToolTipText(i18n.getMessage("ratelimit.config.baseRateLimit.tooltip"));
        panel.add(baseRateLimitSpinner, gbc);

        // LOW percentage slider
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel lowPercentLabel = new JLabel(i18n.getMessage("ratelimit.config.lowPercent"));
        lowPercentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        lowPercentLabel.setForeground(ThemeManager.TEXT_SECONDARY);
        panel.add(lowPercentLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        lowPercentSlider = createPercentSlider();
        panel.add(lowPercentSlider, gbc);

        gbc.gridx = 2; gbc.gridy = 2; gbc.gridwidth = 1;
        gbc.weightx = 0;
        lowPercentValueLabel = new JLabel("100%");
        lowPercentValueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        lowPercentValueLabel.setForeground(ThemeManager.ACCENT_SUCCESS);
        lowPercentValueLabel.setPreferredSize(new Dimension(45, 20));
        panel.add(lowPercentValueLabel, gbc);

        // MEDIUM percentage slider
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel mediumPercentLabel = new JLabel(i18n.getMessage("ratelimit.config.mediumPercent"));
        mediumPercentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        mediumPercentLabel.setForeground(ThemeManager.TEXT_SECONDARY);
        panel.add(mediumPercentLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        mediumPercentSlider = createPercentSlider();
        panel.add(mediumPercentSlider, gbc);

        gbc.gridx = 2; gbc.gridy = 3; gbc.gridwidth = 1;
        gbc.weightx = 0;
        mediumPercentValueLabel = new JLabel("70%");
        mediumPercentValueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        mediumPercentValueLabel.setForeground(ThemeManager.ACCENT_WARNING);
        mediumPercentValueLabel.setPreferredSize(new Dimension(45, 20));
        panel.add(mediumPercentValueLabel, gbc);

        // HIGH percentage slider
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel highPercentLabel = new JLabel(i18n.getMessage("ratelimit.config.highPercent"));
        highPercentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        highPercentLabel.setForeground(ThemeManager.TEXT_SECONDARY);
        panel.add(highPercentLabel, gbc);

        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        highPercentSlider = createPercentSlider();
        panel.add(highPercentSlider, gbc);

        gbc.gridx = 2; gbc.gridy = 4; gbc.gridwidth = 1;
        gbc.weightx = 0;
        highPercentValueLabel = new JLabel("40%");
        highPercentValueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        highPercentValueLabel.setForeground(ThemeManager.ACCENT_ERROR);
        highPercentValueLabel.setPreferredSize(new Dimension(45, 20));
        panel.add(highPercentValueLabel, gbc);

        // Add vertical glue
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), gbc);

        // Add slider listeners
        lowPercentSlider.addChangeListener(sliderChangeListener);
        mediumPercentSlider.addChangeListener(sliderChangeListener);
        highPercentSlider.addChangeListener(sliderChangeListener);

        return panel;
    }

    /**
     * Creates a percentage slider (0-100).
     */
    private JSlider createPercentSlider() {
        JSlider slider = new JSlider(0, 100, 100);
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        return slider;
    }

    /**
     * Updates the slider value labels.
     */
    private void updateSliderLabels() {
        lowPercentValueLabel.setText(lowPercentSlider.getValue() + "%");
        mediumPercentValueLabel.setText(mediumPercentSlider.getValue() + "%");
        highPercentValueLabel.setText(highPercentSlider.getValue() + "%");
    }

    /**
     * Creates the button panel.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        saveButton = new JButton(i18n.getMessage("ratelimit.button.save"));
        saveButton.addActionListener(_ -> saveAndClose());

        cancelButton = new JButton(i18n.getMessage("ratelimit.button.cancel"));
        cancelButton.addActionListener(_ -> dispose());

        applyButton = new JButton(i18n.getMessage("ratelimit.button.apply"));
        applyButton.addActionListener(_ -> applySettings());

        panel.add(applyButton);
        panel.add(cancelButton);
        panel.add(saveButton);

        return panel;
    }

    /**
     * Starts the update timer for refreshing statistics.
     */
    private void startUpdateTimer() {
        updateTimer = new Timer(1000, _ -> updateStats());
        updateTimer.start();
        // Initial update
        updateStats();
    }

    /**
     * Updates the statistics display.
     */
    private void updateStats() {
        SwingUtilities.invokeLater(() -> {
            double currentSpeed = getCurrentSpeed();
            
            // Update max speed tracking
            if (currentSpeed > maxRecordedSpeed) {
                maxRecordedSpeed = currentSpeed;
            }

            currentSpeedLabel.setText(String.format("%.2f MB/s", currentSpeed));
            maxSpeedLabel.setText(String.format("%.2f MB/s", maxRecordedSpeed));

            // Update speed history
            speedHistory[speedHistoryIndex] = currentSpeed;
            speedHistoryIndex = (speedHistoryIndex + 1) % SPEED_HISTORY_SIZE;

            // Repaint chart
            speedChartPanel.repaint();
        });
    }

    /**
     * Gets the current speed from SpeedProbeGroup.
     *
     * @return current speed in MB/s, or 0.0 if unavailable
     */
    private double getCurrentSpeed() {
        try {
            return CopyTask.getSpeedProbeGroup().getTotalSpeed();
        } catch (Exception e) {
            logger.warning("Failed to get speed: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Resets the speed statistics.
     */
    private void resetStats() {
        maxRecordedSpeed = 0.0;
        Arrays.fill(speedHistory, 0.0);
        speedHistoryIndex = 0;
        
        updateStats();
        logger.info("Speed statistics reset");
    }

    /**
     * Loads current settings from ConfigManager.
     */
    private void loadSettings() {
        autoModeCheckBox.setSelected(configManager.get(ConfigSchema.RATE_LIMIT_AUTO_MODE_ENABLED));
        baseRateLimitSpinner.setValue(configManager.get(ConfigSchema.COPY_RATE_LIMIT_BASE));
        lowPercentSlider.setValue(configManager.get(ConfigSchema.RATE_LIMIT_LOW_PERCENT));
        mediumPercentSlider.setValue(configManager.get(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT));
        highPercentSlider.setValue(configManager.get(ConfigSchema.RATE_LIMIT_HIGH_PERCENT));
        updateSliderLabels();
    }

    /**
     * Applies settings without closing the dialog.
     */
    private void applySettings() {
        configManager.set(ConfigSchema.RATE_LIMIT_AUTO_MODE_ENABLED, autoModeCheckBox.isSelected());
        configManager.set(ConfigSchema.COPY_RATE_LIMIT_BASE, ((Number) baseRateLimitSpinner.getValue()).longValue());
        configManager.set(ConfigSchema.RATE_LIMIT_LOW_PERCENT, lowPercentSlider.getValue());
        configManager.set(ConfigSchema.RATE_LIMIT_MEDIUM_PERCENT, mediumPercentSlider.getValue());
        configManager.set(ConfigSchema.RATE_LIMIT_HIGH_PERCENT, highPercentSlider.getValue());

        logger.info("Rate limit configuration applied");
        
        JOptionPane.showMessageDialog(
                this,
                i18n.getMessage("config.success"),
                i18n.getMessage("common.success"),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Saves settings and closes the dialog.
     */
    private void saveAndClose() {
        applySettings();
        dispose();
    }

    @Override
    public void onLocaleChanged(Locale newLocale) {
        refreshLanguage();
    }

    /**
     * Refreshes all language-dependent text.
     */
    public void refreshLanguage() {
        SwingUtilities.invokeLater(() -> {
            setTitle(i18n.getMessage("ratelimit.dialog.title"));
            statsBorder.setTitle(i18n.getMessage("ratelimit.stats.currentSpeed"));
            configBorder.setTitle(i18n.getMessage("ratelimit.config.title"));
            
            resetButton.setText(i18n.getMessage("ratelimit.stats.resetButton"));
            autoModeCheckBox.setText(i18n.getMessage("ratelimit.config.autoMode"));
            baseRateLimitLabel.setText(i18n.getMessage("ratelimit.config.baseRateLimit"));
            
            saveButton.setText(i18n.getMessage("ratelimit.button.save"));
            cancelButton.setText(i18n.getMessage("ratelimit.button.cancel"));
            applyButton.setText(i18n.getMessage("ratelimit.button.apply"));
            
            revalidate();
            repaint();
        });
    }

    @Override
    public void dispose() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
        i18n.removeLocaleChangeListener(this);
        
        // Remove slider listeners
        lowPercentSlider.removeChangeListener(sliderChangeListener);
        mediumPercentSlider.removeChangeListener(sliderChangeListener);
        highPercentSlider.removeChangeListener(sliderChangeListener);
        
        super.dispose();
    }

    /**
     * Shows the rate limit configuration dialog.
     *
     * @param parent parent frame
     */
    public static void showRateLimitConfigDialog(JFrame parent) {
        RateLimitConfigDialog dialog = new RateLimitConfigDialog(parent);
        dialog.setVisible(true);
    }

    /**
     * Custom panel for drawing the speed history chart.
     */
    private class SpeedChartPanel extends JPanel {

        private static final int PADDING = 30;

        public SpeedChartPanel() {
            setBackground(ThemeManager.CARD_BACKGROUND);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR),
                    new EmptyBorder(5, 5, 5, 5)
            ));
            setPreferredSize(new Dimension(300, 150));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int chartWidth = width - 2 * PADDING;
            int chartHeight = height - 2 * PADDING;

            // Find max value for scaling
            double maxSpeed = 1.0; // Minimum scale of 1 MB/s
            for (double speed : speedHistory) {
                if (speed > maxSpeed) {
                    maxSpeed = speed;
                }
            }

            // Draw background grid
            g2d.setColor(ThemeManager.BORDER_COLOR);
            g2d.setStroke(new BasicStroke(0.5f));
            
            // Horizontal grid lines
            for (int i = 0; i <= 4; i++) {
                int y = PADDING + (chartHeight * i / 4);
                g2d.drawLine(PADDING, y, width - PADDING, y);
            }

            // Vertical grid lines
            for (int i = 0; i <= 6; i++) {
                int x = PADDING + (chartWidth * i / 6);
                g2d.drawLine(x, PADDING, x, height - PADDING);
            }

            // Draw Y-axis labels
            g2d.setColor(ThemeManager.TEXT_SECONDARY);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (int i = 0; i <= 4; i++) {
                int y = PADDING + (chartHeight * i / 4);
                double value = maxSpeed * (4 - i) / 4;
                String label = String.format("%.1f", value);
                g2d.drawString(label, 2, y + 4);
            }

            // Draw speed line
            g2d.setColor(ThemeManager.ACCENT_PRIMARY);
            g2d.setStroke(new BasicStroke(2.0f));

            // Build path from history
            int[] xPoints = new int[SPEED_HISTORY_SIZE];
            int[] yPoints = new int[SPEED_HISTORY_SIZE];
            int pointCount = 0;

            for (int i = 0; i < SPEED_HISTORY_SIZE; i++) {
                int idx = (speedHistoryIndex + i) % SPEED_HISTORY_SIZE;
                double speed = speedHistory[idx];

                int x = PADDING + (chartWidth * i / (SPEED_HISTORY_SIZE - 1));
                int y = PADDING + chartHeight - (int) ((speed / maxSpeed) * chartHeight);

                xPoints[pointCount] = x;
                yPoints[pointCount] = y;
                pointCount++;
            }

            // Draw the line
            if (pointCount > 1) {
                g2d.drawPolyline(xPoints, yPoints, pointCount);
            }

            // Draw fill under the line (gradient)
            if (pointCount > 1) {
                GradientPaint gradient = new GradientPaint(
                        0, PADDING,
                        new Color(ThemeManager.ACCENT_PRIMARY.getRed(),
                                ThemeManager.ACCENT_PRIMARY.getGreen(),
                                ThemeManager.ACCENT_PRIMARY.getBlue(), 100),
                        0, height - PADDING,
                        new Color(ThemeManager.ACCENT_PRIMARY.getRed(),
                                ThemeManager.ACCENT_PRIMARY.getGreen(),
                                ThemeManager.ACCENT_PRIMARY.getBlue(), 10)
                );
                g2d.setPaint(gradient);

                int[] fillX = new int[pointCount + 2];
                int[] fillY = new int[pointCount + 2];

                System.arraycopy(xPoints, 0, fillX, 0, pointCount);
                System.arraycopy(yPoints, 0, fillY, 0, pointCount);

                fillX[pointCount] = xPoints[pointCount - 1];
                fillY[pointCount] = height - PADDING;
                fillX[pointCount + 1] = xPoints[0];
                fillY[pointCount + 1] = height - PADDING;

                g2d.fillPolygon(fillX, fillY, pointCount + 2);
            }

            // Draw chart title
            g2d.setColor(ThemeManager.TEXT_PRIMARY);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            String chartTitle = i18n.getMessage("ratelimit.stats.chart.title");
            FontMetrics fm = g2d.getFontMetrics();
            int titleWidth = fm.stringWidth(chartTitle);
            g2d.drawString(chartTitle, (width - titleWidth) / 2, 15);

            g2d.dispose();
        }
    }
}
