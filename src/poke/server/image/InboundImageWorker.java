package poke.server.image;

import io.netty.channel.Channel;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.server.Server;
import poke.server.conf.ServerConf;
import poke.server.image.ImageQueue.ImageQueueEntry;
import poke.core.Image.Header;
import poke.core.Image.Ping;
import poke.core.Image.Request;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.PayLoad;
import poke.server.managers.ConnectionManager;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.LogManager;
import poke.server.managers.NetworkManager;
import poke.server.storage.jpa.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.eclipse.persistence.config.PersistenceUnitProperties;

public class InboundImageWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("image");

	int workerId;
	boolean forever = true;

	private static ConcurrentHashMap<Integer, Request> leadersList = new ConcurrentHashMap<Integer, Request>();

	public InboundImageWorker(ThreadGroup tgrp, int workerId) {
		super(tgrp, "inbound-image-" + workerId);
		this.workerId = workerId;

		if (ImageQueue.outbound == null)
			throw new RuntimeException("connection worker detected null queue");
	}

	@Override
	public void run() {
		while (true) {
			if (!forever && ImageQueue.inbound.size() == 0)
				break;

			try {

				// block until a message is enqueued
				ImageQueueEntry msg = ImageQueue.inbound.take();

				if (logger.isDebugEnabled())
					logger.debug("Inbound image message received");

				Request req = (Request) msg.req;

				ByteString bs = req.getPayload().getData();
				// System.out.println(Arrays.toString(bs.toByteArray()));

				if (req.getPing().getIsPing())
					if (leadersList.contains(req)) {
						System.out
								.println("---------------------Updating leader to the list--------------------------");
						leadersList.put(req.getHeader().getClusterId(), req);
					} else {
						System.out
								.println("---------------------Adding leader to the list--------------------------");
						leadersList.put(req.getHeader().getClusterId(), req);
					}
				else {
					// Image
					if (req.getHeader().getCaption().toString().contains("Security Code:999")) {

						String[] s = req.getHeader().getCaption().split("#");
						System.out
								.println("-----------------------Responding to client's request for an image.");
						getImageForClient(Integer.parseInt(s[1]), msg.channel);

					} else {
						if (ElectionManager.getInstance().whoIsTheLeader() != null) {
							if (ElectionManager.getInstance().whoIsTheLeader() == HeartbeatManager.conf
									.getNodeId()) {

								System.out
										.println("------------IMG received from client.. sending to other nodes");
								// SAVE IN DB
								// System.out.println(Arrays.toString(req.getPayload().getData().toByteArray()));
								// Saves in Log and db.. forwards to other nodes
								sendToMyNodes(req);
								replyToClient(msg.channel);

							} else {
								System.out
										.println("IMG received from client.. forwarding request to leader");
								sendToLeader(req);
								replyToClient(msg.channel);
							}

						} else
							System.out
									.println("--------------NO LEADER!!..REQUEST HAS BEEN REJECTED..");
					}
				}

			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected processing failure, halting worker.",
						e);
				break;
			}
		}

		if (!forever) {
			logger.info("connection queue closing");
		}
	}

	private void getImageForClient(int index, Channel channel) {
		ImageOperation iop = new ImageOperation();
		ImageInfo info = iop.getImage(index);

		Header.Builder h = Header.newBuilder();
		h.setClusterId(0);
		h.setClientId(0);
		h.setIsClient(false);
		
		if(info!=null)
			h.setCaption(info.getCaption());
		else
			h.setCaption("No image found");

		poke.core.Image.PayLoad.Builder p = poke.core.Image.PayLoad
				.newBuilder();
		
		if(info!=null)
			p.setData(ByteString.copyFrom(info.getImage()));
		else
			p.setData(hexStringToByteArray("e04fd020ea3a6910a2d808002b30309d"));
			

		Ping.Builder ping = Ping.newBuilder();
		ping.setIsPing(false);

		Request.Builder r = Request.newBuilder();
		r.setHeader(h.build());
		r.setPayload(p.build());
		r.setPing(ping.build());

		try {
			channel.writeAndFlush(r.build());

		} catch (Exception ex) {
			System.out.println("NOT WORKING");
		}
	}

	private void replyToClient(Channel channel) {
		Header.Builder h = Header.newBuilder();
		h.setClusterId(7);
		h.setClientId(1);
		h.setIsClient(false);
		h.setCaption("Response from Server");

		poke.core.Image.PayLoad.Builder p = poke.core.Image.PayLoad
				.newBuilder();
		p.setData(hexStringToByteArray("e04fd020ea3a6910a2d808002b30309d"));

		Ping.Builder ping = Ping.newBuilder();
		ping.setIsPing(false);

		Request.Builder r = Request.newBuilder();
		r.setHeader(h.build());
		r.setPayload(p.build());
		r.setPing(ping.build());

		try {
			channel.writeAndFlush(r.build());

		} catch (Exception ex) {
			System.out.println("NOT WORKING");
		}
	}

	private void sendToMyNodes(Request req) {

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(HeartbeatManager.conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		PayLoad.Builder p = PayLoad.newBuilder();
		p.setCaption(req.getHeader().getCaption());
		p.setData(req.getPayload().getData());

		int index = LogManager.getInstance().processRequest(req);// Saved inside
																	// the log
																	// and
																	// returns
																	// the value
		p.setIndex(index);

		ImageOperation iop = new ImageOperation();
		ImageInfo i = new ImageInfo(index, req.getPayload().getData()
				.toByteArray(), req.getHeader().getCaption());
		iop.doAction(i);

		Management.Builder b = Management.newBuilder();
		b.setHeader(mhb.build());
		b.setPayload(p.build());
		System.out.println("SENT TO ALL NODES");
		ConnectionManager.broadcast(b.build());
	}

	private void sendToLeader(Request req) {

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(HeartbeatManager.conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		PayLoad.Builder p = PayLoad.newBuilder();
		p.setCaption(req.getHeader().getCaption());
		p.setData(req.getPayload().getData());

		Management.Builder b = Management.newBuilder();
		b.setHeader(mhb.build());
		b.setPayload(p.build());
		System.out.println("SENT TO LEADER");
		ConnectionManager.sendTo(b.build(), ElectionManager.getInstance()
				.whoIsTheLeader());

	}

	public static ByteString hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
					.digit(s.charAt(i + 1), 16));
		}
		return ByteString.copyFrom(data);
	}
}
