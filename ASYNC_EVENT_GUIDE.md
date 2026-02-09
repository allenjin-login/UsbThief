# 异步事件框架使用说明

## 概述

UsbThief事件框架现已支持多线程异步处理和回调结果收集。在保持原有同步事件功能不变的基础上，新增了异步事件监听器接口和相关方法。

## 核心组件

### 1. AsyncEventListener<T, R> 接口

异步事件监听器接口，支持返回 `CompletableFuture<R>`。

```java
AsyncEventListener<DeviceInsertedEvent, String> listener = event -> {
    return CompletableFuture.supplyAsync(() -> {
        // 异步处理事件
        return "处理结果";
    });
};
```

### 2. EventBus 新增方法

#### registerAsync(Class<T> eventClass, AsyncEventListener<T, R> listener, Class<R> resultType)

注册异步监听器并指定返回类型（用于结果收集）。

```java
eventBus.registerAsync(DeviceInsertedEvent.class, listener, String.class);
```

#### registerAsync(Class<T> eventClass, AsyncEventListener<T, R> listener)

注册异步监听器（不需要结果收集时使用）。

```java
eventBus.registerAsync(DeviceInsertedEvent.class, listener);
```

#### dispatchAsync(T event)

异步分发事件到所有监听器（包括同步和异步）。

```java
CompletableFuture<Void> future = eventBus.dispatchAsync(event);
future.thenRun(() -> {
    System.out.println("所有监听器处理完成");
});
```

#### dispatchWithResult(T event, Class<R> resultType)

分发事件并收集指定类型的异步监听器返回结果。

```java
CompletableFuture<List<String>> future = eventBus.dispatchWithResult(event, String.class);
future.thenAccept(results -> {
    results.forEach(result -> System.out.println(result));
});
```

#### dispatchWithResultMap(T event, Class<R> resultType)

分发事件并以Map形式收集异步监听器返回结果。

```java
CompletableFuture<Map<AsyncEventListener<MyEvent, String>, String>> future =
    eventBus.dispatchWithResultMap(event, String.class);
future.thenAccept(results -> {
    results.forEach((listener, result) -> {
        System.out.println("监听器 " + listener.hashCode() + ": " + result);
    });
});
```

## 使用示例

### 示例1: 基本异步监听器

```java
AsyncEventListener<DeviceInsertedEvent, String> listener = event -> {
    return CompletableFuture.supplyAsync(() -> {
        // 模拟异步处理
        Thread.sleep(100);
        return "设备插入: " + event.device().getRootPath();
    });
};

eventBus.registerAsync(DeviceInsertedEvent.class, listener, String.class);
```

### 示例2: 混合使用同步和异步监听器

```java
// 同步监听器（原有功能）
EventListener<DeviceInsertedEvent> syncListener = event -> {
    System.out.println("同步处理: " + event.device());
};
eventBus.register(DeviceInsertedEvent.class, syncListener);

// 异步监听器（新功能）
AsyncEventListener<DeviceInsertedEvent, String> asyncListener = event -> {
    return CompletableFuture.completedFuture("异步处理完成");
};
eventBus.registerAsync(DeviceInsertedEvent.class, asyncListener, String.class);

// 分发事件（会同时调用同步和异步监听器）
eventBus.dispatchAsync(event).join();
```

### 示例3: 收集多个异步监听器的结果

```java
// 注册多个返回不同类型结果的监听器
AsyncEventListener<DeviceInsertedEvent, String> stringListener = event -> {
    return CompletableFuture.completedFuture(event.device().getRootPath().toString());
};
eventBus.registerAsync(DeviceInsertedEvent.class, stringListener, String.class);

AsyncEventListener<DeviceInsertedEvent, Integer> intListener = event -> {
    return CompletableFuture.completedFuture(event.device().getActiveTaskCount());
};
eventBus.registerAsync(DeviceInsertedEvent.class, intListener, Integer.class);

// 收集String类型的结果
CompletableFuture<List<String>> results = eventBus.dispatchWithResult(event, String.class);
results.thenAccept(list -> {
    System.out.println("收集到 " + list.size() + " 个String结果");
});
```

### 示例4: 错误处理

```java
AsyncEventListener<DeviceRemovedEvent, String> listener = event -> {
    return CompletableFuture.supplyAsync(() -> {
        // 可能抛出异常的代码
        if (errorCondition) {
            throw new RuntimeException("处理失败");
        }
        return "成功";
    }).exceptionally(e -> {
        System.err.println("异常处理: " + e.getMessage());
        return "错误: " + e.getMessage();
    });
};
```

## 线程池

异步事件分发使用 `QueueManager.pool` 线程池执行，确保与项目其他组件共享线程资源。

## 向后兼容性

原有的同步事件功能完全保留，不受影响：
- `register()` / `unregister()` 方法不变
- `dispatch()` 同步分发方法不变
- `EventListener` 接口不变
- 现有代码无需修改

## 最佳实践

1. **选择合适的注册方法**:
   - 需要收集结果时：`registerAsync(..., resultType)`
   - 不需要结果时：`registerAsync(...)` 或 `register(...)` (同步)

2. **异步监听器中避免阻塞**:
   - 使用 `CompletableFuture.supplyAsync()` 在线程池中执行耗时操作
   - 避免在异步回调中执行长时间阻塞操作

3. **错误处理**:
   - 使用 `exceptionally()` 处理异步操作中的异常
   - EventBus会捕获并记录监听器中的异常，但不会中断其他监听器的执行

4. **资源清理**:
   - 使用完毕后调用 `unregisterAsync()` 或 `unregister()` 移除监听器
   - 避免监听器泄露导致内存泄漏

## 注意事项

1. 异步监听器的执行顺序不保证
2. 异步监听器在 `QueueManager.pool` 中执行，注意线程安全
3. 使用 `dispatchWithResult` 时，只有注册时指定了 `resultType` 的监听器才会被收集结果
4. `dispatchAsync` 返回的 `CompletableFuture` 只有当所有监听器（包括同步和异步）都完成时才会完成
