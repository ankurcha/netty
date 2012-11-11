package io.netty.channel.socket.http;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;

import java.net.SocketAddress;

public class HttpTunnelClientChannel extends AbstractChannel {
	
	protected HttpTunnelClientChannel(Channel parent, Integer id, ChannelConfig config) {
		super(parent, id);
		this.config = config;  
	}

	private ChannelConfig config;

	@Override
	public ChannelConfig config() {
		return this.config;
	}

	@Override
	public boolean isOpen() {
		return false;
	}

	@Override
	public boolean isActive() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ChannelMetadata metadata() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Unsafe newUnsafe() {
		return new HttpTunnelClientUnsafe();
	}
	
	class HttpTunnelClientUnsafe extends AbstractUnsafe {
        
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, 
                            ChannelFuture future) {
        }

        @Override
		public void suspendRead() {			
		}

		@Override
		public void resumeRead() {
		}
		
	}

	@Override
	protected boolean isCompatible(EventLoop loop) {
		return loop instanceof SingleThreadEventLoop;
	}

	@Override
	protected SocketAddress localAddress0() {
		return null;
	}

	@Override
	protected SocketAddress remoteAddress0() {
		return null;
	}

	@Override
	protected Runnable doRegister() throws Exception {
		return null;
	}

	@Override
	protected void doBind(SocketAddress localAddress) throws Exception {
		
	}

	@Override
	protected void doDisconnect() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void doClose() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void doDeregister() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected boolean isFlushPending() {
		// TODO Auto-generated method stub
		return false;
	}
	
	
	
}
