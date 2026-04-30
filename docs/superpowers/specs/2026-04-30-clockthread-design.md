# ClockThread 通用定时组件 + SLM 重构

## 背景

当前 `ClockThread` 是一个极简的倒计时线程。需要扩展为通用定时/调度工具，具备 CompletableFuture 回调、生命周期控制和延迟执行能力，然后用它重构 `SnifferLifecycleManager` 的 cooldown/restart 逻辑。

## ClockThread 设计

### 类签名

```java
public class ClockThread extends Thread
```

位于 `com.superredrock.usbthief.core` 包。

### 构造器

```java
public ClockThread(TimeUnit unit, long delay)  // 指定单位和延迟
public ClockThread(long delayMillis)            // 便捷：毫秒
```

- 构造时设为 daemon 线程
- 线程名默认 `ClockThread`，可通过 `setName()` 覆盖

### CompletableFuture 集成

```java
private final CompletableFuture<Void> future = new CompletableFuture<>();

public CompletableFuture<Void> future()         // 获取关联的 CF
public ClockThread thenRun(Runnable action)     // 便捷: future().thenRun()，返回 this
```

**行为：**
- 倒计时正常结束 → `future.complete(null)` → 所有 `thenRun` 回调触发
- `cancel()` → `future.cancel(true)` → `CancellationException`
- 倒计时过程中异常 → `future.completeExceptionally(e)`
- 回调在 ClockThread 线程内执行

### 生命周期控制

```java
public void cancel()       // 取消倒计时，中断线程，触发 CF cancel
public void pause()        // 暂停（delay 停止递减，线程 wait）
public void resume()       // 恢复倒计时（notify）
public void restart()      // 重置 delay 为初始值并重新开始
```

### 查询

```java
public long getRemaining(TimeUnit unit)  // 剩余时间（转换单位）
public boolean isDone()                  // 倒计时是否完成
public boolean isPaused()                // 是否暂停
```

### run() 逻辑

```
while (remaining > 0 && !interrupted):
    if paused: wait() (释放锁，等待 notify)
    else: sleep(1 unit), remaining--
if remaining == 0: future.complete(null)
```

用 `synchronized` + `wait/notify` 实现 pause/resume（不是 busy loop）。

### 线程安全

- `remaining` 用 `volatile long`
- pause/resume/cancel 用 `synchronized(this)` 保护
- `cancel()` 调用 `interrupt()` + `future.cancel()`

## SnifferLifecycleManager 重构

### 替换 cooldown/restart 轮询

**移除：**
- `cooldowns` map (ConcurrentHashMap<String, Long>)
- `pendingRestarts` set (Set<String>)

**新增：**
- `timers` map (ConcurrentHashMap<String, ClockThread>)

### scheduleRestart 改造

```java
private void scheduleRestart(String serial, RestartReason reason) {
    long delayMs = getRestartDelayMs(reason);

    ClockThread timer = new ClockThread(TimeUnit.MILLISECONDS, delayMs)
        .thenRun(() -> {
            timers.remove(serial);
            Volume vol = getVolumeBySerial(serial);
            if (vol != null && vol.getState() == IDLE && !sniffers.containsKey(serial)) {
                createSniffer(vol);
            }
        });
    timers.put(serial, timer);
    timer.start();
}
```

### tick() 简化

只保留两个职责：
1. 为 IDLE volume 创建 sniffer（排除已有 sniffer 或 timer 中的）
2. 清理已结束的 sniffer entry

移除 pendingRestarts 轮询逻辑。

### stop() 改造

```java
public void stop(String serialNumber) {
    ClockThread timer = timers.remove(serialNumber);
    if (timer != null) timer.cancel();
    SnifferEntry entry = sniffers.remove(serialNumber);
    if (entry != null) entry.sniffer.close();
}
```

### getRemainingCooldownMs 改造

```java
public long getRemainingCooldownMs(String serialNumber) {
    ClockThread timer = timers.get(serialNumber);
    return timer != null ? timer.getRemaining(TimeUnit.MILLISECONDS) : 0;
}
```

### restart() / pause() 改造

- `restart()`: 取消旧 timer + 旧 sniffer，直接 createSniffer
- `pause()`: 取消旧 sniffer，scheduleRestart

### cleanup() 改造

遍历 `timers.values()` 全部 cancel，然后清理 sniffers。

### getDebugSnapshots 适配

- 从 `timers` 获取 cooldown 信息替代 `pendingRestarts`
- 用 `timer.getRemaining()` 获取剩余时间
- 用 `timer.isDone()` 判断是否已完成

## 不变的部分

- Service 基类不改动
- EventBus 监听逻辑不变
- Sniffer 本身不变
- RestartReason 枚举不变
- 配置项 (ConfigSchema) 不变
