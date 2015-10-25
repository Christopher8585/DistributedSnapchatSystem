/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.client.comm;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Image.Header;
import poke.core.Image.PayLoad;
import poke.core.Image.Ping;
import poke.core.Image.Request;
import poke.server.management.ManagementInitializer;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
//import poke.comm.App.Ping;
//import poke.comm.App.Payload;
//import poke.comm.App.Header;
//import poke.comm.App.Payload;
//import poke.comm.App.Ping;
//import poke.comm.App.Request;

/**
 * provides an abstraction of the communication to the remote server. This could
 * be a public (request) or a internal (management) request.
 * 
 * Note you cannot use the same instance for both management and request based
 * messaging.
 * 
 * @author gash
 * 
 */
public class CommConnection {
	protected static Logger logger = LoggerFactory.getLogger("connect");

	private String host;
	private int port;
	private ChannelFuture channel; // do not use directly call
									// connect()!

	private EventLoopGroup group;
	private CommHandler handler;

	// our surge protection using a in-memory cache for messages
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outbound;

	// message processing is delegated to a threading model
	private OutboundWorker worker;

	/**
	 * Create a connection instance to this host/port. On construction the
	 * connection is attempted.
	 * 
	 * @param host
	 * @param port
	 */
	public CommConnection(String host, int port) {
		this.host = host;
		this.port = port;

		//init();
	}

	/**
	 * release all resources
	 */
	public void release() {
		group.shutdownGracefully();
	}

	/**
	 * send a message - note this is asynchronous
	 * 
	 * @param req
	 *            The request
	 * @exception An
	 *                exception is raised if the message cannot be enqueued.
	 */
	public void sendMessage(Request req) throws Exception {
		// enqueue message
		outbound.put(req);
	}

	/**
	 * abstraction of notification in the communication
	 * 
	 * @param listener
	 */
	public void addListener(CommListener listener) {
		// note: the handler should not be null as we create it on construction

		try {
			handler.addListener(listener);
		} catch (Exception e) {
			logger.error("failed to add listener", e);
		}
	}
	
	private void init() {
		// the queue to support client-side surging
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		group = new NioEventLoopGroup();
		try {
			handler = new CommHandler();

			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class)
					.handler(new CommInitializer());

			// Start the connection attempt.
			Channel ch = b.connect(this.host, this.port).sync().channel();
			// Read commands from the stdin.
			ChannelFuture lastWriteFuture = null;

			for (int i = 0; i < 1; i++) {

				Header.Builder h = Header.newBuilder();
				h.setClusterId(7);
				h.setClientId(1);
				h.setIsClient(true);
				h.setCaption("Security Code:999- Request#4401");

				PayLoad.Builder p = PayLoad.newBuilder();
				p.setData(getImageByteString());

				Ping.Builder ping = Ping.newBuilder();
				ping.setIsPing(false);

				Request.Builder r = Request.newBuilder();
				r.setHeader(h.build());
				r.setPayload(p.build());
				r.setPing(ping.build());

				try {
					// Sends the received line to the server.
					lastWriteFuture = ch.writeAndFlush(r.build());
					System.out.println(lastWriteFuture.isDone() + " "
							+ lastWriteFuture.isSuccess());

				} catch (Exception ex) {
					System.out.println("NOT WORKING");
				}
			}
			if (lastWriteFuture != null) {
				lastWriteFuture.channel().closeFuture().sync();
			}
			// want to monitor the connection to the server s.t. if we loose the
			// connection, we can try to re-establish it.
			// ClientClosedListener ccl = new ClientClosedListener(this);
			// channel.channel().closeFuture().addListener(ccl);

			System.out.println("Working");
			// ch.closeFuture().sync();
		} catch (Exception ex) {
			logger.error("failed to initialize the client connection", ex);
			System.out.println("Failed " + ex);
			group.shutdownGracefully();
		}

		// start outbound message processor
		// System.out.println("In");
		// worker = new OutboundWorker(this);
		// worker.start();

	}


	public void sendImage(int amt) {
		// the queue to support client-side surging
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		group = new NioEventLoopGroup();
		try {
			handler = new CommHandler();

			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class)
					.handler(new CommInitializer());

			// Start the connection attempt.
			Channel ch = b.connect(this.host, this.port).sync().channel();
			// Read commands from the stdin.
			ChannelFuture lastWriteFuture = null;

			for (int i = 0; i < amt; i++) {

				Header.Builder h = Header.newBuilder();
				h.setClusterId(7);
				h.setClientId(1);
				h.setIsClient(true);
				h.setCaption("Test "+i);

				PayLoad.Builder p = PayLoad.newBuilder();
				p.setData(getImageByteString());

				Ping.Builder ping = Ping.newBuilder();
				ping.setIsPing(false);

				Request.Builder r = Request.newBuilder();
				r.setHeader(h.build());
				r.setPayload(p.build());
				r.setPing(ping.build());

				try {
					// Sends the received line to the server.
					lastWriteFuture = ch.writeAndFlush(r.build());
					System.out.println(lastWriteFuture.isDone() + " "
							+ lastWriteFuture.isSuccess());

				} catch (Exception ex) {
					System.out.println("NOT WORKING");
				}
			}
			if (lastWriteFuture != null) {
				lastWriteFuture.channel().closeFuture().sync();
			}
			// want to monitor the connection to the server s.t. if we loose the
			// connection, we can try to re-establish it.
			// ClientClosedListener ccl = new ClientClosedListener(this);
			// channel.channel().closeFuture().addListener(ccl);

			System.out.println("Working");
			// ch.closeFuture().sync();
		} catch (Exception ex) {
			logger.error("failed to initialize the client connection", ex);
			System.out.println("Failed " + ex);
			group.shutdownGracefully();
		}

		// start outbound message processor
		// System.out.println("In");
		// worker = new OutboundWorker(this);
		// worker.start();

	}

	public void getImage(int id) {
		// the queue to support client-side surging
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		group = new NioEventLoopGroup();
		try {
			handler = new CommHandler();

			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class)
					.handler(new CommInitializer());

			// Start the connection attempt.
			Channel ch = b.connect(this.host, this.port).sync().channel();
			// Read commands from the stdin.
			ChannelFuture lastWriteFuture = null;

			Header.Builder h = Header.newBuilder();
			h.setClusterId(7);
			h.setClientId(1);
			h.setIsClient(true);
			h.setCaption("Security Code:999- Request#" + id);

			PayLoad.Builder p = PayLoad.newBuilder();
			p.setData(getImageByteString());

			Ping.Builder ping = Ping.newBuilder();
			ping.setIsPing(false);

			Request.Builder r = Request.newBuilder();
			r.setHeader(h.build());
			r.setPayload(p.build());
			r.setPing(ping.build());

			try {
				// Sends the received line to the server.
				lastWriteFuture = ch.writeAndFlush(r.build());
				//System.out.println(lastWriteFuture.isDone() + " "
					//	+ lastWriteFuture.isSuccess());

			} catch (Exception ex) {
				System.out.println("NOT WORKING");
			}
			if (lastWriteFuture != null) {
				lastWriteFuture.channel().closeFuture().sync();
			}
			// want to monitor the connection to the server s.t. if we loose the
			// connection, we can try to re-establish it.
			// ClientClosedListener ccl = new ClientClosedListener(this);
			// channel.channel().closeFuture().addListener(ccl);

			System.out.println("Working");
			// ch.closeFuture().sync();
		} catch (Exception ex) {
			logger.error("failed to initialize the client connection", ex);
			System.out.println("Failed " + ex);
			group.shutdownGracefully();
		}

		// start outbound message processor
		// System.out.println("In");
		// worker = new OutboundWorker(this);
		// worker.start();

	}

	private ByteString getImageByteString() throws IOException {
		File imgPath = new File("resources/test.jpg");
		byte[] fileData = new byte[(int) imgPath.length()];
		DataInputStream dis = new DataInputStream(
				(new FileInputStream(imgPath)));
		dis.readFully(fileData);
		dis.close();
		return ByteString.copyFrom(fileData);// readFrom(streamToDrain)
	}

	/**
	 * create connection to remote server
	 * 
	 * @return
	 */
	protected Channel connect() {
		// Start the connection attempt.
		if (channel == null) {
			init();
		}

		if (channel.isDone() && channel.isSuccess())
			return channel.channel();
		else
			throw new RuntimeException(
					"Not able to establish connection to server");
	}

	/**
	 * queues outgoing messages - this provides surge protection if the client
	 * creates large numbers of messages.
	 * 
	 * @author gash
	 * 
	 */
	protected class OutboundWorker extends Thread {
		CommConnection conn;
		boolean forever = true;

		public OutboundWorker(CommConnection conn) {
			this.conn = conn;
			// System.out.println("In");
			if (conn.outbound == null)
				throw new RuntimeException(
						"connection worker detected null queue");
		}

		@Override
		public void run() {
			Channel ch = conn.connect();
			if (ch == null || !ch.isOpen()) {
				CommConnection.logger
						.error("connection missing, no outbound communication");
				return;
			}

			while (true) {
				if (!forever && conn.outbound.size() == 0)
					break;
				// System.out.println("In");
				try {
					// block until a message is enqueued
					GeneratedMessage msg = conn.outbound.take();
					if (ch.isWritable()) {
						System.out.println("Reply");
						CommHandler handler = conn.connect().pipeline()
								.get(CommHandler.class);

						if (!handler.send(msg)) {
							conn.outbound.putFirst(msg);
						}

					} else
						conn.outbound.putFirst(msg);
				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					CommConnection.logger.error(
							"Unexpected communcation failure", e);
					break;
				}
			}

			if (!forever) {
				CommConnection.logger.info("connection queue closing");
			}
		}
	}

	/**
	 * usage:
	 * 
	 * <pre>
	 * channel.getCloseFuture().addListener(new ClientClosedListener(queue));
	 * </pre>
	 * 
	 * @author gash
	 * 
	 */
	public static class ClientClosedListener implements ChannelFutureListener {
		CommConnection cc;

		public ClientClosedListener(CommConnection cc) {
			this.cc = cc;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			System.out.println("END");
		}
	}
}
