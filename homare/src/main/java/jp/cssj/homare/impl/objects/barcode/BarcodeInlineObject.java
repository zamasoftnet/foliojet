package jp.cssj.homare.impl.objects.barcode;

import java.io.IOException;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.krysalis.barcode4j.BarcodeClassResolver;
import org.krysalis.barcode4j.BarcodeGenerator;
import org.krysalis.barcode4j.BarcodeUtil;
import org.krysalis.barcode4j.DefaultBarcodeClassResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import jp.cssj.homare.css.InlineObject;
import jp.cssj.homare.impl.objects.barcode.isbn.ISBN;
import jp.cssj.homare.impl.objects.barcode.jp4scc.JP4SCC;
import jp.cssj.homare.impl.objects.barcode.qrcode.QRCode;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Barcode4JのXML記述からバーコード画像を生成します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id$
 */
public class BarcodeInlineObject extends DefaultHandler implements InlineObject {
	private static final BarcodeClassResolver RESOLVER = new DefaultBarcodeClassResolver() {
		{
			registerBarcodeClass("isbn", ISBN.class.getName());
			registerBarcodeClass("qrcode", QRCode.class.getName());
			registerBarcodeClass("japanpost", JP4SCC.class.getName());
		}
	};

	private BarcodeGenerator bg;

	private String message;

	private SAXConfigurationHandler sch;

	private int configDepth = 0;

	public Image getImage(UserAgent ua) throws IOException {
		return new BarcodeImage(ua, this.bg, this.message);
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (lName.equals("barcode")) {
			this.message = atts.getValue("message");
		} else if (configDepth == 0) {
			this.configDepth = 1;
			this.sch = new SAXConfigurationHandler();
			this.sch.startDocument();
		}
		if (this.configDepth >= 1) {
			this.sch.startElement("", lName, lName, atts);
			++this.configDepth;
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.configDepth >= 1) {
			this.sch.characters(ch, off, len);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (this.configDepth >= 1) {
			this.sch.endElement("", lName, lName);
			--this.configDepth;
			if (this.configDepth == 1) {
				this.configDepth = 0;
				this.sch.endDocument();
				Configuration cfg = this.sch.getConfiguration();
				try {
					this.bg = BarcodeUtil.createBarcodeGenerator(cfg, RESOLVER);
				} catch (Exception e) {
					throw new SAXException(e);
				}
			}
		}
	}
}
