package poke.server.election;

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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.client.comm.CommHandler;
import poke.client.comm.CommInitializer;
import poke.core.Image.Header;
import poke.core.Image.Ping;
import poke.core.Image.Request;
import poke.core.Mgmt.PayLoad;
import poke.core.Mgmt.Heartbeat;
import poke.core.Mgmt.LeaderElection;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.VectorClock;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.server.managers.ConnectionManager;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.LogManager;

public class RaftElection implements Election {

	protected static Logger logger = LoggerFactory.getLogger("raft");

	private Integer nodeId;
	private ElectionState current;
	private ElectionListener listener;
	// Maintaining an array of votes
	private boolean[] votesFromNodes;
	// Raft- Save leader
	private int leaderID = -1;

	public RaftElection() {

	}

	public RaftElection(Integer id) {
		this.nodeId = id;
	}

	@Override
	public void setListener(ElectionListener listener) {
		this.listener = listener;
	}

	public void setLeaderID(Integer id) {
		this.leaderID = id;
	}

	public int getLeaderID() {
		return this.leaderID;
	}

	public void setVote(Integer id) {
		votesFromNodes[id] = true;
	}

	public int getVotesCount() {
		int votes = 0;
		if (votesFromNodes != null)
			for (int i = 0; i < votesFromNodes.length; i++)
				if (votesFromNodes[i])
					votes++;

		return votes;
	}

	private void setElectionId(int id) {
		if (current != null) {
			if (current.electionID != id) {
				// need to resolve this!
			}
		}
	}

	public Integer getNodeId() {
		return nodeId;
	}

	@Override
	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	@Override
	public synchronized void clear() {
		current = null;
		votesFromNodes = null;
	}

	@Override
	public boolean isElectionInprogress() {
		return current != null;
	}

	@Override
	public Integer getElectionId() {
		if (current == null)
			return null;
		return current.electionID;
	}

	private void notify(boolean success, Integer leader) {
		
		if (listener != null)
			listener.concludeWith(success, leader);
	}

	private boolean updateCurrent(LeaderElection req) {
		boolean isNew = false;

		if (current == null) {
			current = new ElectionState();
			isNew = true;
		}

		current.electionID = req.getElectId();
		current.candidate = req.getCandidateId();
		current.desc = req.getDesc();
		current.maxDuration = req.getExpires();
		current.startedOn = System.currentTimeMillis();
		current.state = req.getAction();
		current.id = -1; // TODO me or sender?
		current.active = true;
		current.term = req.getTerm();
		current.index = req.getIndex();
		if (votesFromNodes == null);
		setLength(HeartbeatManager.getInstance().conf.getAdjacent()
				.getAdjacentNodes().size() + 1); // ###

		return isNew;
	}

	@Override
	public Integer createElectionID() {
		return ElectionIDGenerator.nextID();
	}

	@Override
	public Integer getWinner() {
		if (current == null)
			return null;
		else if (current.state.getNumber() == ElectAction.DECLAREELECTION_VALUE)
			return current.candidate;
		else
			return null;
	}

	@Override
	public Management process(Management mgmt) {
		if (!mgmt.hasElection())
			return null;

		LeaderElection req = mgmt.getElection();
		Management rtn = null;

		if (req.getAction().getNumber() == ElectAction.DECLAREELECTION_VALUE) {
			// an election is declared!

			System.out
					.println("\n\n*********************************************************");
			System.out.println(" RAFT ELECTION: Election declared");
			System.out.println("   Election ID:  " + req.getElectId());
			System.out.println("   Rcv from:     Node "
					+ mgmt.getHeader().getOriginator());
			System.out
					.println("   Expires:      " + new Date(req.getExpires()));
			System.out.println("   Nominates:    Node " + req.getCandidateId());
			System.out.println("   Desc:         " + req.getDesc());
			System.out
					.println("*********************************************************\n\n");

			// sync master IDs to current election
			ElectionIDGenerator.setMasterID(req.getElectId());

			/**
			 * a new election can be declared over an existing election.
			 * 
			 * TODO need to have an monotonically increasing ID that we can test
			 */

			//if(current==null)
				//isNew= updateCurrent(req);//Updating nodes state..not reqd for raft.
			rtn = castVote(mgmt, true);
			
		} else if (req.getAction().getNumber() == ElectAction.DECLAREVOID_VALUE) {
			// no one was elected, I am dropping into standby mode
			logger.info("Already in a state of election..couldnt get a vote");
			return null;
			//this.clear();
			//notify(false, null);
		} else if (req.getAction().getNumber() == ElectAction.DECLAREWINNER_VALUE) {
			// some node declared itself the leader
			logger.info("Election " + req.getElectId() + ": Node "
					+ req.getCandidateId() + " is declared the leader");
			updateCurrent(mgmt.getElection());
			current.active = false; // it's over
			notify(true, req.getCandidateId());
		} else if (req.getAction().getNumber() == ElectAction.ABSTAIN_VALUE) {
			// for some reason, a node declines to vote - therefore, do nothing
		} else if (req.getAction().getNumber() == ElectAction.NOMINATE_VALUE) {

			//boolean isNew = updateCurrent(mgmt.getElection());
			rtn = castVote(mgmt, true);
		} else if(req.getAction().getNumber() == LeaderElection.ElectAction.THELEADERIS_VALUE){

			boolean isNew = updateCurrent(mgmt.getElection());
			rtn = castVote(mgmt, isNew);
		}
		else
		{
			// this is me!
			System.out.println();
		}

		return rtn;
	}

	private synchronized Management castVote(Management mgmt, boolean isNew) {
		if (!mgmt.hasElection())
			return null;
		System.out.println("castVote");

		LeaderElection req = mgmt.getElection();

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		mhb.setOriginator(this.nodeId);// For Raft

		elb.setElectId(req.getElectId());
		elb.setAction(ElectAction.NOMINATE);
		elb.setDesc(req.getDesc());
		elb.setExpires(req.getExpires());
		elb.setCandidateId(req.getCandidateId());

		// my vote
		if (req.getCandidateId() == this.nodeId && req.getAction().getNumber()==ElectAction.NOMINATE_VALUE) {
			setVote(mgmt.getHeader().getOriginator());
			setVote(this.nodeId);// Voting for Itself
			System.out.println("----------------Vote Counts:  "
					+ getVotesCount() + ".. Received from Node "
					+ mgmt.getHeader().getOriginator());

			if (getVotesCount() > votesFromNodes.length / 2) {
				notify(true, this.nodeId);
				ElectionManager.getInstance().leaderNode=this.nodeId;
				elb.setAction(ElectAction.DECLAREWINNER);
				logger.info("Node " + this.nodeId
						+ " is declaring itself the leader");

				// Broadcast to all nodes in Cluster
				/*
				File f = new File("chkip");
				FileReader fr;
				try {
					fr = new FileReader(f);
					BufferedReader br = new BufferedReader(fr);
					for (int i = 0; i < br.read(); i++){
						String host=br.readLine();
						System.out.println(host);
						//sendToCluster(host, 5770);
					}
					// System.out.println(br.readLine());
					// String ips[]=f.toString().split()
					
					sendToCluster(localhost, 5770);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/
				
			} else
				return null;// Do nothing

		} else if(!ElectionManager.electionStateFlag){
			elb.setAction(ElectAction.DECLAREVOID);
		}
		
		if(elb.getAction().getNumber()==ElectAction.NOMINATE_VALUE)
			ElectionManager.electionStateFlag=false;

		// add myself (may allow duplicate entries, if cycling is allowed)
		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(this.nodeId);
		rpb.setTime(System.currentTimeMillis());
		rpb.setVersion(req.getElectId());
		mhb.addPath(rpb);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		return mb.build();
	}

	private void sendToCluster(String ip, int port) {

		EventLoopGroup group;
		CommHandler handler;
		// ChannelFuture channel;

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
			h.setClusterId(7);
			h.setClientId(1);
			h.setIsClient(false);
			h.setCaption("TEST");

			poke.core.Image.PayLoad.Builder p = poke.core.Image.PayLoad
					.newBuilder();
			// p.setData(getImageByteString());
			p.setData(hexStringToByteArray("e04fd020ea3a6910a2d808002b30309d"));// Dummy
																				// value

			Ping.Builder ping = Ping.newBuilder();
			ping.setIsPing(true);

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
			// ClientClosedListener ccl = new ClientClosedListener(this);
			// channel.channel().closeFuture().addListener(ccl);

		} catch (Exception ex) {
			logger.error("failed to initialize the client connection", ex);
			System.out.println("Failed " + ex);
		}

	}

	public static class ClientClosedListener implements ChannelFutureListener {
		RaftElection cc;

		public ClientClosedListener(RaftElection cc) {
			this.cc = cc;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			// we lost the connection or have shutdown.

			// @TODO if lost, try to re-establish the connection
		}
	}

	private ByteString getImageByteString() throws IOException {
		File imgPath = new File("test.png");
		byte[] fileData = new byte[(int) imgPath.length()];
		DataInputStream dis = new DataInputStream(
				(new FileInputStream(imgPath)));
		dis.readFully(fileData);
		dis.close();
		return ByteString.copyFrom(fileData);// readFrom(streamToDrain)
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

	@Override
	public void setLength(int size) {
		// TODO Auto-generated method stub
		votesFromNodes = new boolean[size];
	}

}
