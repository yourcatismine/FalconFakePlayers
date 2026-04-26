package com.falconfk.bot;

import com.falconfk.FalconFKPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

public final class FalconBotManager {

  private static final String NAME_POOL_FILE = "bot-names.yml";
  private static final String MESSAGE_POOL_FILE = "bot-messages.yml";

  private final FalconFKPlugin plugin;
  private final Set<UUID> activeBots = ConcurrentHashMap.newKeySet();
  private final Map<String, UUID> botNames = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> chatEnabled = new ConcurrentHashMap<>();
  private final Map<UUID, ScheduledTask> chatTasks = new ConcurrentHashMap<>();
  private final Set<String> usedNames = Collections.synchronizedSet(new HashSet<>());
  private final List<String> namePool = new ArrayList<>();
  private final List<String> cleanNamePool = new ArrayList<>();
  private final List<String> chatMessagePool = new ArrayList<>();

  public FalconBotManager(FalconFKPlugin plugin) {
    this.plugin = plugin;
    reloadPools();
  }

  public int summonBots(Player requester, int amount, String customBaseName) {
    if (amount <= 0) {
      return 0;
    }

    Location baseLocation = resolveSpawnLocation(requester);
    if (baseLocation == null || baseLocation.getWorld() == null) {
      return 0;
    }

    queueSummon(baseLocation, amount, customBaseName);
    return amount;
  }

  private void queueSummon(Location baseLocation, int amount, String customBaseName) {
    Bukkit.getAsyncScheduler()
        .runNow(
            plugin,
            ignored -> {
              List<BotPlan> plans = new ArrayList<>(amount);
              List<CompletableFuture<?>> warmups = new ArrayList<>(amount);

              for (int index = 0; index < amount; index++) {
                String botName = createBotName(customBaseName, index, amount);
                if (botName == null) {
                  continue;
                }

                UUID uuid = UUID.randomUUID();
                plans.add(new BotPlan(uuid, botName));
                warmups.add(preloadLuckPermsUser(uuid));
              }

              CompletableFuture.allOf(warmups.toArray(new CompletableFuture[0]))
                  .whenComplete(
                      (ignored2, throwable) ->
                          Bukkit.getRegionScheduler()
                              .runAtFixedRate(
                                  plugin,
                                  baseLocation,
                                  task -> {
                                    if (plans.isEmpty()) {
                                      task.cancel();
                                      return;
                                    }

                                    BotPlan plan = plans.removeFirst();
                                    Player bot = NmsBotSpawner.spawnBot(plan.uuid(), plan.name(), baseLocation);
                                    if (bot == null) {
                                      if (plans.isEmpty()) {
                                        task.cancel();
                                      }
                                      return;
                                    }

                                    activeBots.add(bot.getUniqueId());
                                    botNames.put(bot.getName().toLowerCase(), bot.getUniqueId());
                                    chatEnabled.put(bot.getUniqueId(), true);
                                    usedNames.add(bot.getName().toLowerCase());
                                    configureSpawnedBot(bot);
                                    startChatTask(bot);

                                    if (plans.isEmpty()) {
                                      task.cancel();
                                    }
                                  },
                                  1L,
                                  1L));
            });
  }

  private CompletableFuture<Void> preloadLuckPermsUser(UUID uuid) {
    if (!plugin.isLuckPermsAvailable()) {
      return CompletableFuture.completedFuture(null);
    }

    try {
      LuckPerms luckPerms = LuckPermsProvider.get();
      if (luckPerms == null) {
        return CompletableFuture.completedFuture(null);
      }
      return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> null);
    } catch (Throwable ignored) {
      return CompletableFuture.completedFuture(null);
    }
  }

  private int summonBotsNow(Location baseLocation, int amount, String customBaseName) {
    int spawned = 0;
    for (int index = 0; index < amount; index++) {
      String botName = createBotName(customBaseName, index, amount);
      if (botName == null) {
        continue;
      }

      UUID uuid = UUID.randomUUID();
      Player bot = NmsBotSpawner.spawnBot(uuid, botName, baseLocation);
      if (bot == null) {
        continue;
      }

      activeBots.add(bot.getUniqueId());
      botNames.put(bot.getName().toLowerCase(), bot.getUniqueId());
      chatEnabled.put(bot.getUniqueId(), true);
      usedNames.add(bot.getName().toLowerCase());
      configureSpawnedBot(bot);
      startChatTask(bot);
      spawned++;
    }

    return spawned;
  }

  public void despawnAll() {
    List<UUID> snapshot = new ArrayList<>(activeBots);
    for (UUID uuid : snapshot) {
      Player bot = Bukkit.getPlayer(uuid);
      if (bot != null) {
        bot.getScheduler().execute(plugin, () -> NmsBotSpawner.removeBot(bot), () -> NmsBotSpawner.removeBot(bot), 0L);
      }
      activeBots.remove(uuid);
      chatEnabled.remove(uuid);
      ScheduledTask task = chatTasks.remove(uuid);
      if (task != null) {
        task.cancel();
      }
    }
    botNames.clear();
    chatEnabled.clear();
    chatTasks.clear();
    usedNames.clear();
    plugin.getLogger().info("Removed " + snapshot.size() + " fake bot(s).");
  }

  public boolean despawnBot(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }

    UUID uuid = botNames.get(name.toLowerCase());
    if (uuid == null) {
      return false;
    }

    Player bot = Bukkit.getPlayer(uuid);
    if (bot != null) {
      bot.getScheduler().execute(plugin, () -> NmsBotSpawner.removeBot(bot), () -> NmsBotSpawner.removeBot(bot), 0L);
    }

    activeBots.remove(uuid);
    botNames.remove(name.toLowerCase());
    chatEnabled.remove(uuid);
    ScheduledTask task = chatTasks.remove(uuid);
    if (task != null) {
      task.cancel();
    }
    usedNames.remove(name.toLowerCase());
    return true;
  }

  public List<String> getActiveBotNames() {
    return new ArrayList<>(botNames.keySet());
  }

  public boolean hasBot(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    return botNames.containsKey(name.toLowerCase());
  }

  public boolean setChatEnabled(String name, boolean enabled) {
    if (name == null || name.isBlank()) {
      return false;
    }

    UUID uuid = botNames.get(name.toLowerCase());
    if (uuid == null) {
      return false;
    }

    chatEnabled.put(uuid, enabled);
    return true;
  }

  public int setChatEnabledAll(boolean enabled) {
    int count = 0;
    for (UUID uuid : activeBots) {
      chatEnabled.put(uuid, enabled);
      count++;
    }
    return count;
  }

  public int toggleChatAll() {
    int count = 0;
    for (UUID uuid : activeBots) {
      boolean next = !chatEnabled.getOrDefault(uuid, true);
      chatEnabled.put(uuid, next);
      count++;
    }
    return count;
  }

  public boolean toggleChat(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }

    UUID uuid = botNames.get(name.toLowerCase());
    if (uuid == null) {
      return false;
    }

    boolean next = !chatEnabled.getOrDefault(uuid, true);
    chatEnabled.put(uuid, next);
    return next;
  }

  public boolean isChatEnabled(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }

    UUID uuid = botNames.get(name.toLowerCase());
    return uuid != null && chatEnabled.getOrDefault(uuid, true);
  }

  private void configureSpawnedBot(Player bot) {
    bot.setInvulnerable(false);
    bot.setCollidable(true);
    bot.setAllowFlight(false);
    bot.setFlying(false);
    bot.setGravity(true);
    bot.setCanPickupItems(true);
    try {
      bot.displayName(net.kyori.adventure.text.Component.text(bot.getName()));
    } catch (Exception ignored) {
    }
  }

  private void startChatTask(Player bot) {
    UUID uuid = bot.getUniqueId();
    ScheduledTask existing = chatTasks.remove(uuid);
    if (existing != null) {
      existing.cancel();
    }

    ScheduledTask task =
        bot.getScheduler().runAtFixedRate(
            plugin,
            scheduledTask -> {
              if (!chatEnabled.getOrDefault(uuid, true)) {
                return;
              }
              if (ThreadLocalRandom.current().nextInt(4) != 0) {
                return;
              }
              String message = randomChatMessage(bot);
              if (message == null || message.isBlank()) {
                return;
              }
              try {
                bot.chat(message);
              } catch (Exception ignored) {
              }
            },
            () -> chatTasks.remove(uuid),
            200L,
            400L);

    chatTasks.put(uuid, task);
  }

  private Location resolveSpawnLocation(Player requester) {
    if (requester != null) {
      return requester.getLocation().clone();
    }

    World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    return world != null ? world.getSpawnLocation().clone() : null;
  }

  private String createBotName(String customBaseName, int index, int amount) {
    if (customBaseName == null || customBaseName.isBlank()) {
      return generateName();
    }

    String sanitized = sanitizeName(customBaseName);
    if (sanitized == null) {
      return null;
    }

    if (amount <= 1) {
      return nextAvailableName(sanitized);
    }

    String suffix = String.valueOf(index + 1);
    int maxBaseLength = Math.max(1, 16 - suffix.length());
    String trimmed = sanitized.length() > maxBaseLength ? sanitized.substring(0, maxBaseLength) : sanitized;
    String candidate = trimmed + suffix;
    return nextAvailableName(candidate);
  }

  private String nextAvailableName(String base) {
    String candidate = base;
    int counter = 1;
    while (isNameTaken(candidate)) {
      String suffix = String.valueOf(counter++);
      int maxBaseLength = Math.max(1, 16 - suffix.length());
      String trimmed = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
      candidate = trimmed + suffix;
      if (counter > 9999) {
        return null;
      }
    }
    return candidate;
  }

  private String generateName() {
    List<String> poolSnapshot;
    synchronized (cleanNamePool) {
      poolSnapshot = new ArrayList<>(cleanNamePool);
    }

    if (poolSnapshot.isEmpty()) {
      return fallbackName();
    }

    String chosen = null;
    int count = 0;
    for (String candidate : poolSnapshot) {
      if (candidate == null || candidate.isEmpty() || candidate.length() > 16) {
        continue;
      }
      if (!candidate.matches("[a-zA-Z0-9_]+")) {
        continue;
      }
      if (isNameTaken(candidate)) {
        continue;
      }
      count++;
      if (ThreadLocalRandom.current().nextInt(count) == 0) {
        chosen = candidate;
      }
    }

    if (chosen != null) {
      usedNames.add(chosen.toLowerCase());
      return chosen;
    }

    return fallbackName();
  }

  private boolean isNameTaken(String name) {
    if (name == null || name.isBlank()) {
      return true;
    }
    if (usedNames.contains(name.toLowerCase())) {
      return true;
    }
    if (Bukkit.getPlayerExact(name) != null) {
      return true;
    }
    return Bukkit.getOnlinePlayers().stream().anyMatch(player -> player.getName().equalsIgnoreCase(name));
  }

  private static String sanitizeName(String input) {
    String cleaned = input.replaceAll("[^A-Za-z0-9_]", "_");
    if (cleaned.isBlank()) {
      return null;
    }
    if (cleaned.length() > 16) {
      cleaned = cleaned.substring(0, 16);
    }
    return cleaned;
  }

  private static String trimToMinecraftName(String name) {
    return name.length() > 16 ? name.substring(0, 16) : name;
  }

  private String fallbackName() {
    List<String> poolSnapshot;
    synchronized (namePool) {
      poolSnapshot = new ArrayList<>(namePool);
    }

    if (poolSnapshot.isEmpty()) {
      poolSnapshot = List.of(
          "Nova", "Atlas", "Echo", "Pixel", "Rex", "Milo", "Juno", "Orion", "Aero", "Zephyr",
          "Luna", "Vega", "Niko", "Iris", "Sage", "Kite", "Mira", "Zane", "Rumi", "Aria");
    }

    for (int attempt = 0; attempt < 5000; attempt++) {
      String base = poolSnapshot.get(ThreadLocalRandom.current().nextInt(poolSnapshot.size()));
      String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
      int maxBaseLength = Math.max(1, 16 - suffix.length());
      String candidate = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
      candidate = trimToMinecraftName(candidate + suffix);
      if (!isNameTaken(candidate)) {
        usedNames.add(candidate.toLowerCase());
        return candidate;
      }
    }

    for (int index = 1; index < 10000; index++) {
      String candidate = trimToMinecraftName("bot" + index);
      if (!isNameTaken(candidate)) {
        usedNames.add(candidate.toLowerCase());
        return candidate;
      }
    }

    return null;
  }

  private record BotPlan(UUID uuid, String name) {}

  private void reloadPools() {
    loadNamePool();
    loadMessagePool();
  }

  private void loadNamePool() {
    synchronized (namePool) {
      namePool.clear();
    }

    File file = new File(plugin.getDataFolder(), NAME_POOL_FILE);
    if (!file.exists()) {
      plugin.saveResource(NAME_POOL_FILE, false);
    }

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    List<String> names = yaml.getStringList("name");
    if (names.isEmpty()) {
      names = List.of("Rush_Build", "Glitch6746", "TurboKiller", "Shadow", "One", "Rage");
    }

    synchronized (namePool) {
      for (String name : names) {
        String cleaned = sanitizeName(name);
        if (cleaned != null && !cleaned.isBlank()) {
          namePool.add(cleaned);
        }
      }
    }

    synchronized (cleanNamePool) {
      cleanNamePool.clear();
      for (String name : namePool) {
        if (name == null || name.isBlank() || name.length() > 16) {
          continue;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
          continue;
        }
        cleanNamePool.add(name);
      }
    }
  }

  private void loadMessagePool() {
    synchronized (chatMessagePool) {
      chatMessagePool.clear();
    }

    File file = new File(plugin.getDataFolder(), MESSAGE_POOL_FILE);
    if (!file.exists()) {
      plugin.saveResource(MESSAGE_POOL_FILE, false);
    }

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    List<String> messages = yaml.getStringList("messages");
    if (messages.isEmpty()) {
      messages = List.of("hey", "gg", "nice", "hello", "anyone here?", "let's go");
    }

    synchronized (chatMessagePool) {
      for (String message : messages) {
        if (message != null && !message.isBlank()) {
          chatMessagePool.add(message);
        }
      }
    }
  }

  private String randomChatMessage(Player bot) {
    List<String> poolSnapshot;
    synchronized (chatMessagePool) {
      poolSnapshot = new ArrayList<>(chatMessagePool);
    }

    if (poolSnapshot.isEmpty()) {
      return null;
    }

    String message = poolSnapshot.get(ThreadLocalRandom.current().nextInt(poolSnapshot.size()));
    String randomPlayer = pickRandomOnlinePlayerName(bot);
    return message.replace("{name}", bot.getName()).replace("{random_player}", randomPlayer);
  }

  private String pickRandomOnlinePlayerName(Player bot) {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    players.removeIf(player -> player == null || player.getUniqueId().equals(bot.getUniqueId()));
    if (players.isEmpty()) {
      return bot.getName();
    }
    Player selected = players.get(ThreadLocalRandom.current().nextInt(players.size()));
    return selected.getName();
  }
}
