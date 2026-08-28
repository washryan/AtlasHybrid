package dev.atlashybrid.runtime.scheduler;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

public final class AtlasScheduler implements BukkitScheduler {
    private final AtomicInteger ids = new AtomicInteger();
    private final PriorityQueue<Task> queue = new PriorityQueue<>(Comparator.comparingLong(Task::dueTick).thenComparingInt(Task::getTaskId));
    private final Logger logger;
    private long currentTick;
    private Thread serverThread;
    private boolean accepting = true;

    public AtlasScheduler(Logger logger) {
        this.logger = logger;
    }

    @Override public BukkitTask runTask(Plugin plugin, Runnable task) { return schedule(plugin, task, 1); }
    @Override public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) { return schedule(plugin, task, Math.max(1, delay)); }

    private synchronized Task schedule(Plugin plugin, Runnable runnable, long delay) {
        if (!accepting) throw new IllegalStateException("Scheduler is stopping");
        Task task = new Task(ids.incrementAndGet(), plugin, runnable, currentTick + delay);
        queue.add(task);
        return task;
    }

    public void tick() {
        if (serverThread == null) serverThread = Thread.currentThread();
        if (serverThread != Thread.currentThread()) throw new IllegalStateException("Scheduler tick must run on the server thread");
        List<Task> due = new ArrayList<>();
        synchronized (this) {
            currentTick++;
            while (!queue.isEmpty() && queue.peek().dueTick <= currentTick) due.add(queue.remove());
        }
        for (Task task : due) {
            if (task.cancelled || !task.owner.isEnabled()) continue;
            try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(task.owner.getName())) { task.runnable.run(); }
            catch (Throwable throwable) { logger.log(Level.SEVERE, "Scheduled task " + task.id + " failed for " + task.owner.getName(), throwable); }
        }
    }

    @Override public synchronized void cancelTasks(Plugin plugin) { queue.stream().filter(task -> task.owner == plugin).forEach(Task::cancel); }
    public synchronized void stopAccepting() { accepting = false; }
    public synchronized int pendingTasks() { return (int) queue.stream().filter(task -> !task.cancelled).count(); }
    public synchronized int pendingTasks(Plugin plugin) {
        return (int) queue.stream().filter(task -> !task.cancelled && task.owner == plugin).count();
    }
    public synchronized long currentTick() { return currentTick; }

    private static final class Task implements BukkitTask {
        private final int id;
        private final Plugin owner;
        private final Runnable runnable;
        private final long dueTick;
        private volatile boolean cancelled;

        private Task(int id, Plugin owner, Runnable runnable, long dueTick) {
            this.id = id;
            this.owner = Objects.requireNonNull(owner, "owner");
            this.runnable = Objects.requireNonNull(runnable, "runnable");
            this.dueTick = dueTick;
        }

        long dueTick() { return dueTick; }
        @Override public int getTaskId() { return id; }
        @Override public Plugin getOwner() { return owner; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void cancel() { cancelled = true; }
    }
}
