package jp.cssj.homare.impl.ua.image;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;

import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.ua.UserAgentFactory;

public class ImageUserAgentFactory implements UserAgentFactory {
	public boolean match(String key) {
		String[] mimeTypes = ImageIO.getWriterMIMETypes();
		for (int i = 0; i < mimeTypes.length; ++i) {
			if (mimeTypes[i].equals(key)) {
				return true;
			}
		}
		return false;
	}

	public Iterator<Type> types() {
		List<Type> list = new ArrayList<Type>();
		String[] mimeTypes = ImageIO.getWriterMIMETypes();
		Collection<ImageWriterSpi> shown = new HashSet<ImageWriterSpi>();
		for (int i = 0; i < mimeTypes.length; ++i) {
			String mimeType = mimeTypes[i];
			ImageWriter w = (ImageWriter) ImageIO.getImageWritersByMIMEType(mimeType).next();
			ImageWriterSpi spi = w.getOriginatingProvider();
			if (shown.contains(spi)) {
				continue;
			}
			shown.add(spi);
			String name = spi.getFormatNames()[0].toUpperCase();
			String suffix = spi.getFileSuffixes()[0];
			list.add(new Type(name, mimeType, suffix));
		}
		return list.iterator();
	}

	public UserAgent createUserAgent() {
		return new ImageUserAgent();
	}
}
