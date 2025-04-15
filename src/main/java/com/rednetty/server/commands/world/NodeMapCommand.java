package com.rednetty.server.commands.world;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Command for managing and generating navigation node maps
 */
public class NodeMapCommand implements CommandExecutor, TabCompleter {
    private final YakRealms plugin;
    private static final int CHUNKS_PER_TICK = 3;
    private static final int PROGRESS_BAR_LENGTH = 20;
    private static final int CHUNK_LOAD_RADIUS = 8;
    private static final int SAVE_INTERVAL = 50;

    private final ConcurrentHashMap<UUID, GenerationTask> activeTasks;
    private final File progressDir;

    public static class GenerationTask implements Serializable {
        private static final long serialVersionUID = 1L;

        final UUID taskId;
        final UUID initiatorId;
        final String worldName;
        final int minChunkX, maxChunkX, minChunkZ, maxChunkZ;
        transient BukkitTask runningTask;
        AtomicInteger currentChunkX;
        AtomicInteger currentChunkZ;
        AtomicInteger processedChunks;
        final int totalChunks;
        final long startTime;
        volatile boolean paused;

        GenerationTask(UUID initiatorId, World world, int minX, int maxX, int minZ, int maxZ) {
            this.taskId = UUID.randomUUID();
            this.initiatorId = initiatorId;
            this.worldName = world.getName();
            // Convert block coordinates to chunk coordinates
            this.minChunkX = minX >> 4;
            this.maxChunkX = maxX >> 4;
            this.minChunkZ = minZ >> 4;
            this.maxChunkZ = maxZ >> 4;
            this.currentChunkX = new AtomicInteger(this.minChunkX);
            this.currentChunkZ = new AtomicInteger(this.minChunkZ);
            this.processedChunks = new AtomicInteger(0);
            this.totalChunks = (this.maxChunkX - this.minChunkX + 1) * (this.maxChunkZ - this.minChunkZ + 1);
            this.startTime = System.currentTimeMillis();
            this.paused = false;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            // Reinitialize atomic integers
            currentChunkX = new AtomicInteger(currentChunkX.get());
            currentChunkZ = new AtomicInteger(currentChunkZ.get());
            processedChunks = new AtomicInteger(processedChunks.get());
        }
    }

    public NodeMapCommand(YakRealms plugin) {
        this.plugin = plugin;
        this.activeTasks = new ConcurrentHashMap<>();
        this.progressDir = new File(plugin.getDataFolder(), "nodemap_progress");
        if (!progressDir.exists()) {
            progressDir.mkdirs();
        }
        loadExistingTasks();
    }

    private void loadExistingTasks() {
        File[] files = progressDir.listFiles((dir, name) -> name.endsWith(".task"));
        if (files == null) return;

        for (File file : files) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                GenerationTask task = (GenerationTask) ois.readObject();
                activeTasks.put(task.taskId, task);
                plugin.getLogger().info("Loaded existing node map task " + task.taskId);
                startGenerationTask(task);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load task from " + file.getName() + ": " + e.getMessage());
                file.delete();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.nodemap")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "generate" -> handleGenerateCommand(sender, args);
            case "progress" -> handleProgressCommand(sender, args);
            case "pause" -> handlePauseCommand(sender, args);
            case "cancel" -> handleCancelCommand(sender, args);
            case "help" -> sendHelpMessage(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendHelpMessage(sender);
            }
        }

        return true;
    }

    private void handleGenerateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }

        // Check if player already has an active task
        Optional<GenerationTask> existingTask = activeTasks.values().stream()
                .filter(task -> task.initiatorId.equals(player.getUniqueId()))
                .findFirst();

        if (existingTask.isPresent()) {
            sender.sendMessage(ChatColor.RED + "You already have an active generation task!");
            showTaskProgress(sender, existingTask.get());
            return;
        }

        int radius = 1000; // Default radius
        if (args.length > 1) {
            try {
                radius = Integer.parseInt(args[1]);
                if (radius <= 0 || radius > 10000) {
                    sender.sendMessage(ChatColor.RED + "Radius must be between 1 and 10000. Using default: 1000");
                    radius = 1000;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid radius. Using default: 1000");
            }
        }

        Location center = player.getLocation();
        GenerationTask task = new GenerationTask(
                player.getUniqueId(),
                player.getWorld(),
                center.getBlockX() - radius,
                center.getBlockX() + radius,
                center.getBlockZ() - radius,
                center.getBlockZ() + radius
        );

        activeTasks.put(task.taskId, task);
        asyncSaveTask(task);

        sender.sendMessage(ChatColor.GREEN + "Starting node map generation:");
        sender.sendMessage(ChatColor.GRAY + "World: " + task.worldName);
        sender.sendMessage(ChatColor.GRAY + "Radius: " + radius + " blocks");
        sender.sendMessage(ChatColor.GRAY + "Total chunks: " + task.totalChunks);
        sender.sendMessage(ChatColor.GRAY + "Task ID: " + task.taskId);

        startGenerationTask(task);
    }

    private void handleProgressCommand(CommandSender sender, String[] args) {
        if (activeTasks.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No active generation tasks.");
            return;
        }

        if (args.length > 1) {
            try {
                UUID taskId = UUID.fromString(args[1]);
                GenerationTask task = activeTasks.get(taskId);
                if (task != null) {
                    showTaskProgress(sender, task);
                } else {
                    sender.sendMessage(ChatColor.RED + "Task not found: " + taskId);
                }
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid task ID format.");
            }
            return;
        }

        // Show all tasks
        sender.sendMessage(ChatColor.YELLOW + "Active generation tasks:");
        activeTasks.values().forEach(task -> showTaskProgress(sender, task));
    }

    private void handlePauseCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /nodemap pause <taskId>");
            return;
        }

        try {
            UUID taskId = UUID.fromString(args[1]);
            GenerationTask task = activeTasks.get(taskId);
            if (task != null) {
                task.paused = !task.paused;
                sender.sendMessage(ChatColor.GREEN + "Task " + taskId + " is now " +
                        (task.paused ? "paused" : "resumed"));
                asyncSaveTask(task);
            } else {
                sender.sendMessage(ChatColor.RED + "Task not found: " + taskId);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid task ID format.");
        }
    }

    private void handleCancelCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /nodemap cancel <taskId>");
            return;
        }

        try {
            UUID taskId = UUID.fromString(args[1]);
            GenerationTask task = activeTasks.get(taskId);
            if (task != null) {
                if (task.runningTask != null) {
                    task.runningTask.cancel();
                }
                cleanup(task);
                sender.sendMessage(ChatColor.GREEN + "Task " + taskId + " has been cancelled.");
            } else {
                sender.sendMessage(ChatColor.RED + "Task not found: " + taskId);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid task ID format.");
        }
    }

    private void startGenerationTask(GenerationTask task) {
        task.runningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (task.paused) return;

                World world = plugin.getServer().getWorld(task.worldName);
                if (world == null) {
                    finishWithError(task, "World not found");
                    cancel();
                    return;
                }

                boolean needsSave = false;
                for (int batch = 0; batch < CHUNKS_PER_TICK && task.currentChunkX.get() <= task.maxChunkX; batch++) {
                    int x = task.currentChunkX.get();
                    int z = task.currentChunkZ.get();

                    processChunk(world, x, z);
                    task.processedChunks.incrementAndGet();
                    needsSave = true;

                    if (task.currentChunkZ.incrementAndGet() > task.maxChunkZ) {
                        task.currentChunkZ.set(task.minChunkZ);
                        if (task.currentChunkX.incrementAndGet() > task.maxChunkX) {
                            finishGeneration(task);
                            cancel();
                            return;
                        }
                    }
                }

                if (needsSave && task.processedChunks.get() % SAVE_INTERVAL == 0) {
                    asyncSaveTask(task);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void processChunk(World world, int chunkX, int chunkZ) {
        // Pre-load chunks in a more efficient pattern
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ, true);
        }

        // Here would be the node generation logic
        // For this implementation, we're just ensuring chunks are loaded and processed
        // since you don't have the actual node system in your provided code

        // Keep chunk in memory for a bit to prevent thrashing
        new BukkitRunnable() {
            @Override
            public void run() {
                // Unload chunk if it's still loaded and no players are nearby
                if (world.isChunkLoaded(chunkX, chunkZ) && !hasPlayersNearby(world, chunkX, chunkZ)) {
                    world.unloadChunkRequest(chunkX, chunkZ);
                }
            }
        }.runTaskLater(plugin, 60L); // 3 seconds later
    }

    private boolean hasPlayersNearby(World world, int chunkX, int chunkZ) {
        return world.getPlayers().stream()
                .anyMatch(p -> {
                    Location loc = p.getLocation();
                    int playerChunkX = loc.getBlockX() >> 4;
                    int playerChunkZ = loc.getBlockZ() >> 4;
                    return Math.abs(playerChunkX - chunkX) <= CHUNK_LOAD_RADIUS &&
                            Math.abs(playerChunkZ - chunkZ) <= CHUNK_LOAD_RADIUS;
                });
    }

    private void showTaskProgress(CommandSender sender, GenerationTask task) {
        double percent = (task.processedChunks.get() * 100.0) / task.totalChunks;
        long elapsed = System.currentTimeMillis() - task.startTime;
        long estimatedTotal = task.processedChunks.get() > 0 ?
                (long) (elapsed * (task.totalChunks / (double) task.processedChunks.get())) : 0;
        long remaining = estimatedTotal - elapsed;

        StringBuilder bar = new StringBuilder("[");
        int progressBlocks = (int) ((percent / 100) * PROGRESS_BAR_LENGTH);
        for (int i = 0; i < PROGRESS_BAR_LENGTH; i++) {
            bar.append(i < progressBlocks ? "=" : "-");
        }
        bar.append("]");

        sender.sendMessage(String.format(
                "%s %s %.1f%% (%d/%d) ETA: %dm %ds %s",
                ChatColor.YELLOW + bar.toString(),
                ChatColor.GREEN,
                percent,
                task.processedChunks.get(),
                task.totalChunks,
                remaining / 60000,
                (remaining % 60000) / 1000,
                task.paused ? ChatColor.RED + "[PAUSED]" : ""
        ));
    }

    private void asyncSaveTask(GenerationTask task) {
        UUID taskId = task.taskId;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File taskFile = new File(progressDir, taskId + ".task");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(taskFile))) {
                oos.writeObject(task);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save task " + taskId + ": " + e.getMessage());
            }
        });
    }

    private void finishGeneration(GenerationTask task) {
        long totalTime = (System.currentTimeMillis() - task.startTime) / 1000;
        String completionMessage = String.format(
                "Node map generation complete! Processed %d chunks | Time: %dm %ds",
                task.processedChunks.get(),
                totalTime / 60,
                totalTime % 60
        );

        plugin.getLogger().info(completionMessage);

        // Notify the initiator if they're online
        Player initiator = plugin.getServer().getPlayer(task.initiatorId);
        if (initiator != null && initiator.isOnline()) {
            initiator.sendMessage(ChatColor.GREEN + "Node map generation complete!");
            initiator.sendMessage(ChatColor.GRAY + completionMessage);
        }

        cleanup(task);
    }

    private void finishWithError(GenerationTask task, String error) {
        plugin.getLogger().severe("Node map generation failed for task " + task.taskId + ": " + error);
        Player initiator = plugin.getServer().getPlayer(task.initiatorId);
        if (initiator != null && initiator.isOnline()) {
            initiator.sendMessage(ChatColor.RED + "Node map generation failed: " + error);
        }
        cleanup(task);
    }

    private void cleanup(GenerationTask task) {
        activeTasks.remove(task.taskId);
        File taskFile = new File(progressDir, task.taskId + ".task");
        if (taskFile.exists()) {
            taskFile.delete();
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Node Map Command Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/nodemap generate [radius]" + ChatColor.GRAY + " - Generate node map in radius around you");
        sender.sendMessage(ChatColor.YELLOW + "/nodemap progress [taskId]" + ChatColor.GRAY + " - Show generation progress");
        sender.sendMessage(ChatColor.YELLOW + "/nodemap pause <taskId>" + ChatColor.GRAY + " - Pause/resume a generation task");
        sender.sendMessage(ChatColor.YELLOW + "/nodemap cancel <taskId>" + ChatColor.GRAY + " - Cancel a generation task");
        sender.sendMessage(ChatColor.YELLOW + "/nodemap help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.nodemap")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("generate", "progress", "pause", "cancel", "help").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "generate":
                    return Arrays.asList("500", "1000", "2000", "5000").stream()
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                case "progress":
                case "pause":
                case "cancel":
                    return activeTasks.keySet().stream()
                            .map(UUID::toString)
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}