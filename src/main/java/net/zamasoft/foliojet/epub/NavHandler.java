package net.zamasoft.foliojet.epub;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class NavHandler extends DefaultHandler {
	final Toc toc = new Toc();
	private StringBuilder textBuff = null;
	private final List<List<NavPoint>> stack = new ArrayList<List<NavPoint>>();
	private final Map<String, Item> fullPathToItem;
	private final URI base;
	private int navDepth = 0;

	public NavHandler(Contents contents) {
		this.fullPathToItem = contents.fullPathToItem;
		this.base = URI.create(contents.toc.fullPath);
		this.stack.add(new ArrayList<NavPoint>());
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (this.navDepth != 0) {
			++this.navDepth;
		}
		if (uri.equals(EPubFile.XHTML_NS)) {
			if (this.navDepth <= 0) {
				if (lName.equals("nav")) {
					String type = atts.getValue(EPubFile.OPS_URI, "type");
					if ("toc".equals(type)) {
						this.navDepth = 1;
					}
				} else if (lName.equals("title")) {
					this.textBuff = new StringBuilder();
				}
			} else {
				if (lName.equals("a")) {
					String href = atts.getValue("href");
					if (href != null) {
						this.textBuff = new StringBuilder();
						List<NavPoint> list = this.stack.get(this.stack.size() - 2);
						NavPoint navPoint = list.get(list.size() - 1);
						navPoint.uri = this.base.resolve(href);
						navPoint.item = (Item) this.fullPathToItem.get(navPoint.uri.getPath());
					}
				} else if (lName.equals("li")) {
					NavPoint navPoint = new NavPoint();
					List<NavPoint> list = this.stack.get(this.stack.size() - 1);
					list.add(navPoint);
					this.stack.add(new ArrayList<NavPoint>());
				}
			}
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.textBuff != null) {
			this.textBuff.append(ch, off, len);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (uri.equals(EPubFile.XHTML_NS)) {
			if (this.navDepth <= 0) {
				if (lName.equals("title")) {
					this.toc.docTitle = this.textBuff.toString();
					this.textBuff = null;
				}
			} else {
				if (lName.equals("li")) {
					List<NavPoint> list = this.stack.get(this.stack.size() - 2);
					NavPoint navPoint = list.get(list.size() - 1);
					list = this.stack.remove(this.stack.size() - 1);
					navPoint.children = list.toArray(new NavPoint[list.size()]);
				} else if (this.textBuff == null) {
					// ignore
				} else if (lName.equals("a")) {
					List<NavPoint> list = this.stack.get(this.stack.size() - 2);
					NavPoint navPoint = list.get(list.size() - 1);
					navPoint.label = this.textBuff.toString();
					this.textBuff = null;
				}
			}
		}
		if (this.navDepth != 0) {
			--this.navDepth;
		}
	}

	public Toc getToc() {
		List<NavPoint> list = this.stack.get(this.stack.size() - 1);
		this.toc.navPoints = list.toArray(new NavPoint[list.size()]);
		return this.toc;
	}
}
