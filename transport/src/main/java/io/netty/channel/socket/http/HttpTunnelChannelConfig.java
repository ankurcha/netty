package io.netty.channel.socket.http;

import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;

/**
 * Configuration for HTTP tunnels. Where possible, properties set on this configuration will
 * be applied to the two channels that service sending and receiving data on this end of the
 * tunnel.
 * <p>
 * HTTP tunnel clients have the following additional options:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr>
 * <tr><td>{@code "writeBufferHighWaterMark"}</td><td>{@link #setWriteBufferHighWaterMark(int)}</td></tr>
 * <tr><td>{@code "writeBufferLowWaterMark"}</td><td>{@link #setWriteBufferLowWaterMark(int)}</td></tr>
 * </table>
 */
public abstract class HttpTunnelChannelConfig 
		extends DefaultChannelConfig implements SocketChannelConfig {	

    protected volatile ChannelOption<Integer> writeBufferLowWaterMark = new ChannelOption<Integer>("writeBufferLowWaterMark");

    protected volatile ChannelOption<Integer> writeBufferHighWaterMark = new ChannelOption<Integer>("writeBufferHighWaterMark");
	

}
