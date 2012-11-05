/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureAggregator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundByteHandlerAdapter;

import java.util.List;

/**
 * Downstream handler which places an upper bound on the size of written
 * {@link io.netty.buffer.ByteBuf ByteBuf}. If a buffer
 * is bigger than the specified upper bound, the buffer is broken up
 * into two or more smaller pieces.
 * <p>
 * This is utilised by the http tunnel to smooth out the per-byte latency,
 * by placing an upper bound on HTTP request / response body sizes.
 * </p>
 */
public class WriteFragmenter extends ChannelOutboundByteHandlerAdapter {

    public static final String NAME = "writeFragmenter";

    private int splitThreshold;

    public WriteFragmenter(int splitThreshold) {
        this.splitThreshold = splitThreshold;
    }

    public void setSplitThreshold(int splitThreshold) {
        this.splitThreshold = splitThreshold;
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        ByteBuf in = ctx.outboundByteBuffer();
        ByteBuf out = ctx.nextOutboundByteBuffer();

        while (in.readable()) {
            if(in.readableBytes() <= splitThreshold) {
                ctx.write(in);
            } else {
                List<ByteBuf> fragments = WriteSplitter.split(in, splitThreshold);
                ChannelFutureAggregator aggregator = new ChannelFutureAggregator(future);
                for(ByteBuf fragment: fragments) {
                    ChannelFuture fragmentFuture = Channels.future(ctx.channel(), true);
                    aggregator.addFuture(fragmentFuture);
                    ctx.write(fragment, fragmentFuture);
                }
            }
        }

        in.unsafe().discardSomeReadBytes();
        ctx.flush(future);
    }
    
}
