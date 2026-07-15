package net.zamasoft.foliojet.xml.util;

import net.zamasoft.foliojet.xml.XMLHandler;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * SAXイベントを保持・再現するためのイベント生成ユーティリティです。
 *
 * @author MIYABE Tatsuhiko
 */
public final class SAXEventRecorder {
	private SAXEventRecorder() {
		// utility
	}

	/**
	 * 保持されたSAXイベントです。
	 */
	@FunctionalInterface
	public interface SAXEvent {
		public void doEvent(ContentHandler handler) throws SAXException;
	}

	public static SAXEvent startPrefixMapping(String prefix, String uri) {
		assert prefix != null;
		assert uri != null;
		return handler -> handler.startPrefixMapping(prefix, uri);
	}

	public static SAXEvent endPrefixMapping(String prefix) {
		assert prefix != null;
		return handler -> handler.endPrefixMapping(prefix);
	}

	public static SAXEvent startElement(String uri, String lName, String qName, Attributes atts) {
		final Attributes attsc = new AttributesImpl(atts);
		return handler -> handler.startElement(uri, lName, qName, attsc);
	}

	public static SAXEvent endElement(String uri, String lName, String qName) {
		return handler -> handler.endElement(uri, lName, qName);
	}

	public static SAXEvent characters(char[] ch, int off, int len) {
		// chはバッファなので後で変更される可能性がある
		final char[] fch = new char[len];
		System.arraycopy(ch, off, fch, 0, len);
		return handler -> handler.characters(fch, 0, fch.length);
	}

	public static SAXEvent ignorableWhitespace(char[] ch, int off, int len) {
		// chはバッファなので後で変更される可能性がある
		final char[] fch = new char[len];
		System.arraycopy(ch, off, fch, 0, len);
		return handler -> handler.ignorableWhitespace(fch, 0, fch.length);
	}

	public static SAXEvent skippedEntity(String entity) {
		return handler -> handler.skippedEntity(entity);
	}

	public static SAXEvent setDocumentLocator(Locator locator) {
		return handler -> handler.setDocumentLocator(locator);
	}

	public static SAXEvent processingInstruction(String target, String data) {
		return handler -> handler.processingInstruction(target, data);
	}

	public static SAXEvent startCDATA() {
		return handler -> ((XMLHandler) handler).startCDATA();
	}

	public static SAXEvent endCDATA() {
		return handler -> ((XMLHandler) handler).endCDATA();
	}

	public static SAXEvent startDTD(String name, String publicId, String systemId) {
		return handler -> ((XMLHandler) handler).startDTD(name, publicId, systemId);
	}

	public static SAXEvent endDTD() {
		return handler -> ((XMLHandler) handler).endDTD();
	}

	public static SAXEvent startEntity(String name) {
		return handler -> ((XMLHandler) handler).startEntity(name);
	}

	public static SAXEvent endEntity(String name) {
		return handler -> ((XMLHandler) handler).endEntity(name);
	}

	public static SAXEvent comment(char[] ch, int off, int len) {
		// chはバッファなので後で変更される可能性がある
		final char[] fch = new char[len];
		System.arraycopy(ch, off, fch, 0, len);
		return handler -> ((XMLHandler) handler).comment(fch, 0, fch.length);
	}
}
