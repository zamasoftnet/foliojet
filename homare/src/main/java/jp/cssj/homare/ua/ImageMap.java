package jp.cssj.homare.ua;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.net.URI;
import java.util.ArrayList;

import jp.cssj.homare.ua.ImageMap.Area;

public class ImageMap extends ArrayList<Area> {
	private static final long serialVersionUID = 1L;

	public static class Area {
		public final URI href;
		public final Shape shape;
		
		public Area(final Shape shape, final URI href) {
			this.href = href;
			this.shape = shape;
		}
		
		public String toString() {
			StringBuffer buff = new StringBuffer();
			buff.append("href="+this.href);
			buff.append(";shape="+this.shape);
			return buff.toString();
		}
	}
	
	public ImageMap getTransformedImageMap(AffineTransform t) {
		ImageMap im = new ImageMap();
		for(Area area : this) {
			im.add(new Area(t.createTransformedShape(area.shape), area.href));
		}
		return im;
	}
}
