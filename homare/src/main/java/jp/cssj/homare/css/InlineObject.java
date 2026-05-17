package jp.cssj.homare.css;

import java.io.IOException;

import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;

import org.xml.sax.ContentHandler;

public interface InlineObject extends ContentHandler {
	public Image getImage(UserAgent ua) throws IOException;
}
