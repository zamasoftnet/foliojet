package net.zamasoft.foliojet.epub.util;


import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Wraps runs of digits in vertical text for tate-chu-yoko (the experimental
 * {@code x.net.zamasoft.foliojet.formatter.impl.epub.replace-numbers}).
 *
 * <p>
 * It used to insert two empty {@code div} elements at the start of every body and add an {@code x-epub-<guide type>}
 * class to body. Nothing used them, and they broke the author's {@code :first-child} selectors and drew the author's
 * {@code div} borders and margins at the start of every item; removed on 2026-10-08.
 * </p>
 */
public class WritingModeHandler extends XMLFilterImpl {
	boolean pre;
	final boolean vertical;
	List<Attributes> attStack = new ArrayList<Attributes>();

	public WritingModeHandler(ContentHandler thandler, boolean vertical) {
		super.setContentHandler(thandler);
		this.vertical = vertical;
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
		this.attStack.add(new AttributesImpl(atts));
		if (pre(lName, atts)) {
			pre = true;
		}
		super.startElement(uri, lName, qName, atts);
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		Attributes atts = this.attStack.remove(this.attStack.size() - 1);
		if (pre(lName, atts)) {
			pre = false;
		}
		super.endElement(uri, lName, qName);
	}
}
