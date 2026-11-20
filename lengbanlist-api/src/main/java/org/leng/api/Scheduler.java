package org.leng.api;

import java.time.Duration;

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

    /**
     * 在那个实体所属的线程上执行一次。
     *
     * <p>Folia 上玩家/实体分属不同区域，改玩家可见性、发消息这类操作应当走这里，
     * 而不是全局 {@link #runSync(Runnable)}。Spigot/Paper 上两者等价。
     */
    Cancellable runSync(org.bukkit.entity.Entity entity, Runnable task);

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

    // ------------------------------------------------------------ 类型化时长重载
    // 用 Duration 表达"1 秒""5 分钟"，避免 tick / 毫秒两种单位在调用处混淆。

    /** 延迟 {@code delay}（向上取整到 tick）后同步执行一次。 */
    default Cancellable runSyncLater(Runnable task, Duration delay) {
        return runSyncLater(task, toTicks(delay));
    }

    /** 以 {@code period} 为周期同步执行；周期至少 1 tick。 */
    default Cancellable runSyncRepeating(Runnable task, Duration delay, Duration period) {
        return runSyncRepeating(task, toTicks(delay), Math.max(1L, toTicks(period)));
    }

    /** 延迟 {@code delay} 后在异步线程执行一次。 */
    default Cancellable runAsyncLater(Runnable task, Duration delay) {
        return runAsyncLater(task, Math.max(0L, delay == null ? 0L : delay.toMillis()));
    }

    /** 以 {@code period} 为周期异步执行；周期至少 1 tick。 */
    default Cancellable runAsyncRepeating(Runnable task, Duration delay, Duration period) {
        return runAsyncRepeating(task, toTicks(delay), Math.max(1L, toTicks(period)));
    }

    private static long toTicks(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0L;
        }
        return Math.max(1L, (duration.toMillis() + 49L) / 50L);
    }
}
