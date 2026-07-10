package net.zamasoft.foliojet.epub.util;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.epub.EPubFile;
import net.zamasoft.foliojet.epub.Item;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

public class WritingModeHandler extends XMLFilterImpl {
	boolean pre;
	final boolean vertical;
	List<Attributes> attStack = new ArrayList<Attributes>();
	final AttributesImpl attsi = new AttributesImpl();
	final Item item;

	public WritingModeHandler(ContentHandler thandler, Item item, boolean vertical) {
		super.setContentHandler(thandler);
		this.item = item;
		this.vertical = vertical;
	}

	public WritingModeHandler(ContentHandler thandler, boolean vertical) {
		this(thandler, null, vertical);
	}

	public WritingModeHandler(XMLReader reader, Item item, boolean vertical) {
		super(reader);
		this.item = item;
		this.vertical = vertical;
	}

	public WritingModeHandler(XMLReader reader, boolean vertical) {
		this(reader, null, vertical);
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.vertical && !this.pre) {
			WritingModeHelper.characters(ch, off, len, this.getContentHandler());
			return;
		}
		super.characters(ch, off, len);
	}

	private static boolean pre(String lName, Attributes atts) {
		String clazz = atts.getValue("class");
		if (lName.equals("style") || lName.equals("script")) {
			return true;
		}
		if (clazz == null) {
			return false;
		}
		if (clazz.indexOf("tcy") != -1 || clazz.indexOf("pre") != -1) {
			return true;
		}
		return false;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		boolean inBody = lName.equals("body");
		if (inBody) {
			String type;
			if (this.item != null && this.item.guide != null && this.item.guide.type != null) {
				type = this.item.guide.type;
			} else {
				type = "text";
			}
			this.attsi.setAttributes(atts);
			atts = this.attsi;
			int classIndex = this.attsi.getIndex("", "class");
			StringBuilder classBuff;
			if (classIndex != -1) {
				classBuff = new StringBuilder(this.attsi.getValue(classIndex));
				this.attsi.removeAttribute(classIndex);
				classBuff.append(' ');
			} else {
				classBuff = new StringBuilder();
			}
			classBuff.append("x-epub-").append(type);
			this.attsi.addAttribute("", "class", "class", "CDATA", classBuff.toString());
		}
		this.attStack.add(new AttributesImpl(atts));
		if (pre(lName, atts)) {
			pre = true;
		}
		super.startElement(uri, lName, qName, atts);
		if (inBody) {
			this.attsi.clear();
			this.attsi.addAttribute("", "id", "id", "CDATA", "x-epub-left-nombre");
			super.startElement(EPubFile.XHTML_NS, "div", "div", this.attsi);
			super.endElement(EPubFile.XHTML_NS, "div", "div");
			this.attsi.clear();
			this.attsi.addAttribute("", "id", "id", "CDATA", "x-epub-right-nombre");
			super.startElement(EPubFile.XHTML_NS, "div", "div", this.attsi);
			super.endElement(EPubFile.XHTML_NS, "div", "div");
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		Attributes atts = this.attStack.remove(this.attStack.size() - 1);
		if (pre(lName, atts)) {
			pre = false;
		}
		super.endElement(uri, lName, qName);
	}
}
