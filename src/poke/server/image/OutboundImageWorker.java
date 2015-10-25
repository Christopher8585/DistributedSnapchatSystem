package poke.server.image;

import io.netty.channel.ChannelFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.image.ImageQueue.ImageQueueEntry;


public class OutboundImageWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("image");

	int workerId;
	boolean forever = true;

	public OutboundImageWorker(ThreadGroup tgrp, int workerId) {
		super(tgrp, "outbound-image-" + workerId);
		this.workerId = workerId;

		if (ImageQueue.outbound == null)
			throw new RuntimeException("image worker detected null queue");
	}

	@Override
	public void run() {
		while (true) {
			if (!forever && ImageQueue.outbound.size() == 0)
				break;

			try {
				// block until a message is enqueued
				ImageQueueEntry msg = ImageQueue.outbound.take();

				if (logger.isDebugEnabled())
					logger.debug("Outbound image message routing to node " + msg.req.getHeader().getClientId());

				if (msg.channel.isWritable()) {
					boolean rtn = false;
					if (msg.channel != null && msg.channel.isOpen() && msg.channel.isWritable()) {
						ChannelFuture cf = msg.channel.write(msg);

						// blocks on write - use listener to be async
						cf.awaitUninterruptibly();
						rtn = cf.isSuccess();		
						if (!rtn)
							ImageQueue.outbound.putFirst(msg);
					}

				} else {
					logger.info("channel to node " + msg.req.getHeader().getClientId() + " is not writable");
					ImageQueue.outbound.putFirst(msg);
				}
			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected image communcation failure", e);
				break;
			}
		}

		if (!forever) {
			logger.info("image outbound queue closing");
		}
	}

}
