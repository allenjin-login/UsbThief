package com.superredrock.usbthief.core;

/**
 * 服务状态枚举
 * <p>
 * 状态转换规则：
 * STOPPED → STARTING → RUNNING
 * RUNNING → STOPPING → STOPPED
 * RUNNING → PAUSED → RUNNING
 * 任意状态 → FAILED（发生错误时）
 * FAILED → STOPPING → STOPPED（恢复时）
 */
public enum ServiceState {
    /**
     * 服务未启动
     */
    STOPPED,

    /**
     * 服务正在初始化
     */
    STARTING,

    /**
     * 服务正在运行
     */
    RUNNING,

    /**
     * 服务正在关闭
     */
    STOPPING,

    /**
     * 服务发生错误
     */
    FAILED,

    /**
     * 服务暂停（调度暂停，但未停止）
     */
    PAUSED,

    /**
     * 服务被错误挂起，等待恢复
     */
    SUSPENDED
}
