package poke.server.managers;

import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Image.Request;
import poke.core.Mgmt.Management;
import poke.server.conf.ServerConf;
import poke.server.election.LogEntry;
import poke.server.storage.jpa.ImageOperation;

public class LogManager{
	protected static Logger logger = LoggerFactory.getLogger("logManager");
	protected static AtomicReference<LogManager> instance = new AtomicReference<LogManager>();
	
	ConcurrentHashMap<Integer, LogEntry> logEntries = new ConcurrentHashMap<Integer, LogEntry>();
	
	static Integer Term=-1;//Get from the DB  ###
	static Integer Index=-1;//Get from the DB  ###
	static Integer PrevTerm=-1;//Get from the DB  ### 
	static Integer PrevIndex=-1;//Get from the DB  ### 
	
	boolean forever = true;
	static ServerConf conf;
	
	public static LogManager initManager(ServerConf conf) {
		LogManager.conf = conf;
		instance.compareAndSet(null, new LogManager());
		
		//Load values from DB Here
		
		Index=ImageOperation.getMaxIndex();//LOADING LATEST INDEX VALUE STORED IN DB
		System.out.println("-------------------LOG INDEX "+Index);
		return instance.get();
	}
	
	public Integer getTerm(){
		return Term;
	}
	public Integer setTerm(Integer t){
		return Term=t;
	}
	public Integer getIndex(){
		return Index;
	}
	public Integer getPrevTerm(){
		return PrevTerm;
	}
	public Integer getPrevIndex(){
		return PrevIndex;
	}
	

	public static LogManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}


	protected LogManager() {
		
	}
	
	public synchronized Integer processRequest(Request req) {
		
		PrevIndex=Index++;
		PrevTerm=Term;
		LogEntry le=new LogEntry(Term,Index,PrevIndex,req.getHeader().getCaption());
		logEntries.put(Index,le);
		//System.out.println(logEntries.get(Index));
		printLog();
		//Truncate log
		checkLogSizeAndTruncate();
		return Index;
	}
	
	
	public synchronized Integer processRequest(Management mgmt) {
		
		PrevIndex=Index++;
		PrevTerm=Term;
		LogEntry le=new LogEntry(Term,Index,PrevIndex,mgmt.getPayload().getCaption());
		logEntries.put(Index,le);
		printLog();
		//Truncate log
		checkLogSizeAndTruncate();
		return Index;
	}
	//
	public synchronized Integer processRequest(Management mgmt, Integer index) {

		LogEntry le=new LogEntry(Term,index,index-1,mgmt.getPayload().getCaption());
		logEntries.put(index,le);
		printLog();
		//Updating Index
		if(index>Index){
			Index=index;
			PrevIndex=Index-1;
		}
		//Truncate log
		checkLogSizeAndTruncate();
		return index;
	}
	
	public boolean checkLogSizeAndTruncate(){
		//When log size reaches 1000 entries, we truncate it. To avoid max heap limit
		//Log entries are maintained in the DB..so could be retrieved 
		if(logEntries.size()>1000){
			int midIndex=logEntries.get(Index).getIndex()/2;//Removes half of the log entries(500 entries)
			LogEntry le=logEntries.get(midIndex);
			while(le!=null){
				System.out.println("removing " + le.getIndex());
				logEntries.remove(le.getIndex());
				le=logEntries.get(le.getPrevIndex());
			}
			return true;
		}
		return false;	
	}
	
	public void printLog(){

		System.out
				.println("\n\n*********************************************************");
		System.out.println(" LOG DATA ");

		for (LogEntry le : logEntries.values()){
			System.out.println(le.toString() );
		}
		System.out
				.println("*********************************************************\n\n");

	}
	
	
}
