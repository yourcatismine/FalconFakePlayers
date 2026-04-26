package com.falconfk.command;

import com.falconfk.FalconFKPlugin;
import com.falconfk.bot.FalconBotManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class FalconCommand implements CommandExecutor, TabCompleter {

  private final FalconFKPlugin plugin;
  private final FalconBotManager botManager;

  public FalconCommand(FalconFKPlugin plugin, FalconBotManager botManager) {
    this.plugin = plugin;
    this.botManager = botManager;
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {

    if (args.length == 0) {
      sender.sendMessage("Usage: /falcon summon [amount] [name] | /falcon despawn [all|botname]");
      return true;
    }

    if (args[0].equalsIgnoreCase("summon")) {
      return handleSummon(sender, args);
    }

    if (args[0].equalsIgnoreCase("chat")) {
      return handleChat(sender, args);
    }

    if (args[0].equalsIgnoreCase("despawn")) {
      return handleDespawn(sender, args);
    }

    sender.sendMessage("Unknown subcommand. Use /falcon summon [amount] [name] | /falcon chat [all|botname] [on|off|toggle] | /falcon despawn [all|botname]");
    return true;
  }

  private boolean handleSummon(CommandSender sender, String[] args) {
    if (!sender.hasPermission("falconfk.summon")) {
      sender.sendMessage("You do not have permission to use this command.");
      return true;
    }

    if (args.length > 3) {
      sender.sendMessage("Usage: /falcon summon [amount] [name]");
      return true;
    }

    int amount = 1;
    String customName = null;

    if (args.length >= 2) {
      if (isInteger(args[1])) {
        amount = Math.max(1, Integer.parseInt(args[1]));
        if (args.length == 3) {
          customName = args[2];
        }
      } else {
        customName = args[1];
        if (args.length == 3) {
          sender.sendMessage("Usage: /falcon summon [amount] [name]");
          return true;
        }
      }
    }

    Player player = sender instanceof Player p ? p : null;
    int spawned = botManager.summonBots(player, amount, customName);
    if (spawned <= 0) {
      sender.sendMessage("No bots were spawned.");
      return true;
    }

    if (player == null) {
      sender.sendMessage("Queued " + spawned + " fake bot(s) for gradual spawn.");
    } else {
      sender.sendMessage("Queued " + spawned + " fake bot(s) for gradual spawn.");
    }
    return true;
  }

  private boolean handleChat(CommandSender sender, String[] args) {
    if (!sender.hasPermission("falconfk.chat")) {
      sender.sendMessage("You do not have permission to use this command.");
      return true;
    }

    if (args.length == 1 || args.length > 3) {
      sender.sendMessage("Usage: /falcon chat [all|botname] [on|off|toggle]");
      return true;
    }

    String target = args[1];
    String mode = args.length == 3 ? args[2].toLowerCase() : "toggle";

    if (target.equalsIgnoreCase("all")) {
      switch (mode) {
        case "on" -> {
          int updated = botManager.setChatEnabledAll(true);
          sender.sendMessage("Chat enabled for " + updated + " fake bot(s).");
          return true;
        }
        case "off" -> {
          int updated = botManager.setChatEnabledAll(false);
          sender.sendMessage("Chat disabled for " + updated + " fake bot(s).");
          return true;
        }
        case "toggle" -> {
          int updated = botManager.toggleChatAll();
          sender.sendMessage("Chat toggled for " + updated + " fake bot(s).");
          return true;
        }
        default -> {
          sender.sendMessage("Usage: /falcon chat [all|botname] [on|off|toggle]");
          return true;
        }
      }
    }

    if (!botManager.hasBot(target)) {
      sender.sendMessage("No fake bot named '" + target + "' was found.");
      return true;
    }

    boolean enabled;
    switch (mode) {
      case "on" -> {
        botManager.setChatEnabled(target, true);
        enabled = true;
      }
      case "off" -> {
        botManager.setChatEnabled(target, false);
        enabled = false;
      }
      case "toggle" -> enabled = botManager.toggleChat(target);
      default -> {
        sender.sendMessage("Usage: /falcon chat [all|botname] [on|off|toggle]");
        return true;
      }
    }

    sender.sendMessage("Chat " + (enabled ? "enabled" : "disabled") + " for fake bot '" + target + "'.");
    return true;
  }

  private boolean handleDespawn(CommandSender sender, String[] args) {
    if (!sender.hasPermission("falconfk.despawn")) {
      sender.sendMessage("You do not have permission to use this command.");
      return true;
    }

    if (args.length == 1) {
      sender.sendMessage("Usage: /falcon despawn [all|botname]");
      return true;
    }

    if (args.length > 2) {
      sender.sendMessage("Usage: /falcon despawn [all|botname]");
      return true;
    }

    if (args[1].equalsIgnoreCase("all")) {
      botManager.despawnAll();
      sender.sendMessage("Despawning all fake bots.");
      return true;
    }

    boolean removed = botManager.despawnBot(args[1]);
    if (!removed) {
      sender.sendMessage("No fake bot named '" + args[1] + "' was found.");
      return true;
    }

    sender.sendMessage("Despawning fake bot '" + args[1] + "'.");
    return true;
  }

  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {

    if (args.length == 1) {
      List<String> options = new ArrayList<>();
      if ("summon".startsWith(args[0].toLowerCase())) {
        options.add("summon");
      }
      if ("chat".startsWith(args[0].toLowerCase())) {
        options.add("chat");
      }
      if ("despawn".startsWith(args[0].toLowerCase())) {
        options.add("despawn");
      }
      return options;
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("chat")) {
      List<String> options = new ArrayList<>();
      if ("all".startsWith(args[1].toLowerCase())) {
        options.add("all");
      }
      options.addAll(botManager.getActiveBotNames().stream()
          .filter(name -> name.startsWith(args[1].toLowerCase()))
          .toList());
      return options;
    }

    if (args.length == 3 && args[0].equalsIgnoreCase("chat")) {
      List<String> options = new ArrayList<>();
      for (String mode : List.of("on", "off", "toggle")) {
        if (mode.startsWith(args[2].toLowerCase())) {
          options.add(mode);
        }
      }
      return options;
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("despawn")) {
      List<String> options = new ArrayList<>();
      if ("all".startsWith(args[1].toLowerCase())) {
        options.add("all");
      }
      options.addAll(botManager.getActiveBotNames().stream()
          .filter(name -> name.startsWith(args[1].toLowerCase()))
          .toList());
      return options;
    }

    return List.of();
  }

  private static boolean isInteger(String input) {
    try {
      Integer.parseInt(input);
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}
