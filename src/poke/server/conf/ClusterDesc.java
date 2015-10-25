package poke.server.conf;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "cluster")
@XmlAccessorType(XmlAccessType.FIELD)
public class ClusterDesc {
	private List<ClusterNode> clusterNodes;
	//private String clusterNo;

	public void addCluster(ClusterNode entry) {
		if (entry == null)
			return;
		else if (clusterNodes == null)
			clusterNodes = new ArrayList<ClusterNode>();

		clusterNodes.add(entry);
	}
	
	//public String getClusterNo() {
	//	return clusterNo;
	//}

	//public void setClusterNo(String name) {
	//	this.clusterNo = name;
	//}

	public ClusterNode findById(int id) {
		return clusterNodes.get(id);
	}

	public List<ClusterNode> getClusterNodes() {
		return clusterNodes;
	}

	public void setClusterNodes(List<ClusterNode> conf) {
		this.clusterNodes = conf;
	}

	@XmlRootElement(name = "entryCluster")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static final class ClusterNode {
		private int port;
		private String host;

		public ClusterNode() {
		}

		public ClusterNode(int id, String name) {
			this.port = id;
			this.host = name;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String name) {
			this.host = name;
		}

	}
}
