package io.netty.channel.socket.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelFuture;

public class Channels {

    /**
     * Creates a new non-cancellable {@link io.netty.channel.ChannelFuture} for the specified
     * {@link io.netty.channel.Channel}.
     * @param channel Channel associated to the future
     * @return The ChannelFuture
     */
    public static ChannelFuture future(Channel channel) {
        return future(channel, false);
    }

    /**
     * Creates a new {@link ChannelFuture} for the specified {@link Channel}.
     *
     * @param channel Channel associated to the future
     * @param cancellable {@code true} if and only if the returned future
     *                    can be canceled by {@link ChannelFuture#cancel()}
     * @return The ChannelFuture
     */
    public static ChannelFuture future(Channel channel, boolean cancellable) {
        return new DefaultChannelFuture(channel, cancellable);
    }

}
