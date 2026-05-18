package net.zamasoft.foliojet.ua;

import java.io.IOException;

import net.zamasoft.foliojet.plugin.Plugin;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.image.Image;

public interface ImageLoader extends Plugin<Source> {
	public Image loadImage(UserAgent ua, Source source) throws IOException;
}
