package poke.server.image;

import io.netty.channel.Channel;

import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Image.Request;
import poke.server.image.InboundImageWorker;
import poke.server.image.OutboundImageWorker;
import poke.server.image.ImageQueue.ImageQueueEntry;

public class ImageQueue {
	protected static Logger logger = LoggerFactory.getLogger("image");

	protected static LinkedBlockingDeque<ImageQueueEntry> inbound = new LinkedBlockingDeque<ImageQueueEntry>();
	protected static LinkedBlockingDeque<ImageQueueEntry> outbound = new LinkedBlockingDeque<ImageQueueEntry>();

	// TODO static is problematic
	private static OutboundImageWorker oworker;
	private static InboundImageWorker iworker;

	// not the best method to ensure uniqueness
	private static ThreadGroup tgroup = new ThreadGroup("ImageQueue-" + System.nanoTime());

	public static void startup() {
		if (iworker != null)
			return;

		iworker = new InboundImageWorker(tgroup, 1);
		iworker.start();
		oworker = new OutboundImageWorker(tgroup, 1);
		oworker.start();
	}

	public static void shutdown(boolean hard) {
		// TODO shutdon workers
	}

	public static void enqueueRequest(Request req, Channel ch) {
		try {
			ImageQueueEntry entry = new ImageQueueEntry(req, ch);
			inbound.put(entry);
		} catch (InterruptedException e) {
			logger.error("message not enqueued for processing", e);
		}
	}

	public static void enqueueResponse(Request reply, Channel ch) {
		try {
			ImageQueueEntry entry = new ImageQueueEntry(reply, ch);
			outbound.put(entry);
		} catch (InterruptedException e) {
			logger.error("message not enqueued for reply", e);
		}
	}

	public static class ImageQueueEntry {
		public ImageQueueEntry(Request req, Channel ch) {
			this.req = req;
			this.channel = ch;
		}

		public Request req;
		public Channel channel;
	}
}
