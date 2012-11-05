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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Upstream handler which is responsible for determining whether a received HTTP request is a legal
 * tunnel request, and if so, invoking the appropriate request method on the
 * {@link ServerMessageSwitch} to service the request.
 */
class AcceptedServerChannelRequestDispatch extends ChannelInboundMessageHandlerAdapter<HttpRequest> {

    public static final String NAME = "AcceptedServerChannelRequestDispatch";

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(AcceptedServerChannelRequestDispatch.class);

    private final ServerMessageSwitchUpstreamInterface messageSwitch;

    public AcceptedServerChannelRequestDispatch(ServerMessageSwitchUpstreamInterface messageSwitch) {
        this.messageSwitch = messageSwitch;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        if (HttpTunnelMessageUtils.isOpenTunnelRequest(request)) {
            handleOpenTunnel(ctx);
        }
        else if (HttpTunnelMessageUtils.isSendDataRequest(request)) {
            handleSendData(ctx, request);
        }
        else if (HttpTunnelMessageUtils.isReceiveDataRequest(request)) {
            handleReceiveData(ctx, request);
        }
        else if (HttpTunnelMessageUtils.isCloseTunnelRequest(request)) {
            handleCloseTunnel(ctx, request);
        }
        else {
            respondWithRejection(ctx, request, "invalid request to netty HTTP tunnel gateway");
        }
    }

    private void handleOpenTunnel(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        String tunnelId = messageSwitch.createTunnel((InetSocketAddress) remoteAddress);

        if (LOG.isDebugEnabled()) {
            LOG.debug("open tunnel request received from " + remoteAddress + " - allocated ID " + tunnelId);
        }
        respondWith(ctx, HttpTunnelMessageUtils.createTunnelOpenResponse(tunnelId));
    }

    private void handleCloseTunnel(ChannelHandlerContext ctx,
                                   HttpRequest request) {
        String tunnelId = checkTunnelId(request, ctx);
        if (tunnelId == null) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("close tunnel request received for tunnel " + tunnelId);
        }
        messageSwitch.clientCloseTunnel(tunnelId);
        respondWith(ctx, HttpTunnelMessageUtils.createTunnelCloseResponse())
                .addListener(ChannelFutureListener.CLOSE);
    }

    private void handleSendData(ChannelHandlerContext ctx, HttpRequest request) {
        String tunnelId = checkTunnelId(request, ctx);
        if (tunnelId == null) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("send data request received for tunnel " + tunnelId);
        }

        if (HttpHeaders.getContentLength(request, 0) == 0 ||
                request.getContent() == null ||
                request.getContent().readableBytes() == 0) {
            respondWithRejection(ctx, request,
                    "Send data requests must contain data");
            return;
        }

        messageSwitch.routeInboundData(tunnelId, request.getContent());
        respondWith(ctx, HttpTunnelMessageUtils.createSendDataResponse());
    }

    private void handleReceiveData(ChannelHandlerContext ctx,
                                   HttpRequest request) {
        String tunnelId = checkTunnelId(request, ctx);
        if (tunnelId == null) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("poll data request received for tunnel " + tunnelId);
        }
        messageSwitch.pollOutboundData(tunnelId, ctx.channel());
    }

    private String checkTunnelId(HttpRequest request, ChannelHandlerContext ctx) {
        String tunnelId = HttpTunnelMessageUtils.extractTunnelId(request);
        if (tunnelId == null) {
            respondWithRejection(ctx, request,
                    "no tunnel id specified in request");
        } else if (!messageSwitch.isOpenTunnel(tunnelId)) {
            respondWithRejection(ctx, request,
                    "specified tunnel is either closed or does not exist");
            return null;
        }

        return tunnelId;
    }

    /**
     * Sends the provided response back on the channel, returning the created ChannelFuture
     * for this operation.
     * @param ctx ChannelHandlerContext
     * @param response ResponseObject to be sent (outbound)
     * @return The channel future
     */
    private ChannelFuture respondWith(ChannelHandlerContext ctx, HttpResponse response) {

        ChannelFuture writeFuture = Channels.future(ctx.channel());
        ctx.nextOutboundMessageBuffer().add(response);
        ctx.flush(writeFuture);
        ctx.write(response, writeFuture);
        return writeFuture;
    }

    /**
     * Sends an HTTP 400 message back to on the channel with the specified error message, and asynchronously
     * closes the channel after this is successfully sent.
     * @param ctx ChannelHandlerContext
     * @param rejectedRequest HTTPRequest that was rejected
     * @param errorMessage Error Message to be send
     */
    private void respondWithRejection(ChannelHandlerContext ctx,
                                      HttpRequest rejectedRequest, String errorMessage) {
        if (LOG.isWarnEnabled()) {
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            String tunnelId = HttpTunnelMessageUtils.extractTunnelId(rejectedRequest);
            if (tunnelId == null) {
                tunnelId = "<UNKNOWN>";
            }
            LOG.warn("Rejecting request from " + remoteAddress +
                    " representing tunnel " + tunnelId + " : " + errorMessage);
        }
        HttpResponse rejection = HttpTunnelMessageUtils.createRejection(rejectedRequest, errorMessage);
        respondWith(ctx, rejection).addListener(ChannelFutureListener.CLOSE);
    }

}
