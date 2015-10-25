package poke.server.conf;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "adjacent")
@XmlAccessorType(XmlAccessType.FIELD)
public class NodeDesc {
	private int nodeId;
	private String nodeName;
	private String host;
	private int port;
	private int mgmtPort;
	private int imgPort;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getMgmtPort() {
		return mgmtPort;
	}

	public void setMgmtPort(int mgmtPort) {
		this.mgmtPort = mgmtPort;
	}
	
	public int getImgPort() {
		return imgPort;
	}

	public void setImgPort(int imgPort) {
		this.imgPort = imgPort;
	}

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

}
