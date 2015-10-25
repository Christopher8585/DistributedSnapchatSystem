package poke.server.storage.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.eclipse.persistence.config.PersistenceUnitProperties;

//EclipseLink JPA With PostgreSQL Example

public class ImageOperation {

	static EntityManagerFactory emf;
	static EntityManager em;

	// byte[] image;

	public ImageOperation() {
	}

	public ImageOperation(byte[] image) {
		// this.image = image;
	}

	public boolean doAction(ImageInfo i) {
		System.out.println("calling");
		Properties pros = new Properties();
		pros.setProperty(PersistenceUnitProperties.ECLIPSELINK_PERSISTENCE_XML,
				"/classes/META-INF/persistence.xml");
		emf = Persistence.createEntityManagerFactory("JPATest");
		em = emf.createEntityManager();

		boolean success = true;
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			try {
				Query query = em.createNamedQuery("find imageInfo by index");
				query.setParameter("index", i.getIndex());
				List<ImageInfo> list = query.getResultList();

				if (list.size() == 0)
					em.persist(i);
			} catch (Exception e) {
				System.out.println("Error: " + e);
			}
			tx.commit();

		} catch (Exception e) {
			success = false;
		}
		return success;
	}

	
	
	public static int getMaxIndex() {
		 int index=0;
		try {
			Properties pros = new Properties();
			pros.setProperty(PersistenceUnitProperties.ECLIPSELINK_PERSISTENCE_XML,
					"/classes/META-INF/persistence.xml");
			emf = Persistence.createEntityManagerFactory("JPATest");
			em = emf.createEntityManager();

			try {
				Query query = em.
						createQuery("Select MAX(i.index) from ImageInfo i");
				index=(Integer) query.getSingleResult();

			} catch (Exception e) {
				System.out.println("Error: " + e);
			}

		} catch (Exception e) {
			System.out.println(e);

		}
		return index;
	}
	
	public ImageInfo getImage(int index) {
		ImageInfo imageInfo=null;
		try {
			Properties pros = new Properties();
			pros.setProperty(PersistenceUnitProperties.ECLIPSELINK_PERSISTENCE_XML,
					"/classes/META-INF/persistence.xml");
			emf = Persistence.createEntityManagerFactory("JPATest");
			em = emf.createEntityManager();

			
			try {
				Query query = em.createNamedQuery("find imageInfo by index");
				query.setParameter("index",index);
				imageInfo = (ImageInfo) query.getSingleResult();

			} catch (Exception e) {
				System.out.println("Error: " + e);
			}

		} catch (Exception e) {
			System.out.println(e);

		}
		return imageInfo;
	}
	
	public List<ImageInfo> getListOfImages(int start, int end) {
		 List<ImageInfo>  imageInfo = new ArrayList<ImageInfo>();
		try {
			Properties pros = new Properties();
			pros.setProperty(PersistenceUnitProperties.ECLIPSELINK_PERSISTENCE_XML,
					"/classes/META-INF/persistence.xml");
			emf = Persistence.createEntityManagerFactory("JPATest");
			em = emf.createEntityManager();

			
			try {
				Query query = em.createNamedQuery("range");
				query.setParameter("start",start+1);
				
				//Quick fix for bulk request
				if(end-start>100)
					end=start+100;
				
				query.setParameter("end",end);
				imageInfo = query.getResultList();
			} catch (Exception e) {
				System.out.println("Error: " + e);
			}

		} catch (Exception e) {
			System.out.println(e);

		}
		return imageInfo;
	}
	
	

}