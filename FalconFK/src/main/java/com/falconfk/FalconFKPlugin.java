package com.falconfk;

import com.falconfk.bot.FalconBotManager;
import com.falconfk.command.FalconCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FalconFKPlugin extends JavaPlugin {

  private FalconBotManager botManager;
  private boolean luckPermsAvailable;

  public boolean isLuckPermsAvailable() {
    return luckPermsAvailable;
  }

  @Override
  public void onEnable() {
    FalconFKPluginHolder.setPlugin(this);
    this.luckPermsAvailable = getServer().getPluginManager().getPlugin("LuckPerms") != null;
    this.botManager = new FalconBotManager(this);

    PluginCommand falconCommand = getCommand("falcon");
    if (falconCommand != null) {
      FalconCommand executor = new FalconCommand(this, botManager);
      falconCommand.setExecutor(executor);
      falconCommand.setTabCompleter(executor);
    }

    getLogger().info("FalconFK enabled");
  }

  @Override
  public void onDisable() {
    if (botManager != null) {
      botManager.despawnAll();
    }
    FalconFKPluginHolder.setPlugin(null);
  }
}
