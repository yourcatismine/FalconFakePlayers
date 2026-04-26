package com.falconfk.bot;

import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class NmsBotSpawner {

  private static volatile boolean initialized;
  private static volatile boolean failed;

  private static Method craftServerGetServerMethod;
  private static Method craftWorldGetHandleMethod;
  private static Method craftPlayerGetHandleMethod;

  private static Class<?> minecraftServerClass;
  private static Class<?> serverLevelClass;
  private static Class<?> serverPlayerClass;
  private static Class<?> clientInformationClass;
  private static Class<?> connectionClass;
  private static Class<?> commonListenerCookieClass;
  private static Class<?> serverGamePacketListenerClass;

  private static Constructor<?> gameProfileConstructor;
  private static Method setPosMethod;
  private static Method getPlayerListMethod;

  private static Field connectionFieldInPlayer;
  private static Field xoField;
  private static Field yoField;
  private static Field zoField;

  private static Object clientInfoDefault;

  private NmsBotSpawner() {}

  public static synchronized void init() {
    if (initialized || failed) {
      return;
    }

    try {
      String version = Bukkit.getServer().getClass().getPackage().getName();
      version = version.substring(version.lastIndexOf('.') + 1);
      String cbPkg = version.equals("craftbukkit") ? "org.bukkit.craftbukkit" : "org.bukkit.craftbukkit." + version;

      Class<?> craftServerClass = Class.forName(cbPkg + ".CraftServer");
      Class<?> craftWorldClass = Class.forName(cbPkg + ".CraftWorld");
      Class<?> craftPlayerClass = Class.forName(cbPkg + ".entity.CraftPlayer");
      ClassLoader nmsLoader = craftServerClass.getClassLoader();

      craftServerGetServerMethod = craftServerClass.getMethod("getServer");
      craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");
      craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");

      minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
      try {
        serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
        serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.ServerPlayer");
      } catch (ClassNotFoundException ex) {
        serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
        serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");
      }

      try {
        connectionClass = nmsLoader.loadClass("net.minecraft.network.Connection");
      } catch (ClassNotFoundException ex) {
        connectionClass = nmsLoader.loadClass("net.minecraft.network.NetworkManager");
      }

      try {
        commonListenerCookieClass =
            nmsLoader.loadClass("net.minecraft.server.network.CommonListenerCookie");
      } catch (ClassNotFoundException ignored) {
      }

      try {
        serverGamePacketListenerClass =
            nmsLoader.loadClass("net.minecraft.server.network.ServerGamePacketListenerImpl");
      } catch (ClassNotFoundException ex) {
        try {
          serverGamePacketListenerClass =
              nmsLoader.loadClass("net.minecraft.server.network.PlayerConnection");
        } catch (ClassNotFoundException ignored) {
        }
      }

      try {
        clientInformationClass = nmsLoader.loadClass("net.minecraft.server.level.ClientInformation");
        clientInfoDefault = clientInformationClass.getMethod("createDefault").invoke(null);
      } catch (ClassNotFoundException ignored) {
      }

      Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
      gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

      getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");

      for (Method method : serverPlayerClass.getMethods()) {
        if (method.getName().equals("setPos") && method.getParameterCount() == 3) {
          Class<?>[] params = method.getParameterTypes();
          if (params[0] == double.class && params[1] == double.class && params[2] == double.class) {
            setPosMethod = method;
            break;
          }
        }
      }
      if (setPosMethod == null) {
        setPosMethod = findMethodBySignature(serverPlayerClass, 3, double.class, double.class, double.class);
      }

      Class<?> entityClass;
      try {
        entityClass = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
      } catch (ClassNotFoundException ex) {
        entityClass = serverPlayerClass;
      }
      xoField = findFieldByName(entityClass, "xo");
      yoField = findFieldByName(entityClass, "yo");
      zoField = findFieldByName(entityClass, "zo");

      connectionFieldInPlayer = findFieldByName(serverPlayerClass, "connection");
      if (connectionFieldInPlayer == null) {
        connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerConnection");
      }
      if (connectionFieldInPlayer == null) {
        connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerGameConnection");
      }
      if (connectionFieldInPlayer == null) {
        for (Field field : getAllDeclaredFields(serverPlayerClass)) {
          if (serverGamePacketListenerClass != null
              && (serverGamePacketListenerClass.isAssignableFrom(field.getType())
                  || field.getType().isAssignableFrom(serverGamePacketListenerClass))) {
            field.setAccessible(true);
            connectionFieldInPlayer = field;
            break;
          }
        }
      }

      initialized = true;
    } catch (Exception e) {
      failed = true;
      throw new IllegalStateException("Failed to initialise FalconFK NMS bridge", e);
    }
  }

  public static Player spawnBot(UUID uuid, String name, Location location) {
    if (location == null || location.getWorld() == null) {
      return null;
    }

    try {
      if (!initialized && !failed) {
        init();
      }
      if (!initialized) {
        return null;
      }

      Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
      Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
      Object serverLevel = craftWorldGetHandleMethod.invoke(location.getWorld());
      Object clientInfo = getClientInformation();

      Object serverPlayer = createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);
      if (serverPlayer == null) {
        return null;
      }

      if (setPosMethod != null) {
        setPosMethod.invoke(serverPlayer, location.getX(), location.getY(), location.getZ());
      }
      initPreviousPosition(serverPlayer, location.getX(), location.getY(), location.getZ());

      Object connection = createFakeConnection();
      Object cookie = createCookieDynamic(gameProfile, clientInfo);
      if (connection == null || cookie == null) {
        return null;
      }

      placePlayer(minecraftServer, connection, serverPlayer, cookie);
      injectFakeListener(minecraftServer, connection, serverPlayer, gameProfile, clientInfo);

      Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
      Object entity = getBukkitEntity.invoke(serverPlayer);
      if (entity instanceof Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        return player;
      }
      return null;
    } catch (Exception e) {
      Bukkit.getLogger().warning("[FalconFK] Failed to spawn fake bot '" + name + "': " + e.getMessage());
      return null;
    }
  }

  public static void removeBot(Player bot) {
    if (bot == null) {
      return;
    }
    try {
      bot.kick(net.kyori.adventure.text.Component.empty());
    } catch (Exception e) {
      Bukkit.getLogger().warning("[FalconFK] Failed to remove fake bot '" + bot.getName() + "': " + e.getMessage());
    }
  }

  private static Object createFakeConnection() {
    try {
      return new FakeConnection(InetAddress.getLoopbackAddress());
    } catch (Exception e) {
      return null;
    }
  }

  private static Object getClientInformation() {
    if (clientInfoDefault != null) {
      return clientInfoDefault;
    }
    if (clientInformationClass == null) {
      return null;
    }
    try {
      return clientInformationClass.getMethod("createDefault").invoke(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static Object createServerPlayer(
      Object minecraftServer, Object serverLevel, Object gameProfile, Object clientInfo) {
    if (clientInfo != null && clientInformationClass != null) {
      try {
        Constructor<?> ctor =
            serverPlayerClass.getConstructor(
                minecraftServerClass, serverLevelClass, gameProfile.getClass(), clientInformationClass);
        return ctor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
      } catch (NoSuchMethodException ignored) {
      } catch (Exception ignored) {
      }
    }

    try {
      Constructor<?> ctor =
          serverPlayerClass.getConstructor(minecraftServerClass, serverLevelClass, gameProfile.getClass());
      return ctor.newInstance(minecraftServer, serverLevel, gameProfile);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean placePlayer(Object minecraftServer, Object connection, Object serverPlayer, Object cookie) {
    try {
      Object playerList = getPlayerListMethod.invoke(minecraftServer);
      Method placeMethod = findMethod(playerList.getClass(), "placeNewPlayer", 3);
      if (placeMethod == null) {
        return false;
      }
      placeMethod.invoke(playerList, connection, serverPlayer, cookie);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static Object createCookieDynamic(Object gameProfile, Object clientInfo) {
    if (commonListenerCookieClass == null) {
      return null;
    }

    try {
      Method factory = commonListenerCookieClass.getMethod("createInitial", gameProfile.getClass(), boolean.class);
      return factory.invoke(null, gameProfile, false);
    } catch (Exception ignored) {
    }

    for (Constructor<?> constructor : commonListenerCookieClass.getDeclaredConstructors()) {
      constructor.setAccessible(true);
      Class<?>[] params = constructor.getParameterTypes();
      if (params.length > 0 && params[params.length - 1].getSimpleName().contains("DefaultConstructorMarker")) {
        continue;
      }
      try {
        return switch (params.length) {
          case 1 -> constructor.newInstance(gameProfile);
          case 2 -> constructor.newInstance(gameProfile, 0);
          case 3 -> constructor.newInstance(gameProfile, 0, clientInfo);
          case 4 -> constructor.newInstance(gameProfile, 0, clientInfo, false);
          case 5 -> constructor.newInstance(gameProfile, 0, clientInfo, false, false);
          default -> null;
        };
      } catch (Exception ignored) {
      }
    }

    return null;
  }

  private static void injectFakeListener(
      Object minecraftServer, Object connection, Object serverPlayer, Object gameProfile, Object clientInfo) {
    if (connectionFieldInPlayer == null || serverGamePacketListenerClass == null) {
      return;
    }
    try {
      FakeServerGamePacketListenerImpl fakeListener =
          FakeServerGamePacketListenerImpl.create(minecraftServer, connection, serverPlayer, createCookieDynamic(gameProfile, clientInfo));
      connectionFieldInPlayer.set(serverPlayer, fakeListener);
      if (connection != null) {
        for (Field field : getAllDeclaredFields(connection.getClass())) {
          if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            continue;
          }
          try {
            field.setAccessible(true);
            Object value = field.get(connection);
            if (value != null && serverGamePacketListenerClass.isInstance(value)) {
              field.set(connection, fakeListener);
              break;
            }
          } catch (Exception ignored) {
          }
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static void initPreviousPosition(Object serverPlayer, double x, double y, double z) {
    try {
      if (xoField != null) {
        xoField.setDouble(serverPlayer, x);
      }
      if (yoField != null) {
        yoField.setDouble(serverPlayer, y);
      }
      if (zoField != null) {
        zoField.setDouble(serverPlayer, z);
      }
    } catch (Exception ignored) {
    }
  }

  private static Method findMethod(Class<?> clazz, String name, int paramCount) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          method.setAccessible(true);
          return method;
        }
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static Method findMethodBySignature(Class<?> clazz, int paramCount, Class<?>... paramTypes) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.getParameterCount() == paramCount
            && Arrays.equals(method.getParameterTypes(), paramTypes)) {
          method.setAccessible(true);
          return method;
        }
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static Field findFieldByName(Class<?> clazz, String name) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        if (field.getName().equals(name)) {
          field.setAccessible(true);
          return field;
        }
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static List<Field> getAllDeclaredFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      Collections.addAll(fields, current.getDeclaredFields());
      current = current.getSuperclass();
    }
    return fields;
  }
}
