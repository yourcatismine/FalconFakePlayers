package com.falconfk.bot;

import java.lang.reflect.Method;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

public final class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

  private static volatile boolean entityIdDirectFailed;
  private static volatile Method cachedEntityIdMethod;

  public FakeServerGamePacketListenerImpl(
      MinecraftServer server,
      Connection connection,
      ServerPlayer player,
      CommonListenerCookie cookie) {
    super(server, connection, player, cookie);
  }

  public static FakeServerGamePacketListenerImpl create(
      Object server, Object connection, Object player, Object cookie) {
    return new FakeServerGamePacketListenerImpl(
        (MinecraftServer) server,
        (Connection) connection,
        (ServerPlayer) player,
        (CommonListenerCookie) cookie);
  }

  @Override
  public void onDisconnect(@org.jetbrains.annotations.NotNull DisconnectionDetails details) {
    try {
      super.onDisconnect(details);
    } catch (IllegalStateException e) {
      if (!"Already retired".equals(e.getMessage())) {
        throw e;
      }
    }
  }

  @Override
  public void send(Packet<?> packet) {
    if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
      applyKnockback(motionPacket);
    }
  }

  @SuppressWarnings("unused")
  public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
    if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
      applyKnockback(motionPacket);
    }
  }

  private void applyKnockback(ClientboundSetEntityMotionPacket packet) {
    int packetEntityId = resolveEntityId(packet);
    if (packetEntityId != -1 && packetEntityId != this.player.getId()) {
      return;
    }

    com.falconfk.FalconFKPlugin plugin = com.falconfk.FalconFKPluginHolder.getPlugin();
    if (plugin == null) {
      return;
    }

    org.bukkit.entity.Entity bukkitEntity = this.player.getBukkitEntity();
    bukkitEntity.getScheduler().run(
        plugin,
        task -> {
          try {
            Method getMovement = findMethod(packet.getClass(), "getMovement");
            if (getMovement != null) {
              Object movement = getMovement.invoke(packet);
              Method lerpMotion = findMethod(this.player.getClass(), "lerpMotion", movement.getClass());
              if (lerpMotion != null) {
                lerpMotion.invoke(this.player, movement);
                return;
              }
            }

            double[] xyz = readVector(packet);
            if (xyz != null) {
              Method lerpMotion = findMethod(this.player.getClass(), "lerpMotion", double.class, double.class, double.class);
              if (lerpMotion != null) {
                lerpMotion.invoke(this.player, xyz[0], xyz[1], xyz[2]);
              }
            }
          } catch (Exception ignored) {
          }
        },
        () -> {});
  }

  private static int resolveEntityId(ClientboundSetEntityMotionPacket packet) {
    if (!entityIdDirectFailed) {
      try {
        return packet.getId();
      } catch (NoSuchMethodError e) {
        entityIdDirectFailed = true;
      }
    }

    if (cachedEntityIdMethod == null) {
      for (String name : new String[] {"getEntityId", "id"}) {
        try {
          Method method = ClientboundSetEntityMotionPacket.class.getMethod(name);
          if (method.getReturnType() == int.class) {
            method.setAccessible(true);
            cachedEntityIdMethod = method;
            break;
          }
        } catch (NoSuchMethodException ignored) {
        }
      }
    }

    if (cachedEntityIdMethod == null) {
      return -1;
    }

    try {
      return (int) cachedEntityIdMethod.invoke(packet);
    } catch (Exception e) {
      return -1;
    }
  }

  private static double[] readVector(ClientboundSetEntityMotionPacket packet) {
    try {
      for (String name : new String[] {"getXa", "getX"}) {
        Method method = findMethod(packet.getClass(), name);
        if (method != null) {
          double x = ((Number) method.invoke(packet)).doubleValue();
          Method getYa = findMethod(packet.getClass(), "getYa");
          Method getZa = findMethod(packet.getClass(), "getZa");
          if (getYa != null && getZa != null) {
            return new double[] {
              x,
              ((Number) getYa.invoke(packet)).doubleValue(),
              ((Number) getZa.invoke(packet)).doubleValue()
            };
          }
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static Method findMethod(Class<?> type, String name, Class<?>... params) {
    try {
      Method method = type.getMethod(name, params);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException ex) {
      return null;
    }
  }
}
