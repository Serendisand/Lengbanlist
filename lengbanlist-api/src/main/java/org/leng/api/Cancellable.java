package org.leng.api;

/** 可取消的调度任务。 */
public interface Cancellable {

    void cancel();

    boolean isCancelled();
}
