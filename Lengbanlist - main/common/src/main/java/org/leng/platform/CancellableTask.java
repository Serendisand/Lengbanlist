package org.leng.platform;

/** 可在主线程繁忙导致 Web 超时时尝试取消的同步任务包装。 */
public interface CancellableTask {
    CancellableTask NOOP = () -> {};

    void cancel();
}
