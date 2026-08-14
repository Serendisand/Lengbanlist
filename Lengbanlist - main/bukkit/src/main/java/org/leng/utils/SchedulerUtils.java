package org.leng.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.leng.Lengbanlist;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class SchedulerUtils {

    private static boolean folia;
    private static boolean initialized;


    private static Object globalRegionScheduler;
    private static Object asyncScheduler;


    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method asyncRunAtFixedRate;
    private static Method entityGetScheduler;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method scheduledTaskCancel;

    private SchedulerUtils() {}

    public static void init(Lengbanlist plugin) {
        if (initialized) return;
        initialized = true;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;

            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            globalRegionScheduler = getGlobalRegionScheduler.invoke(null);

            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncScheduler.invoke(null);

            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            scheduledTaskCancel = scheduledTaskClass.getMethod("cancel");

            Class<?> globalClass = globalRegionScheduler.getClass();
            globalRun = globalClass.getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayed = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

            Class<?> asyncClass = asyncScheduler.getClass();
            asyncRunNow = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);
            asyncRunDelayed = asyncClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
            asyncRunAtFixedRate = asyncClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

            entityGetScheduler = Entity.class.getMethod("getScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entitySchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

            plugin.getLogger().info("Folia 调度器已初始化（反射缓存模式）");
        } catch (Exception e) {
            folia = false;
            plugin.getLogger().info("使用传统 Bukkit 调度器");
        }
    }

    public static boolean isFolia() {
        return folia;
    }


    public static SchedulerTask runTask(Lengbanlist plugin, Runnable task) {
        if (folia) {
            try {
                Object result = globalRun.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run());
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia global run failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
        return new SchedulerTask(bt);
    }

    public static SchedulerTask runTask(Lengbanlist plugin, CommandSender sender, Runnable task) {
        if (sender instanceof Entity) {
            return runTask(plugin, (Entity) sender, task);
        }
        return runTask(plugin, task);
    }

    public static SchedulerTask runTask(Lengbanlist plugin, Entity entity, Runnable task) {
        if (folia && entity != null) {
            try {
                Object scheduler = entityGetScheduler.invoke(entity);
                Object result = entityRun.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) () -> {});
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity run failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        return runTask(plugin, task);
    }

    public static SchedulerTask runTaskLater(Lengbanlist plugin, Runnable task, long delayTicks) {
        if (folia) {
            try {
                Object result = globalRunDelayed.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run(), delayTicks);
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia global runDelayed failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return new SchedulerTask(bt);
    }

    public static SchedulerTask runTaskLater(Lengbanlist plugin, Entity entity, Runnable task, long delayTicks) {
        if (folia && entity != null) {
            try {
                Object scheduler = entityGetScheduler.invoke(entity);
                Object result = entityRunDelayed.invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), (Runnable) () -> {}, delayTicks);
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia entity runDelayed failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        return runTaskLater(plugin, task, delayTicks);
    }

    public static SchedulerTask runTaskTimer(Lengbanlist plugin, Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            try {
                Object result = globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run(), delayTicks, periodTicks);
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia global runAtFixedRate failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new SchedulerTask(bt);
    }


    public static void runAsync(Lengbanlist plugin, Runnable task) {
        if (folia) {
            try {
                asyncRunNow.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async runNow failed: " + e.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runAsyncDelayed(Lengbanlist plugin, Runnable task, long delayMs) {
        if (folia) {
            try {
                asyncRunDelayed.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run(), delayMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async runDelayed failed: " + e.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayMs / 50);
        }
    }


    public static SchedulerTask runTaskTimerAsynchronously(Lengbanlist plugin, Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            try {
                Object result = asyncRunAtFixedRate.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run(), delayTicks * 50, periodTicks * 50, TimeUnit.MILLISECONDS);
                return new SchedulerTask(result);
            } catch (Exception e) {
                plugin.getLogger().warning("Folia async runAtFixedRate failed: " + e.getMessage());
                return new SchedulerTask((Object) null);
            }
        }
        BukkitTask bt = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new SchedulerTask(bt);
    }


    public static class SchedulerTask {
        private final Object foliaTask;
        private final BukkitTask bukkitTask;

        SchedulerTask(Object foliaTask) {
            this.foliaTask = foliaTask;
            this.bukkitTask = null;
        }

        SchedulerTask(BukkitTask bukkitTask) {
            this.foliaTask = null;
            this.bukkitTask = bukkitTask;
        }

        public void cancel() {
            if (foliaTask != null && scheduledTaskCancel != null) {
                try {
                    scheduledTaskCancel.invoke(foliaTask);
                } catch (Exception ignored) {}
            }
            if (bukkitTask != null) {
                bukkitTask.cancel();
            }
        }
    }
}
