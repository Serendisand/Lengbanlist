package org.leng.api;

/**
 * 调度器。
 *
 * <p><b>不要使用 {@code Bukkit.getScheduler()}</b>：核心本身支持 Folia，而 Folia 没有
 * 全局主线程，直接用 Bukkit 调度器会抛异常。走这里才能同时兼容 Spigot / Paper / Folia。
 *
 * <p><b>注意单位不一致</b>（沿用核心既有行为，避免扩展作者误判）：
 *
 * <ul>
 *   <li>{@code runSync*} 系列：{@code delay}/{@code period} 均为 <b>tick</b>（20 tick = 1 秒）</li>
 *   <li>{@code runAsyncLater}：{@code delayMillis} 为<b>毫秒</b></li>
 *   <li>{@code runAsyncRepeating}：{@code delayTicks}/{@code periodTicks} 为 <b>tick</b></li>
 * </ul>
 */
public interface Scheduler {

    /** 当前服务端是否为 Folia。 */
    boolean isFolia();

    /** 在主线程（Folia 上为当前区域线程）执行一次。 */
    Cancellable runSync(Runnable task);

    /** 延迟若干个 tick 后执行一次。 */
    Cancellable runSyncLater(Runnable task, long delayTicks);

    /** 周期性执行，周期以 tick 计。 */
    Cancellable runSyncRepeating(Runnable task, long delayTicks, long periodTicks);

    /** 立即在异步线程执行一次。 */
    void runAsync(Runnable task);

    /** 延迟若干<b>毫秒</b>后在异步线程执行一次。 */
    Cancellable runAsyncLater(Runnable task, long delayMillis);

    /** 周期性异步执行，周期以 tick 计。 */
    Cancellable runAsyncRepeating(Runnable task, long delayTicks, long periodTicks);
}
