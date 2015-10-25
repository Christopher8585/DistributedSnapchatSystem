package Testing;

import poke.server.storage.jpa.ImageInfo;
import poke.server.storage.jpa.ImageOperation;

public class TestJPA {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ImageOperation iop = new ImageOperation();
		ImageInfo i=new ImageInfo(0, 
				null, "test");
		iop.doAction(i);
	}

}
