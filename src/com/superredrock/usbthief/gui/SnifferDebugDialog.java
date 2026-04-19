package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.worker.SnifferDebugSnapshot;
import com.superredrock.usbthief.worker.SnifferLifecycleManager;
import com.superredrock.usbthief.worker.SnifferPhase;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SnifferDebugDialog extends JDialog {

    private static final int POLL_MS = 500;

    private final Timer timer;
    private final JPanel cardsPanel;

    public SnifferDebugDialog(Frame owner) {
        super(owner, "Sniffer Debug Monitor", false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(420, 320);
        setMinimumSize(new Dimension(350, 200));
        setLocationRelativeTo(owner);

        cardsPanel = new JPanel();
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(cardsPanel);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);

        timer = new Timer(POLL_MS, _ -> refresh());
        timer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
            }
        });
    }

    private void refresh() {
        List<SnifferDebugSnapshot> snapshots = SnifferLifecycleManager.getInstance().getDebugSnapshots();

        SwingUtilities.invokeLater(() -> {
            cardsPanel.removeAll();

            if (snapshots.isEmpty()) {
                JLabel empty = new JLabel("No active sniffers", SwingConstants.CENTER);
                empty.setForeground(ThemeManager.TEXT_MUTED);
                cardsPanel.add(empty);
            } else {
                for (SnifferDebugSnapshot s : snapshots) {
                    cardsPanel.add(buildCard(s));
                }
            }

            cardsPanel.revalidate();
            cardsPanel.repaint();
        });
    }

    private JPanel buildCard(SnifferDebugSnapshot s) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.BLACK, 1),
            s.driveLetter() + " (" + s.serialNumber() + ")"
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;

        // Phase badge
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel phaseLabel = new JLabel(s.phase().getDisplay());
        phaseLabel.setOpaque(true);
        phaseLabel.setFont(phaseLabel.getFont().deriveFont(Font.BOLD, 11f));
        phaseLabel.setForeground(Color.WHITE);
        phaseLabel.setBackground(phaseColor(s.phase()));
        phaseLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        card.add(phaseLabel, gbc);

        // Change counter
        if (s.phase() == SnifferPhase.MONITORING) {
            row++;
            gbc.gridwidth = 1;
            gbc.gridx = 0; gbc.gridy = row;
            JLabel counterLabel = new JLabel("Changes: " + s.changeCount() + " / " + s.threshold());
            counterLabel.setFont(counterLabel.getFont().deriveFont(10f));
            card.add(counterLabel, gbc);

            gbc.gridx = 1;
            JProgressBar counterBar = new JProgressBar(0, Math.max(1, s.threshold()));
            counterBar.setValue(Math.min(s.changeCount(), s.threshold()));
            counterBar.setStringPainted(true);
            counterBar.setPreferredSize(new Dimension(120, 16));
            card.add(counterBar, gbc);
        }

        // Reset countdown
        if (s.phase() == SnifferPhase.MONITORING && s.resetIntervalSec() > 0) {
            row++;
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            JLabel resetLabel = new JLabel("Next reset in: " + formatDuration(s.secondsUntilReset()) + " (interval: " + s.resetIntervalSec() + "s)");
            resetLabel.setFont(resetLabel.getFont().deriveFont(10f));
            card.add(resetLabel, gbc);
        }

        // Watched directories
        if (s.phase() != SnifferPhase.FINISHED || s.watchedDirCount() > 0) {
            row++;
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            JLabel dirLabel = new JLabel("Watched dirs: " + s.watchedDirCount());
            dirLabel.setFont(dirLabel.getFont().deriveFont(10f));
            card.add(dirLabel, gbc);
        }

        // Cooldown
        if (s.cooldownRemainingMs() > 0) {
            row++;
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            long sec = TimeUnit.MILLISECONDS.toSeconds(s.cooldownRemainingMs());
            JLabel cooldownLabel = new JLabel("Cooldown: " + formatDuration(sec) + " (" + s.cooldownReason() + ")");
            cooldownLabel.setFont(cooldownLabel.getFont().deriveFont(10f));
            cooldownLabel.setForeground(new Color(220, 80, 60));
            card.add(cooldownLabel, gbc);
        }

        return card;
    }

    private static Color phaseColor(SnifferPhase phase) {
        return switch (phase) {
            case INITIAL_SCAN -> new Color(52, 152, 219);
            case MONITORING -> new Color(46, 204, 113);
            case FINISHED -> new Color(149, 165, 166);
        };
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min > 0) {
            return min + "m " + sec + "s";
        }
        return sec + "s";
    }
}
