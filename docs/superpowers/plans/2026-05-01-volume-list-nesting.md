# VolumeListPanel Device-Volume 嵌套结构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 VolumeListPanel 从扁平 VolumeCard 列表改为 Device-Volume 嵌套结构，多卷设备显示可折叠分组，单卷设备保持平铺。

**Architecture:** 在现有 VolumeListPanel 内新增 `DeviceGroupPanel` 内部类（BoxLayout.Y_AXIS），包含分组头（Header）和卷容器。通过 `Map<Device, DeviceGroupPanel>` 追踪多卷设备分组。事件处理逻辑在 addVolume/removeVolume 中根据 Device 卷数动态切换平铺/嵌套模式。

**Tech Stack:** Java 25, Swing (BoxLayout, BorderLayout), FlatLaf, i18n ResourceBundle

---

### Task 1: 添加 i18n keys 到所有 locale 文件

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/messages.properties` (default)
- Modify: `src/com/superredrock/usbthief/gui/messages_en.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_zh.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_ja.properties`
- Modify: `src/com/superredrock/usbthief/gui/messages_de.properties`

- [ ] **Step 1: 在 messages.properties 末尾添加以下 keys**

```properties
# Device Group
device.group.vidPid=VID:{0} PID:{1}
device.group.totalCapacity=Total: {0}
device.group.batchDisable=Disable All
device.group.batchEnable=Enable All
device.group.batchBlacklist=Blacklist Device
```

- [ ] **Step 2: 在 messages_en.properties 末尾添加相同的英文 keys**

```properties
# Device Group
device.group.vidPid=VID:{0} PID:{1}
device.group.totalCapacity=Total: {0}
device.group.batchDisable=Disable All
device.group.batchEnable=Enable All
device.group.batchBlacklist=Blacklist Device
```

- [ ] **Step 3: 在 messages_zh.properties 末尾添加中文 keys**

```properties
# Device Group
device.group.vidPid=VID:{0} PID:{1}
device.group.totalCapacity=总计: {0}
device.group.batchDisable=全部禁用
device.group.batchEnable=全部启用
device.group.batchBlacklist=加入黑名单
```

- [ ] **Step 4: 在 messages_ja.properties 末尾添加日文 keys**

```properties
# Device Group
device.group.vidPid=VID:{0} PID:{1}
device.group.totalCapacity=合計: {0}
device.group.batchDisable=すべて無効化
device.group.batchEnable=すべて有効化
device.group.batchBlacklist=ブラックリストに追加
```

- [ ] **Step 5: 在 messages_de.properties 末尾添加德文 keys**

```properties
# Device Group
device.group.vidPid=VID:{0} PID:{1}
device.group.totalCapacity=Gesamt: {0}
device.group.batchDisable=Alle deaktivieren
device.group.batchEnable=Alle aktivieren
device.group.batchBlacklist=Zur Sperrliste hinzufügen
```

- [ ] **Step 6: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/com/superredrock/usbthief/gui/messages*.properties
git commit -m "feat: add i18n keys for device group header"
```

---

### Task 2: 添加 DeviceGroupPanel 内部类和 Device 映射字段

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/VolumeListPanel.java`

这是核心实现任务。分多步完成。

- [ ] **Step 1: 在 VolumeListPanel 类中添加 deviceGroups 字段**

在 `VolumeListPanel` 类的字声明区域（约第 30 行 `Map<Volume, VolumeCard> volumeCards` 之后）添加：

```java
private final Map<Device, DeviceGroupPanel> deviceGroups = new HashMap<>();
```

同时在文件头部 imports 中确保有：
```java
import javax.swing.border.LineBorder;
```

- [ ] **Step 2: 添加 DeviceGroupPanel 内部类**

在 `VolumeCard` 内部类之前（约第 356 行 `// ========== VolumeCard` 注释之前）添加以下完整的内部类：

```java
// ========== DeviceGroupPanel — multi-volume collapsible group ==========

private class DeviceGroupPanel extends JPanel {

    private final Device device;
    private final JPanel volumesContainer;
    private final JButton toggleButton;
    private final JLabel infoLabel;
    private final JButton moreButton;
    private final JPopupMenu groupMenu;
    private final JMenuItem batchEnableItem;
    private final JMenuItem batchDisableItem;
    private final JMenuItem batchBlacklistItem;
    private boolean expanded = true;

    public DeviceGroupPanel(Device device) {
        this.device = device;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // --- Header panel ---
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(UIManager.getColor("Component.borderColor"), 1, true),
            new EmptyBorder(3, 6, 3, 6)
        ));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        header.setOpaque(true);

        // Toggle arrow
        toggleButton = new JButton("▼");
        toggleButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        toggleButton.setFocusPainted(false);
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggleButton.addActionListener(_ -> toggleExpanded());

        // Info label
        infoLabel = new JLabel(buildInfoText());
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        infoLabel.setForeground(ThemeManager.TEXT_SECONDARY);

        // More button + menu
        groupMenu = new JPopupMenu();
        batchEnableItem = new JMenuItem(i18n.getMessage("device.group.batchEnable"));
        batchEnableItem.addActionListener(_ -> batchEnableAll());
        batchDisableItem = new JMenuItem(i18n.getMessage("device.group.batchDisable"));
        batchDisableItem.addActionListener(_ -> batchDisableAll());
        batchBlacklistItem = new JMenuItem(i18n.getMessage("device.group.batchBlacklist"));
        batchBlacklistItem.addActionListener(_ -> blacklistDevice());
        groupMenu.add(batchEnableItem);
        groupMenu.add(batchDisableItem);
        groupMenu.addSeparator();
        groupMenu.add(batchBlacklistItem);

        moreButton = new JButton("⋮");
        moreButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        moreButton.setFocusPainted(false);
        moreButton.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        moreButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        moreButton.addActionListener(e -> groupMenu.show(moreButton, 0, moreButton.getHeight()));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(toggleButton);
        leftPanel.add(infoLabel);

        header.add(leftPanel, BorderLayout.CENTER);
        header.add(moreButton, BorderLayout.EAST);

        // --- Volumes container ---
        volumesContainer = new JPanel();
        volumesContainer.setLayout(new BoxLayout(volumesContainer, BoxLayout.Y_AXIS));
        volumesContainer.setOpaque(false);

        add(header);
        add(volumesContainer);
    }

    private String buildInfoText() {
        StringBuilder sb = new StringBuilder();
        if (device.getVid() != null && device.getPid() != null) {
            sb.append(i18n.getMessage("device.group.vidPid", device.getVid(), device.getPid()));
            sb.append("  ");
        }
        sb.append("SN:").append(device.getSerialNumber());
        sb.append("  ");
        long totalBytes = device.getVolumes().stream()
            .mapToLong(v -> {
                try {
                    return v.getFileStore() != null ? v.getFileStore().getTotalSpace() : 0;
                } catch (IOException _) { return 0; }
            })
            .sum();
        sb.append(i18n.getMessage("device.group.totalCapacity", SizeFormatter.format(totalBytes)));
        return sb.toString();
    }

    public void refreshHeader() {
        infoLabel.setText(buildInfoText());
    }

    public void addVolumeCard(VolumeCard card) {
        volumesContainer.add(card);
        volumesContainer.revalidate();
        volumesContainer.repaint();
        refreshHeader();
    }

    public void removeVolumeCard(VolumeCard card) {
        volumesContainer.remove(card);
        volumesContainer.revalidate();
        volumesContainer.repaint();
        refreshHeader();
    }

    public java.util.List<VolumeCard> getVolumeCards() {
        java.util.List<VolumeCard> cards = new java.util.ArrayList<>();
        for (Component c : volumesContainer.getComponents()) {
            if (c instanceof VolumeCard vc) cards.add(vc);
        }
        return cards;
    }

    private void toggleExpanded() {
        expanded = !expanded;
        toggleButton.setText(expanded ? "▼" : "▶");
        volumesContainer.setVisible(expanded);
        revalidate();
        repaint();
    }

    private void batchEnableAll() {
        for (Volume v : device.getVolumes()) {
            deviceManager.enable(v);
        }
    }

    private void batchDisableAll() {
        for (Volume v : device.getVolumes()) {
            deviceManager.disable(v);
        }
    }

    private void blacklistDevice() {
        String sn = device.getSerialNumber();
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
            i18n.getMessage("device.card.blacklist.confirm", sn, sn),
            i18n.getMessage("device.card.blacklist.confirm.title"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            ConfigManager.getInstance().addToDeviceBlacklistBySerial(sn);
            JOptionPane.showMessageDialog(parentFrame,
                i18n.getMessage("device.card.blacklist.success"),
                i18n.getMessage("device.card.blacklist.success.title"),
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void refreshLanguage() {
        batchEnableItem.setText(i18n.getMessage("device.group.batchEnable"));
        batchDisableItem.setText(i18n.getMessage("device.group.batchDisable"));
        batchBlacklistItem.setText(i18n.getMessage("device.group.batchBlacklist"));
        refreshHeader();
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS（此时 DeviceGroupPanel 未被使用，但编译不应出错）

- [ ] **Step 4: Commit**

```bash
git add src/com/superredrock/usbthief/gui/VolumeListPanel.java
git commit -m "feat: add DeviceGroupPanel inner class for nested volume display"
```

---

### Task 3: 重构 addVolume 方法支持嵌套逻辑

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/VolumeListPanel.java`

- [ ] **Step 1: 添加 createVolumeCard 工厂方法**

在 `addVolume` 方法之前添加：

```java
private VolumeCard createVolumeCard(Volume volume) {
    VolumeCard card = new VolumeCard(volume, parentFrame, deviceManager);
    card.getCheckBox().addItemListener(_ -> updateBatchButtons());
    return card;
}
```

- [ ] **Step 2: 重写 addVolume 方法**

将现有 `addVolume` 方法（约第 336-348 行）替换为：

```java
private void addVolume(Volume volume) {
    if (volumeCards.containsKey(volume)) {
        return;
    }

    Device device = volume.getDevice();
    VolumeCard card = createVolumeCard(volume);
    volumeCards.put(volume, card);

    if (device == null || device.getVolumes().size() <= 1) {
        // Single volume or no device — flat layout
        devicesPanel.add(card);
    } else {
        DeviceGroupPanel group = deviceGroups.get(device);
        if (group == null) {
            // Device went from 1 to 2 volumes — migrate existing flat card into new group
            group = new DeviceGroupPanel(device);
            deviceGroups.put(device, group);

            // Find and move the existing flat VolumeCard for the other volume
            for (Volume otherVol : device.getVolumes()) {
                if (otherVol != volume && volumeCards.containsKey(otherVol)) {
                    VolumeCard existingCard = volumeCards.get(otherVol);
                    devicesPanel.remove(existingCard);
                    group.addVolumeCard(existingCard);
                }
            }

            // Insert group at the end (or replace position)
            devicesPanel.add(group);
        } else {
            group.addVolumeCard(card);
        }
    }

    devicesPanel.revalidate();
    devicesPanel.repaint();
    updateEmptyState();
}
```

- [ ] **Step 3: 更新 initializeExistingVolumes 保持遍历顺序**

将 `initializeExistingVolumes` 方法（约第 122-128 行）替换为：

```java
private void initializeExistingVolumes() {
    SwingUtilities.invokeLater(() -> {
        // Group volumes by device for correct initial rendering
        Map<Device, java.util.List<Volume>> byDevice = new java.util.LinkedHashMap<>();
        java.util.List<Volume> noDevice = new java.util.ArrayList<>();

        for (Volume volume : deviceManager.getAllVolumes()) {
            Device dev = volume.getDevice();
            if (dev == null) {
                noDevice.add(volume);
            } else {
                byDevice.computeIfAbsent(dev, _ -> new java.util.ArrayList<>()).add(volume);
            }
        }

        // Add volumes respecting device grouping
        for (java.util.List<Volume> volumes : byDevice.values()) {
            for (Volume v : volumes) {
                addVolume(v);
            }
        }
        for (Volume v : noDevice) {
            addVolume(v);
        }
    });
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/com/superredrock/usbthief/gui/VolumeListPanel.java
git commit -m "feat: restructure addVolume to support Device-Volume nesting"
```

---

### Task 4: 重写 onVolumeRemoved 支持拆组和清理

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/VolumeListPanel.java`

- [ ] **Step 1: 重写 onVolumeRemoved 方法**

将现有 `onVolumeRemoved` 方法（约第 221-239 行）替换为：

```java
private void onVolumeRemoved(VolumeRemovedEvent event) {
    SwingUtilities.invokeLater(() -> {
        Volume vol = event.volume();
        Volume oldKey = null;
        for (Volume v : volumeCards.keySet()) {
            if (v.getSerialNumber().equals(vol.getSerialNumber())) {
                oldKey = v;
                break;
            }
        }
        if (oldKey == null) return;

        VolumeCard card = volumeCards.remove(oldKey);
        Device device = oldKey.getDevice();

        if (device != null && deviceGroups.containsKey(device)) {
            DeviceGroupPanel group = deviceGroups.get(device);
            group.removeVolumeCard(card);

            long remaining = device.getVolumes().stream()
                .filter(v -> volumeCards.containsKey(v) && v != oldKey)
                .count();

            if (remaining <= 1) {
                // Dissolve group — move remaining card(s) back to flat layout
                java.util.List<VolumeCard> remainingCards = new java.util.ArrayList<>(group.getVolumeCards());
                devicesPanel.remove(group);
                deviceGroups.remove(device);
                for (VolumeCard rc : remainingCards) {
                    devicesPanel.add(rc);
                }
            }
        } else {
            devicesPanel.remove(card);
        }

        devicesPanel.revalidate();
        devicesPanel.repaint();
        updateEmptyState();
    });
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/com/superredrock/usbthief/gui/VolumeListPanel.java
git commit -m "feat: handle group dissolution on volume removal"
```

---

### Task 5: 更新 onVolumeStateChanged 和 refreshLanguage

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/VolumeListPanel.java`

- [ ] **Step 1: 更新 onVolumeStateChanged 刷新分组头**

将现有 `onVolumeStateChanged` 方法（约第 241-256 行）替换为：

```java
private void onVolumeStateChanged(VolumeStateChangedEvent event) {
    SwingUtilities.invokeLater(() -> {
        Volume vol = event.volume();
        Volume oldKey = null;
        for (Volume v : volumeCards.keySet()) {
            if (v.getSerialNumber().equals(vol.getSerialNumber())) {
                oldKey = v;
                break;
            }
        }
        if (oldKey != null) {
            VolumeCard card = volumeCards.get(oldKey);
            card.updateVolume(vol);
        }
        // Refresh group header (total capacity may change)
        Device device = vol.getDevice();
        if (device != null && deviceGroups.containsKey(device)) {
            deviceGroups.get(device).refreshHeader();
        }
    });
}
```

- [ ] **Step 2: 更新 refreshLanguage 包含 DeviceGroupPanel**

在 `refreshLanguage` 方法中（约第 193-215 行），在 `for (VolumeCard card : volumeCards.values())` 循环之前添加：

```java
for (DeviceGroupPanel group : deviceGroups.values()) {
    group.refreshLanguage();
}
```

- [ ] **Step 3: 更新 setSelectAll 和 updateBatchButtons 遍历所有 cards**

现有 `setSelectAll` 和 `updateBatchButtons` 方法已经遍历 `volumeCards.values()`，所以不需要改动——它们自然覆盖嵌套在 DeviceGroupPanel 中的 VolumeCard。

确认这两个方法没有引用 `devicesPanel.getComponents()`。如果有的话，替换为遍历 `volumeCards.values()`。

- [ ] **Step 4: 更新 batchEnable/batchDisable/batchAddToBlacklist 确认它们遍历 volumeCards**

现有实现已经使用 `for (VolumeCard card : volumeCards.values())`，无需改动。确认一下即可。

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/com/superredrock/usbthief/gui/VolumeListPanel.java
git commit -m "feat: refresh group headers on state change and language switch"
```

---

### Task 6: 更新 onVolumeInserted 中的 checkbox 监听器

**Files:**
- Modify: `src/com/superredrock/usbthief/gui/VolumeListPanel.java`

- [ ] **Step 1: 移除 addVolume 中旧的 checkbox 监听器注册**

由于 `createVolumeCard` 工厂方法已经包含了 `card.getCheckBox().addItemListener(_ -> updateBatchButtons())`，确认旧的 `addVolume` 方法中不再有重复的监听器注册。

检查 `addVolume` 方法体：不应包含 `card.getCheckBox().addItemListener(...)` 这行。如果有则删除（已经移到了 `createVolumeCard` 中）。

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit（如有改动）**

```bash
git add src/com/superredrock/usbthief/gui/VolumeListPanel.java
git commit -m "fix: remove duplicate checkbox listener in addVolume"
```

（如果没有改动，跳过此步）

---

### Task 7: 最终编译与 package 验证

**Files:** 无新增修改

- [ ] **Step 1: 完整编译**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Package 验证**

Run: `mvn package -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 检查最终文件差异**

Run: `git diff --stat HEAD~6`
Expected: 6 个文件的修改（VolumeListPanel.java + 5 个 messages*.properties）
