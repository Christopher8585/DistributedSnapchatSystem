package poke.server.storage.jpa;
import java.io.Serializable;

import javax.persistence.*;

@Entity
@Table(name="ImageInfo")

@NamedQueries({
	@NamedQuery(query = "Select i from ImageInfo i where i.index = :index", 
			name = "find imageInfo by index"),
	@NamedQuery(query = "Select i from ImageInfo i where i.index between :start and :end", 
			name = "range")
})
public class ImageInfo implements Serializable{

	private static final long serialVersionUID = 1L;

	public ImageInfo()
	{
		
	}
	
	public ImageInfo(int index, byte[] image, String caption)
	{
		this.index = index;
		this.image = image;
		this.caption = caption;
	}
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="Id")
	private Integer id;
	
	@Column
	private int index;
	
	@Column
	private byte[] image;
	
	@Column
	private String caption;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public byte[] getImage() {
		return image;
	}

	public void setImage(byte[] image) {
		this.image = image;
	}

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

}
