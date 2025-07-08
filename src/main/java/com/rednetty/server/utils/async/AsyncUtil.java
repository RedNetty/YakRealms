package com.rednetty.server.utils.async;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility for easier async operations and task management
 * Simplifies common async patterns and provides better task management
 */
public class AsyncUtil {
    private static JavaPlugin plugin;
    private static final ConcurrentHashMap<String, BukkitTask> namedTasks = new ConcurrentHashMap<>();

    public static void init(JavaPlugin plugin) {
        AsyncUtil.plugin = plugin;
    }

    /**
     * Run task async and return to main thread with result
     * @param supplier The async operation to perform
     * @return CompletableFuture with the result
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = supplier.get();
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(result));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> future.completeExceptionally(e));
            }
        });
        return future;
    }

    /**
     * Run task async then execute callback on main thread
     * @param async The async operation
     * @param sync The sync callback with the result
     */
    public static <T> void runAsyncThenSync(Supplier<T> async, Consumer<T> sync) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = async.get();
                Bukkit.getScheduler().runTask(plugin, () -> sync.accept(result));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Schedule repeating task with name for easy cancellation
     * @param name Unique name for the task
     * @param task The task to run
     * @param delay Initial delay in ticks
     * @param period Period between executions in ticks
     */
    public static void scheduleRepeating(String name, Runnable task, long delay, long period) {
        cancelNamed(name);
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        namedTasks.put(name, bukkitTask);
    }

    /**
     * Schedule delayed task with name
     * @param name Unique name for the task
     * @param task The task to run
     * @param delay Delay in ticks
     */
    public static void scheduleDelayed(String name, Runnable task, long delay) {
        cancelNamed(name);
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        namedTasks.put(name, bukkitTask);
    }

    /**
     * Schedule async repeating task with name
     */
    public static void scheduleAsyncRepeating(String name, Runnable task, long delay, long period) {
        cancelNamed(name);
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        namedTasks.put(name, bukkitTask);
    }

    /**
     * Schedule async delayed task with name
     */
    public static void scheduleAsyncDelayed(String name, Runnable task, long delay) {
        cancelNamed(name);
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        namedTasks.put(name, bukkitTask);
    }

    /**
     * Cancel named task
     * @param name Name of the task to cancel
     */
    public static void cancelNamed(String name) {
        BukkitTask task = namedTasks.remove(name);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Cancel all named tasks
     */
    public static void cancelAllNamed() {
        namedTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        namedTasks.clear();
    }

    /**
     * Check if named task exists and is running
     */
    public static boolean isTaskRunning(String name) {
        BukkitTask task = namedTasks.get(name);
        return task != null && !task.isCancelled();
    }

    /**
     * Convert time units to ticks
     * @param time The time value
     * @param unit The time unit
     * @return Equivalent ticks
     */
    public static long toTicks(long time, TimeUnit unit) {
        return (unit.toMillis(time) / 50); // 50ms = 1 tick
    }

    /**
     * Convert ticks to milliseconds
     */
    public static long toMillis(long ticks) {
        return ticks * 50;
    }

    /**
     * Retry operation with exponential backoff
     * @param operation The operation to retry
     * @param maxAttempts Maximum retry attempts
     * @return CompletableFuture with the result
     */
    public static <T> CompletableFuture<T> retry(Supplier<T> operation, int maxAttempts) {
        return retry(operation, maxAttempts, 1000, 2.0);
    }

    /**
     * Retry operation with custom backoff settings
     */
    public static <T> CompletableFuture<T> retry(Supplier<T> operation, int maxAttempts, long baseDelayMs, double backoffMultiplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        retryInternal(operation, maxAttempts, baseDelayMs, backoffMultiplier, 1, future);
        return future;
    }

    private static <T> void retryInternal(Supplier<T> operation, int maxAttempts, long baseDelayMs,
                                          double backoffMultiplier, int attempt, CompletableFuture<T> future) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = operation.get();
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(result));
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    Bukkit.getScheduler().runTask(plugin, () -> future.completeExceptionally(e));
                    return;
                }

                long delay = (long) (baseDelayMs * Math.pow(backoffMultiplier, attempt - 1));
                scheduleDelayed("retry_" + future.hashCode(), () ->
                                retryInternal(operation, maxAttempts, baseDelayMs, backoffMultiplier, attempt + 1, future),
                        toTicks(delay, TimeUnit.MILLISECONDS));
            }
        });
    }

    /**
     * Execute multiple async tasks and wait for all to complete
     */
    @SafeVarargs
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... futures) {
        return CompletableFuture.allOf(futures);
    }

    /**
     * Execute multiple async tasks and return first completed
     */
    @SafeVarargs
    public static <T> CompletableFuture<T> anyOf(CompletableFuture<T>... futures) {
        return (CompletableFuture<T>) CompletableFuture.anyOf(futures);
    }

    /**
     * Chain async operations
     */
    public static <T, U> CompletableFuture<U> chain(Supplier<T> first, java.util.function.Function<T, U> second) {
        return supplyAsync(first).thenApply(second);
    }

    /**
     * Execute with timeout
     */
    public static <T> CompletableFuture<T> withTimeout(Supplier<T> operation, long timeout, TimeUnit unit) {
        CompletableFuture<T> future = supplyAsync(operation);

        scheduleDelayed("timeout_" + future.hashCode(), () -> {
            if (!future.isDone()) {
                future.completeExceptionally(new java.util.concurrent.TimeoutException("Operation timed out"));
            }
        }, toTicks(timeout, unit));

        return future;
    }
}