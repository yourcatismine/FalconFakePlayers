package com.falconfk;

public final class FalconFKPluginHolder {

  private static FalconFKPlugin plugin;

  private FalconFKPluginHolder() {}

  public static void setPlugin(FalconFKPlugin pluginInstance) {
    plugin = pluginInstance;
  }

  public static FalconFKPlugin getPlugin() {
    return plugin;
  }
}
