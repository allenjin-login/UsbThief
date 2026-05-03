package com.superredrock.usbthief.gui.dailog;

import com.superredrock.usbthief.core.Service;
import com.superredrock.usbthief.core.ServiceState;
import com.superredrock.usbthief.core.QueueManager;
import com.superredrock.usbthief.core.event.Event;
import com.superredrock.usbthief.core.event.EventBus;
import com.superredrock.usbthief.core.event.EventListener;
import com.superredrock.usbthief.core.event.device.DeviceEvent;
import com.superredrock.usbthief.core.event.device.VolumeEvent;
import com.superredrock.usbthief.core.event.device.VolumeRemovedEvent;
import com.superredrock.usbthief.core.event.index.IndexEvent;
import com.superredrock.usbthief.core.event.index.IndexSavedEvent;
import com.superredrock.usbthief.core.event.index.IndexLoadedEvent;
import com.superredrock.usbthief.core.event.storage.StorageLowEvent;
import com.superredrock.usbthief.core.event.storage.StorageRecoveredEvent;
import com.superredrock.usbthief.core.event.storage.FilesRecycledEvent;
import com.superredrock.usbthief.core.event.storage.EmptyFoldersDeletedEvent;
import com.superredrock.usbthief.core.event.worker.FileDiscoveredEvent;
import com.superredrock.usbthief.core.event.worker.CopyCompletedEvent;
import com.superredrock.usbthief.core.event.index.DuplicateDetectedEvent;
import com.superredrock.usbthief.gui.theme.ThemeManager;
import com.superredrock.usbthief.index.Index;
import com.superredrock.usbthief.worker.*;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SnifferDebugDialog extends JDialog {

    private static final int POLL_MS = 250;
    private static final int EVENT_BUFFER_SIZE = 200;
    private static final String[] SYSTEM_THREAD_PREFIXES = {
        "Reference Handler", "Finalizer", "Signal Dispatcher", "Attach Listener", "Common-Cleaner"
    };

    private static final int TAB_SNIFFERS = 0;
    private static final int TAB_SERVICES = 1;
    private static final int TAB_EVENTS = 2;
    private static final int TAB_THREADS = 3;
    private static final String[] TAB_NAMES = {"Sniffers", "Services", "Events", "Threads"};
    private static final String[] TAB_KEYS = {"SNIFFERS", "SERVICES", "EVENTS", "THREADS"};

    private final Timer timer;
    private int activeTab = TAB_SNIFFERS;

    private final JButton[] tabButtons = new JButton[4];

    private final JPanel sniffersPanel = new JPanel();
    private final JPanel servicesPanel = new JPanel();
    private final JPanel eventsPanel = new JPanel();
    private final JPanel threadsPanel = new JPanel();

    private final LinkedList<CapturedEvent> eventBuffer = new LinkedList<>();
    private final EventListener<Event> eventListener;
    private String activeEventFilter = "All";
    private final CardLayout cardLayout;
    private final JPanel contentContainer;

    private final LinkedHashMap<String, SnifferDebugSnapshot> ejectedCache = new LinkedHashMap<>();
    private final Map<String, Long> ejectedTimestamps = new HashMap<>();

    private static final Color TAB_ACTIVE_BG = ThemeManager.ACCENT_PRIMARY;
    private static final Color TAB_INACTIVE_BG = Color.WHITE;
    private static final Color TAB_ACTIVE_FG = Color.WHITE;
    private static final Color TAB_INACTIVE_FG = ThemeManager.TEXT_MUTED;

    public SnifferDebugDialog(Frame owner) {
        super(owner, "UsbThief Debug", false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(640, 480);
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(owner);

        setLayout(new BorderLayout());

        add(buildTabBar(), BorderLayout.NORTH);
        contentContainer = buildContentArea();
        cardLayout = (CardLayout) contentContainer.getLayout();
        add(contentContainer, BorderLayout.CENTER);

        eventListener = event -> {
            synchronized (eventBuffer) {
                eventBuffer.add(new CapturedEvent(event));
                while (eventBuffer.size() > EVENT_BUFFER_SIZE) {
                    eventBuffer.remove(0);
                }
            }
            if (event instanceof VolumeRemovedEvent) {
                VolumeRemovedEvent vre = (VolumeRemovedEvent) event;
                cacheEjectedVolume(vre.volume().getSerialNumber());
            }
        };
        EventBus.getInstance().register(Event.class, eventListener);

        timer = new Timer(POLL_MS, ignored -> refresh());
        timer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
                EventBus.getInstance().unregister(Event.class, eventListener);
            }
        });

        switchTab(TAB_SNIFFERS);
    }

    // ========== Tab Bar ==========

    private JPanel buildTabBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 8));
        bar.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        bar.setBorder(new MatteBorder(0, 0, 1, 0, ThemeManager.BORDER_COLOR));

        for (int i = 0; i < TAB_NAMES.length; i++) {
            JButton btn = new JButton(TAB_NAMES[i]);
            btn.setFocusPainted(false);
            btn.setBorderPainted(true);
            btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            int tabIndex = i;
            btn.addActionListener(ignored -> switchTab(tabIndex));
            tabButtons[i] = btn;
            bar.add(btn);
        }
        return bar;
    }

    private void switchTab(int index) {
        activeTab = index;
        for (int i = 0; i < tabButtons.length; i++) {
            JButton btn = tabButtons[i];
            if (i == index) {
                btn.setBackground(TAB_ACTIVE_BG);
                btn.setForeground(TAB_ACTIVE_FG);
                btn.setBorder(new LineBorder(TAB_ACTIVE_BG, 1, true));
            } else {
                btn.setBackground(TAB_INACTIVE_BG);
                btn.setForeground(TAB_INACTIVE_FG);
                btn.setBorder(new LineBorder(ThemeManager.BORDER_COLOR, 1, true));
            }
        }
        showActivePanel();
    }

    private JPanel buildContentArea() {
        JPanel container = new JPanel(new CardLayout());
        container.setBackground(ThemeManager.BACKGROUND_PRIMARY);

        for (JPanel panel : new JPanel[]{sniffersPanel, servicesPanel, eventsPanel, threadsPanel}) {
            panel.setBackground(ThemeManager.BACKGROUND_PRIMARY);
        }

        container.add(sniffersPanel, TAB_KEYS[0]);
        container.add(servicesPanel, TAB_KEYS[1]);
        container.add(eventsPanel, TAB_KEYS[2]);
        container.add(threadsPanel, TAB_KEYS[3]);
        return container;
    }

    private void showActivePanel() {
        cardLayout.show(contentContainer, TAB_KEYS[activeTab]);
    }

    // ========== Refresh ==========

    private void refresh() {
        switch (activeTab) {
            case TAB_SNIFFERS -> refreshSniffers();
            case TAB_SERVICES -> refreshServices();
            case TAB_EVENTS -> refreshEvents();
            case TAB_THREADS -> refreshThreads();
        }
    }

    // ========== Tab 1: Sniffers ==========

    private void cacheEjectedVolume(String serialNumber) {
        List<SnifferDebugSnapshot> current = SnifferLifecycleManager.getInstance().getDebugSnapshots();
        for (SnifferDebugSnapshot s : current) {
            if (s.serialNumber().equals(serialNumber)) {
                SnifferDebugSnapshot ejected = new SnifferDebugSnapshot(
                    s.driveLetter(), s.serialNumber(), SnifferPhase.EJECTED,
                    s.changeCount(), s.threshold(), s.secondsUntilReset(),
                    s.resetIntervalSec(), s.watchedDirCount(), 0, null
                );
                ejectedCache.put(serialNumber, ejected);
                ejectedTimestamps.put(serialNumber, System.currentTimeMillis());
                return;
            }
        }
        if (!ejectedCache.containsKey(serialNumber)) {
            ejectedCache.put(serialNumber, new SnifferDebugSnapshot(
                "", serialNumber, SnifferPhase.EJECTED,
                0, 0, 0, 0, 0, 0, null
            ));
            ejectedTimestamps.put(serialNumber, System.currentTimeMillis());
        }
    }

    private void refreshSniffers() {
        List<SnifferDebugSnapshot> snapshots = SnifferLifecycleManager.getInstance().getDebugSnapshots();
        Set<String> activeSerials = new HashSet<>();
        for (SnifferDebugSnapshot s : snapshots) {
            activeSerials.add(s.serialNumber());
        }
        ejectedCache.keySet().removeIf(serial -> {
            if (activeSerials.contains(serial)) {
                ejectedTimestamps.remove(serial);
                return true;
            }
            return false;
        });

        List<SnifferDebugSnapshot> merged = new ArrayList<>(snapshots);
        merged.addAll(ejectedCache.values());

        sniffersPanel.removeAll();
        sniffersPanel.setLayout(new BoxLayout(sniffersPanel, BoxLayout.Y_AXIS));
        sniffersPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        if (merged.isEmpty()) {
            JLabel empty = new JLabel("No active sniffers", SwingConstants.CENTER);
            empty.setForeground(ThemeManager.TEXT_MUTED);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            sniffersPanel.add(empty);
        } else {
            for (SnifferDebugSnapshot s : merged) {
                JPanel card = buildSnifferCard(s);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
                sniffersPanel.add(card);
                sniffersPanel.add(Box.createVerticalStrut(10));
            }
        }
        sniffersPanel.revalidate();
        sniffersPanel.repaint();
    }

    private JPanel buildSnifferCard(SnifferDebugSnapshot s) {
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setBackground(ThemeManager.CARD_BACKGROUND);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(s.cooldownRemainingMs() > 0 ? blendColor(ThemeManager.ACCENT_ERROR, 0.3f) : ThemeManager.BORDER_COLOR, 1, true),
            new EmptyBorder(14, 14, 14, 14)
        ));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftHeader.setOpaque(false);

        JLabel driveIcon = new JLabel(s.driveLetter());
        driveIcon.setOpaque(true);
        driveIcon.setBackground(blendColor(ThemeManager.ACCENT_PRIMARY, 0.1f));
        driveIcon.setForeground(ThemeManager.ACCENT_PRIMARY);
        driveIcon.setFont(driveIcon.getFont().deriveFont(Font.BOLD, 14f));
        driveIcon.setHorizontalAlignment(SwingConstants.CENTER);
        driveIcon.setPreferredSize(new Dimension(32, 32));
        driveIcon.setBorder(new LineBorder(blendColor(ThemeManager.ACCENT_PRIMARY, 0.15f), 1, true));

        JPanel namePanel = getDebugPanel(s);

        leftHeader.add(driveIcon);
        leftHeader.add(namePanel);

        JLabel badge = buildPhaseBadge(s);
        header.add(leftHeader, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        stats.setOpaque(false);

        if (s.phase() == SnifferPhase.MONITORING) {
            stats.add(buildStatItem("Changes", s.changeCount() + " / " + s.threshold()));
            stats.add(buildProgressBar(s.changeCount(), Math.max(1, s.threshold())));
            if (s.resetIntervalSec() > 0) {
                stats.add(buildStatItem("Reset In", formatDuration(s.secondsUntilReset())));
            }
            stats.add(buildStatItem("Watched Dirs", String.valueOf(s.watchedDirCount())));
        } else if (s.phase() == SnifferPhase.FINISHED && s.cooldownRemainingMs() > 0) {
            long sec = TimeUnit.MILLISECONDS.toSeconds(s.cooldownRemainingMs());
            JLabel remainingLabel = new JLabel(formatDuration(sec));
            remainingLabel.setFont(remainingLabel.getFont().deriveFont(Font.BOLD, 12f));
            remainingLabel.setForeground(ThemeManager.ACCENT_ERROR);
            stats.add(buildStatItem("Remaining", ""));
            stats.add(remainingLabel);
            if (s.cooldownReason() != null && !s.cooldownReason().isEmpty()) {
                stats.add(buildStatItem("Reason", s.cooldownReason()));
            }
        } else if (s.phase() == SnifferPhase.INITIAL_SCAN) {
            stats.add(buildStatItem("Watched Dirs", String.valueOf(s.watchedDirCount())));
        } else if (s.phase() == SnifferPhase.EJECTED) {
            Long ts = ejectedTimestamps.get(s.serialNumber());
            if (ts != null) {
                long elapsedSec = (System.currentTimeMillis() - ts) / 1000;
                JLabel elapsedLabel = new JLabel(formatDuration(elapsedSec));
                elapsedLabel.setFont(elapsedLabel.getFont().deriveFont(Font.BOLD, 12f));
                elapsedLabel.setForeground(ThemeManager.TEXT_MUTED);
                stats.add(buildStatItem("Elapsed", ""));
                stats.add(elapsedLabel);
            }
        }

        card.add(stats, BorderLayout.CENTER);
        return card;
    }

    private static JPanel getDebugPanel(SnifferDebugSnapshot s) {
        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setOpaque(false);
        JLabel nameLabel = new JLabel(s.driveLetter() != null && !s.driveLetter().isEmpty() ? "Volume " + s.driveLetter() : s.serialNumber());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        JLabel serialLabel = new JLabel("Serial: " + s.serialNumber());
        serialLabel.setFont(serialLabel.getFont().deriveFont(Font.PLAIN, 10f));
        serialLabel.setForeground(ThemeManager.TEXT_MUTED);
        namePanel.add(nameLabel);
        namePanel.add(serialLabel);
        return namePanel;
    }

    private JLabel buildPhaseBadge(SnifferDebugSnapshot s) {
        String text;
        Color bg;
        Color fg = Color.WHITE;
        switch (s.phase()) {
            case INITIAL_SCAN:
                text = "Scanning";
                bg = ThemeManager.ACCENT_INFO;
                break;
            case EJECTED:
                text = "Ejected";
                bg = ThemeManager.TEXT_MUTED;
                fg = ThemeManager.TEXT_PRIMARY;
                break;
            case MONITORING:
                text = "Monitoring";
                bg = ThemeManager.ACCENT_SUCCESS;
                break;
            case FINISHED:
            default:
                if (s.cooldownRemainingMs() > 0) {
                    text = "Cooldown";
                    bg = ThemeManager.ACCENT_ERROR;
                } else {
                    text = "Finished";
                    bg = ThemeManager.TEXT_MUTED;
                    fg = ThemeManager.TEXT_PRIMARY;
                }
                break;
        }
        JLabel badge = new JLabel(text);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
        badge.setBorder(new EmptyBorder(3, 10, 3, 10));
        return badge;
    }

    // ========== Tab 2: Services ==========

    private void refreshServices() {
        Service[] services = getServices();
        long running = Arrays.stream(services).filter(s -> s != null && s.getServiceState() == ServiceState.RUNNING).count();
        long paused = Arrays.stream(services).filter(s -> s != null && s.getServiceState() == ServiceState.PAUSED).count();
        long failed = Arrays.stream(services).filter(s -> s != null && s.getServiceState() == ServiceState.FAILED).count();

        servicesPanel.removeAll();
        servicesPanel.setLayout(new BorderLayout());
        servicesPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        summary.setOpaque(false);
        summary.add(buildCountBadge(running + " Running", ThemeManager.ACCENT_SUCCESS));
        summary.add(buildCountBadge(paused + " Paused", ThemeManager.ACCENT_WARNING));
        summary.add(buildCountBadge(failed + " Failed", ThemeManager.ACCENT_ERROR));

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);
        for (Service svc : services) {
            grid.add(buildServiceCard(svc));
        }

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(summary, BorderLayout.WEST);

        servicesPanel.add(top, BorderLayout.NORTH);
        servicesPanel.add(grid, BorderLayout.CENTER);
        servicesPanel.revalidate();
        servicesPanel.repaint();
    }

    private Service[] getServices() {
        return new Service[]{
            QueueManager.getDeviceManager(),
            SnifferLifecycleManager.getInstance(),
            TaskScheduler.getInstance(),
            Index.getInstance(),
            RecyclerService.getInstance()
        };
    }

    private JPanel buildServiceCard(Service svc) {
        if (svc == null) return new JPanel();

        ServiceState state = svc.getServiceState();
        Color dotColor = switch (state) {
            case RUNNING -> ThemeManager.ACCENT_SUCCESS;
            case PAUSED -> ThemeManager.ACCENT_WARNING;
            case FAILED -> ThemeManager.ACCENT_ERROR;
            default -> ThemeManager.TEXT_MUTED;
        };

        JPanel card = new JPanel(new BorderLayout());
        boolean isFailed = state == ServiceState.FAILED;
        card.setBackground(ThemeManager.CARD_BACKGROUND);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(isFailed ? blendColor(ThemeManager.ACCENT_ERROR, 0.3f) : ThemeManager.BORDER_COLOR, 1, true),
            new EmptyBorder(12, 12, 12, 12)
        ));

        JPanel top = getTop(svc, isFailed, dotColor);

        card.add(top, BorderLayout.CENTER);
        return card;
    }

    private static JPanel getTop(Service svc, boolean isFailed, Color dotColor) {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel nameLabel = new JLabel(svc.getServiceName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
        nameLabel.setForeground(isFailed ? ThemeManager.ACCENT_ERROR : ThemeManager.TEXT_PRIMARY);
        top.add(nameLabel, BorderLayout.WEST);

        JLabel dot = new JLabel("●");
        dot.setForeground(dotColor);
        dot.setFont(dot.getFont().deriveFont(Font.PLAIN, 10f));
        top.add(dot, BorderLayout.EAST);
        return top;
    }

    // ========== Tab 3: Events ==========

    private void refreshEvents() {
        List<CapturedEvent> events;
        synchronized (eventBuffer) {
            events = new ArrayList<>(eventBuffer);
        }

        eventsPanel.removeAll();
        eventsPanel.setLayout(new BorderLayout());
        eventsPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel filterBar = new JPanel(new BorderLayout());
        filterBar.setOpaque(false);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        filters.setOpaque(false);
        String[] categories = {"All", "Device", "Copy", "Index", "Storage"};
        for (String cat : categories) {
            JButton btn = new JButton(cat);
            btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 10f));
            btn.setFocusPainted(false);
            btn.setBorderPainted(true);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (cat.equals(activeEventFilter)) {
                btn.setBackground(ThemeManager.ACCENT_PRIMARY);
                btn.setForeground(Color.WHITE);
                btn.setBorder(new LineBorder(ThemeManager.ACCENT_PRIMARY, 1, true));
            } else {
                btn.setBackground(Color.WHITE);
                btn.setForeground(ThemeManager.TEXT_MUTED);
                btn.setBorder(new LineBorder(ThemeManager.BORDER_COLOR, 1, true));
            }
            btn.addActionListener(ignored -> {
                activeEventFilter = cat;
            });
            filters.add(btn);
        }
        filterBar.add(filters, BorderLayout.WEST);

        JLabel eventRateLabel = new JLabel(events.size() + " events");
        eventRateLabel.setFont(eventRateLabel.getFont().deriveFont(Font.PLAIN, 10f));
        eventRateLabel.setForeground(ThemeManager.TEXT_MUTED);
        filterBar.add(eventRateLabel, BorderLayout.EAST);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ThemeManager.CARD_BACKGROUND);
        listPanel.setBorder(new LineBorder(ThemeManager.BORDER_COLOR, 1, true));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

        for (CapturedEvent ce : events) {
            if (!matchesFilter(ce.event, activeEventFilter)) continue;
            JPanel row = buildEventRow(ce, fmt);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            listPanel.add(row);
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        eventsPanel.add(filterBar, BorderLayout.NORTH);
        eventsPanel.add(scroll, BorderLayout.CENTER);
        eventsPanel.revalidate();
        eventsPanel.repaint();

        scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
    }

    private boolean matchesFilter(Event event, String filter) {
        if ("All".equals(filter)) return true;
        return switch (filter) {
            case "Device" -> event instanceof DeviceEvent || event instanceof VolumeEvent;
            case "Copy" -> event instanceof FileDiscoveredEvent || event instanceof CopyCompletedEvent || event instanceof DuplicateDetectedEvent;
            case "Index" -> event instanceof IndexEvent || event instanceof IndexSavedEvent || event instanceof IndexLoadedEvent;
            case "Storage" -> event instanceof StorageLowEvent || event instanceof StorageRecoveredEvent || event instanceof FilesRecycledEvent || event instanceof EmptyFoldersDeletedEvent;
            default -> true;
        };
    }

    private JPanel buildEventRow(CapturedEvent ce, DateTimeFormatter fmt) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setBackground(ThemeManager.CARD_BACKGROUND);
        row.setBorder(new MatteBorder(0, 0, 1, 0, ThemeManager.BORDER_COLOR));

        JLabel timeLabel = new JLabel(fmt.format(Instant.ofEpochMilli(ce.event.timestamp())));
        timeLabel.setForeground(ThemeManager.TEXT_MUTED);
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        timeLabel.setPreferredSize(new Dimension(60, 20));

        Event event = ce.event;
        String category;
        Color catColor;
        if (event instanceof DeviceEvent || event instanceof VolumeEvent) {
            category = "DEVICE";
            catColor = ThemeManager.ACCENT_INFO;
        } else if (event instanceof FileDiscoveredEvent || event instanceof CopyCompletedEvent || event instanceof DuplicateDetectedEvent) {
            category = "COPY";
            catColor = ThemeManager.ACCENT_SUCCESS;
        } else if (event instanceof IndexEvent || event instanceof IndexSavedEvent || event instanceof IndexLoadedEvent) {
            category = "INDEX";
            catColor = ThemeManager.ACCENT_PRIMARY;
        } else if (event instanceof StorageLowEvent || event instanceof StorageRecoveredEvent || event instanceof FilesRecycledEvent || event instanceof EmptyFoldersDeletedEvent) {
            category = "STORAGE";
            catColor = ThemeManager.ACCENT_WARNING;
        } else {
            category = "OTHER";
            catColor = ThemeManager.TEXT_MUTED;
        }

        JLabel catBadge = new JLabel(category);
        catBadge.setOpaque(true);
        catBadge.setBackground(blendColor(catColor, 0.15f));
        catBadge.setForeground(catColor);
        catBadge.setFont(catBadge.getFont().deriveFont(Font.BOLD, 10f));
        catBadge.setBorder(new EmptyBorder(1, 6, 1, 6));

        JLabel descLabel = new JLabel(event.description());
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));

        row.add(timeLabel);
        row.add(catBadge);
        row.add(descLabel);
        return row;
    }

    // ========== Tab 4: Threads ==========

    private void refreshThreads() {
        threadsPanel.removeAll();
        threadsPanel.setLayout(new BorderLayout());
        threadsPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        Set<Thread> allThreads = Thread.getAllStackTraces().keySet();
        List<Thread> filtered = allThreads.stream()
            .filter(t -> !isSystemThread(t.getName()))
            .sorted(Comparator.comparing(Thread::getName))
            .collect(Collectors.toList());

        long daemonCount = filtered.stream().filter(Thread::isDaemon).count();

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        summary.setOpaque(false);
        JLabel summaryLabel = new JLabel("Total: " + filtered.size() + " threads | " + daemonCount + " daemon");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 10f));
        summaryLabel.setForeground(ThemeManager.TEXT_MUTED);
        summary.add(summaryLabel);

        String[] columns = {"#", "Name", "State", "Daemon", "Priority"};
        Object[][] data = new Object[filtered.size()][5];
        for (int i = 0; i < filtered.size(); i++) {
            Thread t = filtered.get(i);
            data[i][0] = i + 1;
            data[i][1] = t.getName();
            data[i][2] = t.getState().toString();
            data[i][3] = t.isDaemon() ? "Yes" : "No";
            data[i][4] = t.getPriority();
        }

        JTable table = new JTable(data, columns);
        table.setRowHeight(24);
        table.setFont(table.getFont().deriveFont(Font.PLAIN, 11f));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 10f));
        table.setGridColor(ThemeManager.BORDER_COLOR);
        table.setShowGrid(true);
        table.setBackground(ThemeManager.CARD_BACKGROUND);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(ThemeManager.BORDER_COLOR, 1, true));

        threadsPanel.add(summary, BorderLayout.NORTH);
        threadsPanel.add(scroll, BorderLayout.CENTER);
        threadsPanel.revalidate();
        threadsPanel.repaint();
    }

    private static boolean isSystemThread(String name) {
        for (String prefix : SYSTEM_THREAD_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    // ========== Helpers ==========

    private static JLabel buildCountBadge(String text, Color color) {
        JLabel badge = new JLabel(text);
        badge.setOpaque(true);
        badge.setBackground(blendColor(color, 0.15f));
        badge.setForeground(color);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
        badge.setBorder(new EmptyBorder(3, 10, 3, 10));
        return badge;
    }

    private static JPanel buildStatItem(String label, String value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 10f));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        JLabel val = new JLabel(value);
        val.setFont(val.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(lbl);
        panel.add(val);
        return panel;
    }

    private static JProgressBar buildProgressBar(int value, int max) {
        JProgressBar bar = new JProgressBar(0, max);
        bar.setValue(Math.min(value, max));
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(80, 16));
        return bar;
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min > 0) return min + "m " + sec + "s";
        return sec + "s";
    }

    private static Color blendColor(Color base, float alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(alpha * 255));
    }

    private static class CapturedEvent {
        final Event event;
        final long capturedAt;

        CapturedEvent(Event event) {
            this.event = event;
            this.capturedAt = System.currentTimeMillis();
        }
    }
}
