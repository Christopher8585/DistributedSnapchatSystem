/*
 * copyright 2012, gash
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
package poke.server.management;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.PayLoad;
import poke.server.election.ElectionIDGenerator;
import poke.server.management.ManagementQueue.ManagementQueueEntry;
import poke.server.managers.ConnectionManager;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.LogManager;
import poke.server.managers.NetworkManager;
import poke.server.storage.jpa.ImageInfo;
import poke.server.storage.jpa.ImageOperation;

import javax.persistence.Persistence;

/**
 * The inbound management worker is the cortex for all work related to the
 * Health and Status (H&S) of the node.
 * 
 * Example work includes processing job bidding, elections, network connectivity
 * building. An instance of this worker is blocked on the socket listening for
 * events. If you want to approximate a timer, executes on a consistent interval
 * (e.g., polling, spin-lock), you will have to implement a thread that injects
 * events into this worker's queue.
 * 
 * HB requests to this node are NOT processed here. Nodes making a request to
 * receive heartbeats are in essence requesting to establish an edge (comm)
 * between two nodes. On failure, the connecter must initiate a reconnect - to
 * produce the heartbeatMgr.
 * 
 * On loss of connection: When a connection is lost, the emitter will not try to
 * establish the connection. The edge associated with the lost node is marked
 * failed and all outbound (enqueued) messages are dropped (TBD as we could
 * delay this action to allow the node to detect and re-establish the
 * connection).
 * 
 * Connections are bi-directional (reads and writes) at this time.
 * 
 * @author gash
 * 
 */
public class InboundMgmtWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("management");
	public static boolean processing =true;
	
	
	int workerId;
	boolean forever = true;

	public InboundMgmtWorker(ThreadGroup tgrp, int workerId) {
		super(tgrp, "inbound-mgmt-" + workerId);
		this.workerId = workerId;

		if (ManagementQueue.outbound == null)
			throw new RuntimeException("connection worker detected null queue");
	}

	
	@Override
	public void run() {
		while (true) {
			if (!forever && ManagementQueue.inbound.size() == 0)
				break;

			try {

				//Created for faster processing of bulk requests
				if(ManagementQueue.inbound.size()==0)
					processing=true;
					
				// block until a message is enqueued
				ManagementQueueEntry msg = ManagementQueue.inbound.take();		
				
				if (logger.isDebugEnabled())
					logger.debug("Inbound management message received");

				Management mgmt = (Management) msg.req;
				
				if (mgmt.hasBeat()) {

					HeartbeatManager.getInstance().processRequest(mgmt);
					System.out.println("Term of node "+mgmt.getHeader().getOriginator()+" is" +mgmt.getBeat().getTerm() +" and of this node is " + ElectionManager.getInstance().getTerm());
					//If heartbeat sent by leader then reset ping
				
					if(mgmt.getHeader().getOriginator()==(ElectionManager.getInstance().whoIsTheLeader()==null?-1:ElectionManager.getInstance().whoIsTheLeader())
							&& (mgmt.getBeat().getTerm()>= ElectionManager.getInstance().getTerm())
							){
						ElectionManager.getInstance().leaderNode=mgmt.getBeat().getLeaderNode();
						HeartbeatManager.getInstance().setLeaderExists(true);
						ElectionManager.electionStateFlag=true;// Reset the power to vote(Only needed once-Redundant)
						System.out.println("GOT THE MSG FROM THE LEADER-------- RESETTING BEAT--------------");
						HeartbeatManager.setIsLeader(false);// ###
						//###NEW
						ElectionManager.getInstance().setTerm(mgmt.getBeat().getTerm());
						ElectionIDGenerator.setMasterID(mgmt.getBeat().getTerm());
						LogManager.getInstance().setTerm(mgmt.getBeat().getTerm());
						
						continue;
					}
					
					//if(mgmt.getBeat().getLeaderNode()!=(ElectionManager.getInstance().whoIsTheLeader()==null?-1:ElectionManager.getInstance().whoIsTheLeader())&&
							if(mgmt.getBeat().getLeaderNode()!=-1 &&
							 mgmt.getBeat().getTerm()> ElectionManager.getInstance().getTerm()
							){
						//New node updating its value
						System.out.println(" -------RESETTING New LEADER BEAT--------------");
						ElectionManager.getInstance().leaderNode=mgmt.getBeat().getLeaderNode();
						HeartbeatManager.getInstance().setLeaderExists(true);
						HeartbeatManager.setIsLeader(false);// ###
						ElectionManager.electionStateFlag=true;
						//###NEW
						ElectionManager.getInstance().setTerm(mgmt.getBeat().getTerm());
						LogManager.getInstance().setTerm(mgmt.getBeat().getTerm());
						
						continue;
					}
					
					System.out.println(ElectionManager.getInstance().whoIsTheLeader()+" "+mgmt.getBeat().getLeaderNode() +"  "
							+ mgmt.getHeader().getOriginator());
					ElectionManager.getInstance().assessCurrentState(mgmt);

				} else if (mgmt.hasElection()) {
					ElectionManager.getInstance().processRequest(mgmt);
				} else if (mgmt.hasGraph()) {
					NetworkManager.getInstance().processRequest(mgmt, msg.channel);
				} else if(mgmt.hasPayload()){
					if(ElectionManager.getInstance().whoIsTheLeader()!=null || ElectionManager.getInstance().whoIsTheLeader()!=-1){
						if(ElectionManager.getInstance().whoIsTheLeader()==HeartbeatManager.conf.getNodeId()){
							System.out.println("IMG received from a node.. sending to other nodes" );
							
							//SAVE IN DB
							//System.out.println(Arrays.toString(mgmt.getPayload().getData().toByteArray()));
							System.out.println("------------CAPTION:"+  mgmt.getPayload().getCaption());
							
							//SAVES INSIDE LOG and DB..THEN SENDS THE REQ TO OTHER NODES
							sendToMyNodes(mgmt);
					

						}
						else{
							//Check if the log state matches with the Leaders log.

							//SAVE IN DB
							LogManager.getInstance().processRequest(mgmt,mgmt.getPayload().getIndex());
							//System.out.println(Arrays.toString(mgmt.getPayload().getData().toByteArray()));
							ImageOperation iop = new ImageOperation();
							ImageInfo i=new ImageInfo(mgmt.getPayload().getIndex(), 
									mgmt.getPayload().getData().toByteArray(), mgmt.getPayload().getCaption());
							iop.doAction(i);
							//System.out.println(Arrays.toString(mgmt.getPayload().getData().toByteArray()));
							System.out.println("------------CAPTION:"+  mgmt.getPayload().getCaption());
							HeartbeatManager.getInstance().setLeaderExists(true);
							processing=false;
						}
					}
					
				}		
				else
					logger.error("Unknown management message");


			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected processing failure, halting worker.", e);
				break;
			}
		}

		if (!forever) {
			logger.info("connection queue closing");
		}
	}
	
	private void sendToMyNodes(Management mgmt) {
		

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(HeartbeatManager.conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);
		
		PayLoad.Builder p=PayLoad.newBuilder();
		p.setCaption( mgmt.getPayload().getCaption());
		p.setData( mgmt.getPayload().getData());
		
		int index=LogManager.getInstance().processRequest(mgmt);
		p.setIndex(index);
		
		ImageOperation iop = new ImageOperation();
		ImageInfo i=new ImageInfo(index, 
				mgmt.getPayload().getData().toByteArray(), mgmt.getPayload().getCaption());
		iop.doAction(i);
		// TODO token must be known between nodes
		
		Management.Builder b = Management.newBuilder();
		b.setHeader(mhb.build());
		b.setPayload(p.build());
		System.out.println("SENT TO ALL NODES");
		ConnectionManager.broadcast(b.build());
		
	}

}
