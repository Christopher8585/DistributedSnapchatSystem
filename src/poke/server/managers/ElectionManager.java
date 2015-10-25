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
package poke.server.managers;

import java.beans.Beans;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.core.Mgmt.LeaderElection;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.PayLoad;
import poke.core.Mgmt.VectorClock;
import poke.server.conf.ServerConf;
import poke.server.election.Election;
import poke.server.election.ElectionListener;
import poke.server.election.FloodMaxElection;
import poke.server.election.LogEntry;
import poke.server.election.RaftElection;
import poke.server.storage.jpa.ImageInfo;
import poke.server.storage.jpa.ImageOperation;

/**
 * The election manager is used to determine leadership within the network. The
 * leader is not always a central point in which decisions are passed. For
 * instance, a leader can be used to break ties or act as a scheduling dispatch.
 * However, the dependency on a leader in a decentralized design (or any design,
 * matter of fact) can lead to bottlenecks during heavy, peak loads.
 * 
 * TODO An election is a special case of voting. We should refactor to use only
 * voting.
 * 
 * QUESTIONS:
 * 
 * Can we look to the PAXOS alg. (see PAXOS Made Simple, Lamport, 2001) to model
 * our behavior where we have no single point of failure; each node within the
 * network can act as a consensus requester and coordinator for localized
 * decisions? One can envision this as all nodes accept jobs, and all nodes
 * request from the network whether to accept or reject the request/job.
 * 
 * Does a 'random walk' approach to consistent data replication work?
 * 
 * What use cases do we would want a central coordinator vs. a consensus
 * building model? How does this affect liveliness?
 * 
 * Notes:
 * <ul>
 * <li>Communication: the communication (channel) established by the heartbeat
 * manager is used by the election manager to communicate elections. This
 * creates a constraint that in order for internal (mgmt) tasks to be sent to
 * other nodes, the heartbeat must have already created the channel.
 * </ul>
 * 
 * @author gash
 * 
 */
public class ElectionManager implements ElectionListener {
	protected static Logger logger = LoggerFactory.getLogger("election");
	protected static AtomicReference<ElectionManager> instance = new AtomicReference<ElectionManager>();

	private static ServerConf conf;

	/** The election that is in progress - only ONE! */
	private Election election;
	private int electionCycle = -1;
	private Integer syncPt = 1;

	/** The leader */
	public static Integer leaderNode;
	private int votes;
	public static boolean electionStateFlag; // ###

	public static ElectionManager initManager(ServerConf conf) {
		ElectionManager.conf = conf;
		electionStateFlag = true; // Right to Vote
		instance.compareAndSet(null, new ElectionManager());
		// setLength(HeartbeatManager.getInstance().conf.getAdjacent()
		// .getAdjacentNodes().size() + 1); // ###
		return instance.get();
	}

	/**
	 * Access a consistent instance for the life of the process.
	 * 
	 * TODO do we need to have a singleton for this? What happens when a process
	 * acts on behalf of separate interests?
	 * 
	 * @return
	 */
	public static ElectionManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}

	/**
	 * returns the leader of the network
	 * 
	 * @return
	 */
	public Integer whoIsTheLeader() {
		return this.leaderNode;
	}

	/**
	 * initiate an election from within the server - most likely scenario is the
	 * heart beat manager detects a failure of the leader and calls this method.
	 * 
	 * Depending upon the algo. used (bully, flood, lcr, hs) the
	 * manager.startElection() will create the algo class instance and forward
	 * processing to it. If there was an election in progress and the election
	 * ID is not the same, the new election will supplant the current (older)
	 * election. This should only be triggered from nodes that share an edge
	 * with the leader.
	 */
	public void startElection() {

		electionCycle = electionInstance().createElectionID();
		setLength(HeartbeatManager.getInstance().conf.getAdjacent()
				.getAdjacentNodes().size() + 1);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(electionCycle);
		elb.setAction(ElectAction.DECLAREELECTION);
		elb.setDesc("Node " + conf.getNodeId()
				+ " detects no leader. Election!");
		elb.setCandidateId(conf.getNodeId()); // promote self
		elb.setExpires(2 * 60 * 1000 + System.currentTimeMillis()); // 1 minute

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it out to all my edges
		logger.info("Election started by node " + conf.getNodeId());
		ConnectionManager.broadcast(mb.build());
	}

	/**
	 * @param args
	 */
	private static boolean[] votesFromNodes;

	public void setLength(int size) {
		// TODO Auto-generated method stub

		electionInstance().setLength(
				HeartbeatManager.getInstance().conf.getAdjacent()
						.getAdjacentNodes().size() + 1);
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

	public int getTerm() {
		return electionCycle;
	}

	public void setTerm(int t) {
		electionCycle = t;
	}

	public void processRequest(Management mgmt) {
		if (!mgmt.hasElection())
			return;

		LeaderElection req = mgmt.getElection();

		if (req.getAction().getNumber() == LeaderElection.ElectAction.DECLAREVOID_VALUE) {
			//System.out
				//	.println("--------------------Empty vote-----------------------");

			// Two or more candidates
			// Moves back to follower state
			return;
		}

		if (req.getAction().getNumber() == LeaderElection.ElectAction.WHOISTHELEADER_VALUE) {
			respondToWhoIsTheLeader(mgmt);
			return;
		} else if (req.getAction().getNumber() == LeaderElection.ElectAction.THELEADERIS_VALUE) {
			// if (req.getCandidateId() == -1) {
			// Candidate state

			if (electionInstance().isElectionInprogress()) {
				Management rtn = electionInstance().process(mgmt);
				if (rtn != null) {
					if (rtn.getElection().getCandidateId() == conf.getNodeId())
						ConnectionManager.broadcast(rtn);
					else {
						System.out
								.println("------Voting for "
										+ rtn.getElection().getCandidateId());
						ConnectionManager.sendTo(rtn, rtn.getElection()
								.getCandidateId());
					}
				}
			} else {
				respondToWhoIsTheLeader(mgmt);
			}

			/*
			 * setVote(mgmt.getHeader().getOriginator());
			 * setVote(conf.getNodeId());
			 * 
			 * if (getVotesCount() > conf.getNumberOfElectionVotes()) {
			 * synchronized (syncPt) { // startElection();
			 * setLength(HeartbeatManager.getInstance().conf
			 * .getAdjacent().getAdjacentNodes().size() + 1); } }
			 */
			// return;
			// }

			logger.info("Node " + conf.getNodeId()
					+ " got an answer on who the leader is. Its Node "
					+ req.getCandidateId());
			return;
		}

		// else fall through to an election

		if (req.hasExpires()) {
			long ct = System.currentTimeMillis();
			if (ct > req.getExpires()) {
				// ran out of time so the election is over
				election.clear();
				return;
			}
		}

		// Assuming leader has already been voted and node doesn't know about it
		// yet.
		if (leaderNode != null) {
			respondToWhoIsTheLeader(mgmt);
			return;
		}

		Management rtn = electionInstance().process(mgmt);
		if (rtn != null) {
			if (rtn.getElection().getCandidateId() == conf.getNodeId())
				ConnectionManager.broadcast(rtn);
			else {
				System.out.println("---------Voting for "
						+ rtn.getElection().getCandidateId());
				ConnectionManager.sendTo(rtn, rtn.getElection()
						.getCandidateId());
			}
		}

	}

	/**
	 * check the health of the leader (usually called after a HB update)
	 * 
	 * @param mgmt
	 */
	public void assessCurrentState(Management mgmt) {
		logger.info("ElectionManager.assessCurrentState() checking elected leader status");
		// firstTime > 0 &&
		if ((leaderNode == null || leaderNode==-1) && ConnectionManager.getNumMgmtConnections() > 0) {
			// give it two tries to get the leader
			// this.firstTime--;
			respondToWhoIsTheLeader(mgmt);
			// askWhoIsTheLeader();
		} else if ((leaderNode != null || leaderNode!=-1)&& leaderNode!= mgmt.getBeat().getLeaderNode()) {
			// Dead node
			//respondToWhoIsTheLeader(mgmt);
			//System.out.println("---------------Ignoring old leader msgggggggggggg---------------------");
		}
	else {

			logger.info("Ack to leader from node "
					+ mgmt.getHeader().getOriginator());

			if (mgmt.hasBeat() && leaderNode == conf.getNodeId()) {
				if (mgmt.getBeat().getIndex() != -2) {
					if (mgmt.getBeat().getIndex() < LogManager.getInstance()
							.getIndex()) {

						sendUpdatesFromDB((int) LogManager.getInstance()
								.getIndex(), mgmt.getBeat().getIndex(), mgmt
								.getHeader().getOriginator());
					}
				}
			}
		}
	}

	private void sendUpdatesFromLog(int from, int to, Integer sendTo) {
		// Won't work as the log gets intialized on start up..
		// Created a new function sendUpdatesFromDB to get data from the DB
		ImageOperation io = new ImageOperation();
		ImageInfo imageInfo = null;
		while (from != to) {
			MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
			mhb.setOriginator(HeartbeatManager.conf.getNodeId());
			mhb.setTime(System.currentTimeMillis());
			mhb.setSecurityCode(-999);

			PayLoad.Builder p = PayLoad.newBuilder();

			LogEntry le = LogManager.getInstance().logEntries.get(from);

			if (le != null) {
				imageInfo = io.getImage(le.getIndex());
				if (imageInfo != null) {
					p.setCaption(le.getTitle());
					p.setData(ByteString.copyFrom(imageInfo.getImage()));// Loading image 
					p.setIndex(le.getIndex());
					// TODO token must be known between nodes

					Management.Builder b = Management.newBuilder();
					b.setHeader(mhb.build());
					b.setPayload(p.build());

					System.out.println("Updating Node " + sendTo);
					System.out.println("DATA index: " + from
							+ " sent. Data contains title " + le.getTitle());

					ConnectionManager.sendTo(b.build(), sendTo);
				}
			} else
				break;// No log entry found

			from = le.getPrevIndex();

		}

	}

	private void sendUpdatesFromDB(int from, int to, Integer sendTo) {

		ImageOperation io = new ImageOperation();
		List<ImageInfo> imageInfoList = io.getListOfImages(to, from);

		for (int i = 0; i < imageInfoList.size(); i++) {
			ImageInfo info = imageInfoList.get(i);
			if (info != null) {
				MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
				mhb.setOriginator(HeartbeatManager.conf.getNodeId());
				mhb.setTime(System.currentTimeMillis());
				mhb.setSecurityCode(-999);

				PayLoad.Builder p = PayLoad.newBuilder();

				p.setCaption(info.getCaption());
				p.setData(ByteString.copyFrom(info.getImage()));
				p.setIndex(info.getIndex());
				// TODO token must be known between nodes

				Management.Builder b = Management.newBuilder();
				b.setHeader(mhb.build());
				b.setPayload(p.build());

				System.out.println("Updating Node " + sendTo);
				System.out.println("DATA index: " + from
						+ " sent. Data contains title " + info.getCaption());

				ConnectionManager.sendTo(b.build(), sendTo);
			}
		}
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

	private void sendToMyNodes(Management mgmt) {

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(HeartbeatManager.conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		PayLoad.Builder p = PayLoad.newBuilder();
		p.setCaption(mgmt.getPayload().getCaption());
		p.setData(mgmt.getPayload().getData());

		// TODO token must be known between nodes

		Management.Builder b = Management.newBuilder();
		b.setHeader(mhb.build());
		b.setPayload(p.build());
		System.out.println("SENT TO ALL NODES");
		ConnectionManager.broadcast(b.build());

	}

	/** election listener implementation */
	@Override
	public void concludeWith(boolean success, Integer leaderID) {
		if (success) {
			logger.info("----> the leader is " + leaderID);
			this.leaderNode = leaderID;
			electionStateFlag = true;
			HeartbeatManager.setIsLeader(true);
			LogManager.getInstance().setTerm(ElectionManager.getInstance().getTerm());
		}

		election.clear();
	}

	private void respondToWhoIsTheLeader(Management mgmt) {
		logger.info("Node " + conf.getNodeId() + " is replying to "
				+ mgmt.getHeader().getOriginator()
				+ "'s request who the leader is. Its Node " + this.leaderNode);

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(electionCycle);

		if (this.leaderNode != null) {
			elb.setAction(ElectAction.THELEADERIS);
		} else
			elb.setAction(ElectAction.DECLAREVOID);

		elb.setDesc("Node " + this.leaderNode + " is the leader");
		elb.setCandidateId(this.leaderNode == null ? -1 : this.leaderNode);
		elb.setExpires(-1);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it to the requester
		// logger.info("Election started by node " + conf.getNodeId());
		try {

			ConnectionManager.getConnection(mgmt.getHeader().getOriginator(),
					true).write(mb.build());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void askWhoIsTheLeader() {
		logger.info("Node " + conf.getNodeId() + " is searching for the leader");

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(-1);
		elb.setAction(ElectAction.WHOISTHELEADER);
		elb.setDesc("Node " + this.leaderNode + " is asking who the leader is");
		elb.setCandidateId(-1);
		elb.setExpires(-1);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it to the requester
		// ConnectionManager.getConnection(mgmt.getHeader().getOriginator(),
		// true).write(mb.build());
		// ConnectionManager.broadcast(mb.build());
	}

	private Election electionInstance() {
		if (election == null) {
			synchronized (syncPt) {
				if (election != null)
					return election;

				// new election
				String clazz = ElectionManager.conf.getElectionImplementation();

				// if an election instance already existed, this would
				// override the current election
				try {
					election = (Election) Beans.instantiate(this.getClass()
							.getClassLoader(), clazz);
					election.setNodeId(conf.getNodeId());
					election.setListener(this);
					election.setLength(conf.getAdjacent().getAdjacentNodes()
							.size());

					// this sucks - bad coding here! should use configuration
					// properties
					if (election instanceof FloodMaxElection) {
						logger.warn("Node " + conf.getNodeId()
								+ " setting max hops to arbitrary value (4)");
						((FloodMaxElection) election).setMaxHops(4);
					}

				} catch (Exception e) {
					logger.error("Failed to create " + clazz, e);
				}
			}
		}

		return election;

	}
}
