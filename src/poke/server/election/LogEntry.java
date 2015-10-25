package poke.server.election;

public class LogEntry {
	int term;
	int index;
	int prevIndex;
	String title;

	public LogEntry(int t,int i,int pi,String title){
		this.term=t;
		this.index=i;
		this.prevIndex=pi;
		this.title=title;
	}
	
	public String getTitle(){
		return this.title;
	}
	
	public int getTerm(){
		return this.term;
	}
	
	public int getIndex(){
		return this.index;
	}
	
	public int getPrevIndex(){
		return this.prevIndex;
	}
	
	public String toString(){
		return "Index: "+ this.getIndex()+",  Title: "+this.getTitle() +
		",  Previous Index: "+ this.getPrevIndex()+",  Term: "+this.getTerm();
	}
}
