package net.zamasoft.foliojet.epub;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class NcxHandler extends DefaultHandler {
	final Toc toc = new Toc();
	private StringBuilder textBuff = null;
	private boolean inText = false;
	private final List<List<NavPoint>> stack = new ArrayList<List<NavPoint>>();
	private final Map<String, Item> fullPathToItem;
	private final URI base;

	public NcxHandler(Contents contents) {
		this.fullPathToItem = contents.fullPathToItem;
		this.base = URI.create(contents.toc.fullPath);
		this.stack.add(new ArrayList<NavPoint>());
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (uri.equals(EPubFile.NCX_URI)) {
			if (lName.equals("text")) {
				this.textBuff = new StringBuilder();
				this.inText = true;
			} else if (lName.equals("navPoint")) {
				NavPoint navPoint = new NavPoint();
				navPoint.id = atts.getValue("id");
				navPoint.playOrder = Integer.parseInt(atts.getValue("playOrder"));
				List<NavPoint> list = this.stack.get(this.stack.size() - 1);
				list.add(navPoint);
				this.stack.add(new ArrayList<NavPoint>());
			} else if (lName.equals("content")) {
				List<NavPoint> list = this.stack.get(this.stack.size() - 2);
				NavPoint navPoint = list.get(list.size() - 1);
				String src = atts.getValue("src");
				navPoint.uri = this.base.resolve(src);
				navPoint.item = (Item) this.fullPathToItem.get(navPoint.uri.getPath());
			}
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.inText) {
			this.textBuff.append(ch, off, len);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (uri.equals(EPubFile.NCX_URI)) {
			if (lName.equals("navPoint")) {
				List<NavPoint> list = this.stack.get(this.stack.size() - 2);
				NavPoint navPoint = list.get(list.size() - 1);
				list = this.stack.remove(this.stack.size() - 1);
				navPoint.children = list.toArray(new NavPoint[list.size()]);
				return;
			}
			if (this.textBuff == null) {
				return;
			}
			if (lName.equals("text")) {
				this.inText = false;
				return;
			} else if (lName.equals("docTitle") && this.toc != null) {
				this.toc.docTitle = this.textBuff.toString();
			} else if (lName.equals("navLabel")) {
				List<NavPoint> list = this.stack.get(this.stack.size() - 2);
				NavPoint navPoint = list.get(list.size() - 1);
				navPoint.label = this.textBuff.toString();
			} else {
				return;
			}
		} else {
			return;
		}
		this.textBuff = null;
	}

	public Toc getToc() {
		List<NavPoint> list = this.stack.get(this.stack.size() - 1);
		this.toc.navPoints = list.toArray(new NavPoint[list.size()]);
		return this.toc;
	}
}
