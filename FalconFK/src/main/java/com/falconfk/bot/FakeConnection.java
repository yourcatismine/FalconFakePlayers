package com.falconfk.bot;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelDuplexHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FakeConnection extends Connection {

  public FakeConnection(InetAddress address) {
    super(PacketFlow.SERVERBOUND);
    this.channel = new EmbeddedChannel();
    this.address = new InetSocketAddress(address, 25565);
    Connection.configureSerialization(this.channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
    if (this.channel.pipeline().get("packet_handler") == null) {
      this.channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
    }
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public void send(@NotNull Packet<?> packet) {}

  @Override
  public void send(@NotNull Packet<?> packet, @Nullable io.netty.channel.ChannelFutureListener listener) {}

  @Override
  public void send(@NotNull Packet<?> packet, @Nullable io.netty.channel.ChannelFutureListener listener, boolean flush) {}

  public void send(@NotNull Packet<?> packet, @Nullable PacketSendListener listener) {}

  public void send(@NotNull Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {}
}
