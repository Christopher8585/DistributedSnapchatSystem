package poke.server.image;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Image;
import poke.core.Image.Request;

public class ImageHandler extends SimpleChannelInboundHandler<Request> {
	protected static Logger logger = LoggerFactory.getLogger("image");

	public ImageHandler() {
		// logger.info("** HeartbeatHandler created **");
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Request req) throws Exception {
		// processing is deferred to the worker threads
		 logger.info("---> image handler got a message from client:" + req.getHeader().getClientId()+ " clusterId:"+ req.getHeader().getClientId());
		ImageQueue.enqueueRequest(req, ctx.channel());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.error("image channel inactive");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

	/**
	 * usage:
	 * 
	 * <pre>
	 * channel.getCloseFuture().addListener(new ManagementClosedListener(queue));
	 * </pre>
	 * 
	 * @author gash
	 * 
	 */
	public static class ImageClosedListener implements ChannelFutureListener {
		// private ManagementQueue sq;

		public ImageClosedListener(ImageQueue sq) {
			// this.sq = sq;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			// if (sq != null)
			// sq.shutdown(true);
			// sq = null;
		}

	}
}
