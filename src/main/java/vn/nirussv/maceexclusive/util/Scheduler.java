package vn.nirussv.maceexclusive.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public final class Scheduler {

    private static final boolean FOLIA = isFoliaServer();

    private Scheduler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Task runTask(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return Task.folia(Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run()));
        }
        return Task.bukkit(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static Task runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            return Task.folia(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), foliaTicks(delayTicks)));
        }
        return Task.bukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static Task runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return Task.folia(Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                foliaTicks(delayTicks),
                foliaTicks(periodTicks)
            ));
        }
        return Task.bukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    public static Task runTaskAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return Task.folia(Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run()));
        }
        return Task.bukkit(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    public static Task runTaskLaterAsync(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            return Task.folia(Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                scheduledTask -> task.run(),
                ticksToMillis(foliaTicks(delayTicks)),
                TimeUnit.MILLISECONDS
            ));
        }
        return Task.bukkit(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    public static Task runTaskTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return Task.folia(Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                ticksToMillis(foliaTicks(delayTicks)),
                ticksToMillis(foliaTicks(periodTicks)),
                TimeUnit.MILLISECONDS
            ));
        }
        return Task.bukkit(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    public static Task runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (!FOLIA || !isUsable(entity)) {
            return runTask(plugin, task);
        }
        ScheduledTask scheduledTask = entity.getScheduler().run(plugin, ignored -> task.run(), null);
        return scheduledTask == null ? runTask(plugin, task) : Task.folia(scheduledTask);
    }

    public static Task runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!FOLIA || !isUsable(entity)) {
            return runTaskLater(plugin, task, delayTicks);
        }
        ScheduledTask scheduledTask = entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, foliaTicks(delayTicks));
        return scheduledTask == null ? runTaskLater(plugin, task, delayTicks) : Task.folia(scheduledTask);
    }

    public static Task runLocationTaskLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (!FOLIA || location == null || location.getWorld() == null) {
            return runTaskLater(plugin, task, delayTicks);
        }
        return Task.folia(Bukkit.getRegionScheduler().runDelayed(plugin, location, ignored -> task.run(), foliaTicks(delayTicks)));
    }

    public static Task runLocationTaskTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA || location == null || location.getWorld() == null) {
            return runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
        return Task.folia(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, ignored -> task.run(), foliaTicks(delayTicks), foliaTicks(periodTicks)));
    }

    public static Task runEntityTaskTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA || !isUsable(entity)) {
            return runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
        ScheduledTask scheduledTask = entity.getScheduler().runAtFixedRate(plugin, ignored -> task.run(), null, foliaTicks(delayTicks), foliaTicks(periodTicks));
        return scheduledTask == null ? runTaskTimer(plugin, task, delayTicks, periodTicks) : Task.folia(scheduledTask);
    }

    private static long foliaTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    private static long ticksToMillis(long ticks) {
        return ticks * 50L;
    }

    private static boolean isUsable(Entity entity) {
        return entity != null && entity.isValid();
    }

    private static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static final class Task {

        private final BukkitTask bukkitTask;
        private final ScheduledTask foliaTask;

        private Task(BukkitTask bukkitTask, ScheduledTask foliaTask) {
            this.bukkitTask = bukkitTask;
            this.foliaTask = foliaTask;
        }

        private static Task bukkit(BukkitTask task) {
            return new Task(task, null);
        }

        private static Task folia(ScheduledTask task) {
            return new Task(null, task);
        }

        public void cancel() {
            if (bukkitTask != null) {
                bukkitTask.cancel();
                return;
            }
            if (foliaTask != null) {
                foliaTask.cancel();
            }
        }

        public boolean isCancelled() {
            if (bukkitTask != null) {
                return bukkitTask.isCancelled();
            }
            if (foliaTask == null) {
                return true;
            }
            ScheduledTask.ExecutionState state = foliaTask.getExecutionState();
            return state == ScheduledTask.ExecutionState.CANCELLED || state == ScheduledTask.ExecutionState.CANCELLED_RUNNING;
        }
    }
}
