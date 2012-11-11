package io.netty.channel.socket.rxtx;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.TooManyListenersException;

public class RxtxChannel extends AbstractChannel{

    protected RxtxChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    @Override
    protected Unsafe newUnsafe() {
        return new RxtxUnsafe();
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
        return pipeline().channel().remoteAddress();
    }

    @Override
    protected Runnable doRegister() throws Exception {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {        
        eventLoop().execute(new DisconnectRunnable(unsafe().voidFuture(), (RxtxUnsafe) unsafe()));
    }

    @Override
    protected void doDeregister() throws Exception {
        // TODO
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    /**
     * Returns the configuration of this channel.
     */
    @Override
    public ChannelConfig config() {
        return pipeline().channel().config();
    }

    @Override
    public boolean isOpen() {        
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    /**
     * Return the {@link io.netty.channel.ChannelMetadata} of the {@link io.netty.channel.Channel} which describe the nature of the {@link io.netty.channel.Channel}.
     */
    @Override
    public ChannelMetadata metadata() {
        return null;
    }


    private class WriteRunnable implements Runnable {

        private final DefaultChannelFuture future;

        private final RxtxUnsafe channelSink;

        private final ByteBuf message;

        public WriteRunnable(final DefaultChannelFuture future,
                             final RxtxUnsafe channelSink,
                             final ByteBuf message) {
            this.future = future;
            this.channelSink = channelSink;
            this.message = message;
        }

        @Override
        public void run() {
            try {
                channelSink.outputStream.write(message.array(), message.readerIndex(), message.readableBytes());
                channelSink.outputStream.flush();
                future.setSuccess();

            } catch (Exception e) {
                future.setFailure(e);
            }
        }
    }

    private class ConnectRunnable implements Runnable {

        private final DefaultChannelFuture channelFuture;

        private final RxtxUnsafe channelSink;

        ConnectRunnable(final DefaultChannelFuture channelFuture, final RxtxUnsafe channelSink) {
            this.channelFuture = channelFuture;
            this.channelSink = channelSink;
        }

        @Override
        public void run() {

            if (channelSink.closed) {
                channelFuture.setFailure(new Exception("Channel is already closed."));
            } else {
                try {
                    connectInternal();
                    channelFuture.setSuccess();
                } catch (Exception e) {
                    channelFuture.setFailure(e);
                }
            }

        }

        private void connectInternal() throws NoSuchPortException, PortInUseException,
                UnsupportedCommOperationException,  IOException, TooManyListenersException {

            final CommPort commPort;
            try {

                final CommPortIdentifier cpi =
                        CommPortIdentifier.getPortIdentifier(channelSink.remoteAddress.getDeviceAddress());
                commPort = cpi.open(this.getClass().getName(), 1000);

            } catch (NoSuchPortException e) {
                throw e;
            } catch (PortInUseException e) {
                throw e;
            }

            channelSink.serialPort = (SerialPort) commPort;
            channelSink.serialPort.addEventListener(new RXTXSerialPortEventListener(channelSink));
            channelSink.serialPort.notifyOnDataAvailable(true);
            channelSink.serialPort.setSerialPortParams(
                    channelSink.config.getBaudrate(),
                    channelSink.config.getDatabits().getValue(),
                    channelSink.config.getStopbits().getValue(),
                    channelSink.config.getParitybit().getValue()
            );

            channelSink.serialPort.setDTR(channelSink.config.isDtr());
            channelSink.serialPort.setRTS(channelSink.config.isRts());

            channelSink.outputStream = new BufferedOutputStream(channelSink.serialPort.getOutputStream());
            channelSink.inputStream = new BufferedInputStream(channelSink.serialPort.getInputStream());
        }
    }

    private class DisconnectRunnable implements Runnable {

        private final ChannelFuture channelFuture;

        private final RxtxUnsafe channelSink;

        public DisconnectRunnable(final ChannelFuture channelFuture, final RxtxUnsafe channelSink) {
            this.channelFuture = channelFuture;
            this.channelSink = channelSink;
        }

        @Override
        public void run() {
            if (channelSink.closed) {
                channelFuture.setFailure(new Exception("Channel is already closed."));
            } else {
                try {
                    disconnectInternal();
                    channelSink.channel.doClose();
                } catch (Exception e) {
                    channelFuture.setFailure(e);
                }
            }
        }

        private void disconnectInternal() throws Exception {

            Exception exception = null;

            try {
                if (channelSink.inputStream != null) {
                    channelSink.inputStream.close();
                }
            } catch (IOException e) {
                exception = e;
            }

            try {
                if (channelSink.outputStream != null) {
                    channelSink.outputStream.close();
                }
            } catch (IOException e) {
                exception = e;
            }

            if (channelSink.serialPort != null) {
                channelSink.serialPort.removeEventListener();
                channelSink.serialPort.close();
            }

            channelSink.inputStream = null;
            channelSink.outputStream = null;
            channelSink.serialPort = null;

            if (exception != null) {
                throw exception;
            }
        }
    }


    class RxtxUnsafe extends AbstractUnsafe {

        RxtxDeviceAddress remoteAddress;

        BufferedOutputStream outputStream;
        BufferedInputStream inputStream;

        SerialPort serialPort;
        volatile boolean closed;

        private final RxtxChannelConfig config;
        RxtxChannel channel;

        public RxtxUnsafe() {
            config = new RxtxChannelConfig();
        }

        /**
         * <p>
         * Connect the {@link io.netty.channel.Channel} of the given {@link io.netty.channel.ChannelFuture} with the 
         * given remote {@link java.net.SocketAddress}.
         * If a specific local {@link java.net.SocketAddress} should be used it need to be given as argument. Otherwise 
         * just pass <code>null</code> to it.
         * <p/>
         * The {@link io.netty.channel.ChannelFuture} will get notified once the connect operation was complete.
         */
        @Override
        public void connect(final SocketAddress remoteAddress,
                            SocketAddress localAddress, final ChannelFuture future) {



            if(eventLoop().inEventLoop()) {

                if(!ensureOpen(future))
                    return;

                if(remoteAddress!=null) {
                    this.remoteAddress = (RxtxDeviceAddress) remoteAddress;
                    eventLoop().execute(new ConnectRunnable((DefaultChannelFuture) future, this));
                }

            }
        }

        /**
         * Suspend reads from the underlying transport, which basicly has the effect of no new data that will
         * get dispatched.
         */
        @Override
        public void suspendRead() {
        }

        /**
         * Resume reads from the underlying transport. If {@link #suspendRead()} was not called before, this
         * has no effect.
         */
        @Override
        public void resumeRead() {
        }
    }

    private class RXTXSerialPortEventListener implements SerialPortEventListener {

        private final RxtxUnsafe channelSink;

        public RXTXSerialPortEventListener(final RxtxUnsafe channelSink) {
            this.channelSink = channelSink;
        }

        @Override
        public void serialEvent(final SerialPortEvent event) {
            switch (event.getEventType()) {
                case SerialPortEvent.DATA_AVAILABLE:
                    final ChannelPipeline pipeline = channelSink.channel.pipeline();
                    try {
                        if (channelSink.inputStream != null && channelSink.inputStream.available() > 0) {
                            int available = channelSink.inputStream.available();
                            byte[] buffer = new byte[available];
                            int read = channelSink.inputStream.read(buffer);
                            if (read > 0) {
                                pipeline.inboundByteBuffer().writeBytes(buffer, 0, read);
                                pipeline.fireInboundBufferUpdated();
                            }
                        }
                    } catch (IOException e) {
                        pipeline.fireExceptionCaught(e);
                        channelSink.channel.close();
                    }
                    break;
            }
        }
    }

}
