# CopyCompletedEvent 使用说明

## 概述

`CopyCompletedEvent` 是在文件复制操作完成时（无论成功、失败还是取消）分发的事件。它提供了详细的复制统计信息，可用于：
- 实时监控复制进度
- 统计成功/失败/取消的文件数量
- 计算总体复制速度和字节总数
- 构建复制历史记录

## 事件属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `sourcePath` | Path | 源文件路径 |
| `destinationPath` | Path | 目标文件路径（失败时为 null） |
| `fileSize` | long | 文件总大小（字节） |
| `bytesCopied` | long | 实际复制的字节数（失败/取消时可能小于 fileSize） |
| `result` | CopyResult | 复制结果（SUCCESS/FAIL/CANCEL） |
| `timestamp` | long | 事件创建时间戳 |

## 便捷方法

- `isSuccess()` - 返回复制是否成功
- `isFailure()` - 返回复制是否失败
- `isCancelled()` - 返回复制是否被取消
- `progressPercentage()` - 返回复制进度（0.0 到 1.0）

## 使用示例

### 示例1: 基本事件监听

```java
EventBus eventBus = EventBus.getInstance();

EventListener<CopyCompletedEvent> listener = event -> {
    if (event.isSuccess()) {
        System.out.println("复制成功: " + event.sourcePath().getFileName());
        System.out.println("文件大小: " + event.fileSize() + " 字节");
    } else if (event.isFailure()) {
        System.err.println("复制失败: " + event.sourcePath());
    } else if (event.isCancelled()) {
        System.out.println("复制被取消: " + event.sourcePath()
            + " (已复制: " + event.bytesCopied() + " 字节)");
    }
};

eventBus.register(CopyCompletedEvent.class, listener);
```

### 示例2: 统计复制数据

```java
// 用于统计的类
class CopyStats {
    private long totalBytes = 0;
    private long successCount = 0;
    private long failCount = 0;
    private long cancelCount = 0;

    public void recordCopy(CopyCompletedEvent event) {
        totalBytes += event.bytesCopied();

        if (event.isSuccess()) {
            successCount++;
        } else if (event.isFailure()) {
            failCount++;
        } else if (event.isCancelled()) {
            cancelCount++;
        }
    }

    public void printStats() {
        System.out.println("复制统计:");
        System.out.println("  总字节数: " + formatBytes(totalBytes));
        System.out.println("  成功: " + successCount + " 个文件");
        System.out.println("  失败: " + failCount + " 个文件");
        System.out.println("  取消: " + cancelCount + " 个文件");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

// 注册统计监听器
CopyStats stats = new CopyStats();
EventListener<CopyCompletedEvent> statsListener = stats::recordCopy;
eventBus.register(CopyCompletedEvent.class, statsListener);

// 定期打印统计
Timer timer = new Timer(true);
timer.schedule(new TimerTask() {
    @Override
    public void run() {
        stats.printStats();
    }
}, 0, 5000); // 每5秒打印一次
```

### 示例3: 记录复制历史

```java
class CopyHistory {
    private final List<CopyCompletedEvent> history = new ArrayList<>();

    public void addRecord(CopyCompletedEvent event) {
        history.add(event);
        // 限制历史记录大小
        if (history.size() > 1000) {
            history.remove(0);
        }
    }

    public List<CopyCompletedEvent> getRecentHistory(int count) {
        return history.subList(Math.max(0, history.size() - count), history.size());
    }

    public long getRecentSuccessCount(int seconds) {
        long threshold = System.currentTimeMillis() - (seconds * 1000L);
        return history.stream()
            .filter(e -> e.timestamp() >= threshold)
            .filter(CopyCompletedEvent::isSuccess)
            .count();
    }
}

// 注册历史记录监听器
CopyHistory history = new CopyHistory();
EventListener<CopyCompletedEvent> historyListener = history::addRecord;
eventBus.register(CopyCompletedEvent.class, historyListener);
```

### 示例4: 异步处理复制完成事件

```java
AsyncEventListener<CopyCompletedEvent, CopyStats> asyncListener = event -> {
    return CompletableFuture.supplyAsync(() -> {
        // 在后台线程中处理统计数据
        CopyStats stats = computeStats(event);
        return stats;
    }).thenAccept(stats -> {
        // 更新UI显示
        updateUI(stats);
    });
};

eventBus.registerAsync(CopyCompletedEvent.class, asyncListener, CopyStats.class);
```

## 事件触发时机

`CopyCompletedEvent` 在以下情况下触发：

1. **复制成功** - 文件完整复制到目标位置
   - `result = SUCCESS`
   - `bytesCopied = fileSize`
   - `destinationPath` 有效

2. **文件已存在（重复）** - 文件已在索引中，无需复制
   - `result = SUCCESS`
   - `bytesCopied = fileSize`
   - `destinationPath = null`
   - 同时会分发 `DuplicateDetectedEvent`

3. **复制失败** - 发生 I/O 错误或中断
   - `result = FAIL`
   - `bytesCopied = 0`（或已复制的部分字节数）
   - `destinationPath = null` 或无效

4. **复制取消** - 用户手动取消或线程被中断
   - `result = CANCEL`
   - `bytesCopied` 小于 `fileSize`（已复制的部分）
   - `destinationPath` 可能有效（部分文件已写入）

## 与其他事件的关系

| 事件 | 触发时机 | 用途 |
|------|---------|------|
| `FileIndexedEvent` | 文件成功添加到索引后 | 更新索引文件列表 |
| `DuplicateDetectedEvent` | 检测到重复文件时 | 记录重复文件 |
| `CopyCompletedEvent` | 每次复制操作完成后（无论结果） | 统计和监控 |

## 注意事项

1. **事件频率高** - 每个文件复制完成都会触发，注意监听器性能
2. **线程安全** - 事件在 `CopyTask` 线程池中触发，监听器需要处理并发
3. **资源清理** - 使用 `unregister()` 移除不需要的监听器
4. **异常处理** - 监听器中的异常会被 EventBus 捕获并记录，但不会影响其他监听器

## 应用场景

- **实时进度显示** - 在 GUI 中显示当前复制的文件和进度
- **性能监控** - 计算平均复制速度和吞吐量
- **错误追踪** - 记录失败文件和错误类型
- **统计数据** - 生成复制报告（总字节数、成功率等）
- **历史查询** - 保存复制历史供用户查询
