package poke.server;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.client.comm.CommHandler;
import poke.client.comm.CommInitializer;
import poke.core.Image.Header;
import poke.core.Image.PayLoad;
import poke.core.Image.Ping;
import poke.core.Image.Request;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf.AdjacentConf;
import poke.server.managers.HeartbeatData;
import poke.server.queue.ChannelQueue;
import poke.server.queue.QueueFactory;

public class ServerHandler2 extends SimpleChannelInboundHandler<Request> {
	protected static Logger logger = LoggerFactory.getLogger("server");

	private ChannelQueue queue;
	
	

	public ServerHandler2() {
		// logger.info("** ServerHandler created **");
	}
	

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Request req) throws Exception {
		// processing is deferred to the worker threads
		ByteString bs=req.getPayload().getData();
		//System.out.println(Arrays.toString(bs.toByteArray()));
		/*
		if(req.getPing().getIsPing()) 
			if(Server.leadersList.contains(req)){
				System.out.println("---------------------Updating leader to the list--------------------------");
				Server.leadersList.put(req.getHeader().getClusterId(),req);
			}
			else{
				System.out.println("---------------------Adding leader to the list--------------------------");
				Server.leadersList.put(req.getHeader().getClusterId(),req);
			}
		else{
			//Image
			System.out.println(Arrays.toString(bs.toByteArray()));
			*/
			//sendToMyNodes(req);

		//}

	}
	
	private void sendToNode(String ip, int port, Request req) {

		EventLoopGroup group;
		CommHandler handler;
		//ChannelFuture channel; 

		group = new NioEventLoopGroup();
		try {
			handler = new CommHandler();

			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class)
					.handler(new CommInitializer());

			// Start the connection attempt.
			Channel ch = b.connect(ip, port).sync().channel();
			// Read commands from the stdin.
			ChannelFuture lastWriteFuture = null;

			Header.Builder h = Header.newBuilder();
			h.setClusterId(req.getHeader().getClusterId());
			h.setClientId(req.getHeader().getClientId());
			h.setIsClient(false);
			h.setCaption("TEST");

			PayLoad.Builder p = PayLoad.newBuilder();
			//p.setData(getImageByteString());
			p.setData(req.getPayload().getData());//Dummy value
			
			Ping.Builder ping = Ping.newBuilder();
			ping.setIsPing(false);

			Request.Builder r = Request.newBuilder();
			r.setHeader(h.build());
			r.setPayload(p.build());
			r.setPing(ping.build());

			try {
				// Sends the received line to the server.
				lastWriteFuture = ch.writeAndFlush(r.build());// (r.build());
				System.out.println(lastWriteFuture.isDone() + " "
						+ lastWriteFuture.isSuccess());
				System.out.println("Message sent");

			} catch (Exception ex) {
				System.out.println("NOT WORKING");
			}
			ch.closeFuture().sync();
			
			if (lastWriteFuture != null) {
				lastWriteFuture.sync();
			}
			group.shutdownGracefully();
			//ClientClosedListener ccl = new ClientClosedListener(this);
			//channel.channel().closeFuture().addListener(ccl);

		} catch (Exception ex) {
			logger.error("failed to initialize the client connection", ex);
			System.out.println("Failed " + ex);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

	/**
	 * Isolate how the server finds the queue. Note this cannot return null.
	 * 
	 * @param channel
	 * @return
	 */
	private ChannelQueue queueInstance(Channel channel) {
		// if a single queue is needed, this is where we would obtain a
		// handle to it.

		if (queue != null)
			return queue;
		else {
			queue = QueueFactory.getInstance(channel);

			// on close remove from queue
			channel.closeFuture().addListener(new ConnectionClosedListener(queue));
		}

		return queue;
	}

	public static class ConnectionClosedListener implements ChannelFutureListener {
		private ChannelQueue sq;

		public ConnectionClosedListener(ChannelQueue sq) {
			this.sq = sq;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			// Note re-connecting to clients cannot be initiated by the server
			// therefore, the server should remove all pending (queued) tasks. A
			// more optimistic approach would be to suspend these tasks and move
			// them into a separate bucket for possible client re-connection
			// otherwise discard after some set period. This is a weakness of a
			// connection-required communication design.

			if (sq != null)
				sq.shutdown(true);
			sq = null;
		}

	}
}
