package jp.cssj.homare.xml.util;

import jp.cssj.homare.xml.XMLHandler;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * SAXEventインスタンスをイベントごとに生成します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: SAXEventRecorder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class SAXEventRecorder {
	/**
	 * SAXイベントを保持し、再現します。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: SAXEventRecorder.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static interface SAXEvent {
		/**
		 * イベントを実行します。
		 * 
		 * @param handler
		 */
		public void doEvent(ContentHandler handler) throws SAXException;
	}

	public static SAXEvent startPrefixMapping(final String prefix, final String uri) {
		assert prefix != null;
		assert uri != null;
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.startPrefixMapping(prefix, uri);
			}
		};
	}

	public static SAXEvent endPrefixMapping(final String prefix) {
		assert prefix != null;
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.endPrefixMapping(prefix);
			}
		};
	}

	public static SAXEvent startElement(final String uri, final String lName, final String qName, Attributes atts) {
		final Attributes attsc = new AttributesImpl(atts);
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.startElement(uri, lName, qName, attsc);
			}
		};
	}

	public static SAXEvent endElement(final String uri, final String lName, final String qName) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.endElement(uri, lName, qName);
			}
		};
	}

	public static SAXEvent characters(char[] ch, int off, int len) {
		final char[] fch = new char[len];
		// chはバッファなので後で変更される可能性がある
		System.arraycopy(ch, off, fch, 0, len);
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.characters(fch, 0, fch.length);
			}
		};
	}

	public static SAXEvent ignorableWhitespace(char[] ch, int off, int len) {
		// chはバッファなので後で変更される可能性がある
		final char[] fch = new char[len];
		System.arraycopy(ch, off, fch, 0, len);
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.ignorableWhitespace(fch, 0, fch.length);
			}
		};
	}

	public static SAXEvent skippedEntity(final String entity) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.skippedEntity(entity);
			}
		};
	}

	public static SAXEvent setDocumentLocator(final Locator locator) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.setDocumentLocator(locator);
			}
		};
	}

	public static SAXEvent processingInstruction(final String target, final String data) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				handler.processingInstruction(target, data);
			}
		};
	}

	public static SAXEvent startCDATA() {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).startCDATA();
			}
		};
	}

	public static SAXEvent endCDATA() {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).endCDATA();
			}
		};
	}

	public static SAXEvent startDTD(final String name, final String publicId, final String systemId) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).startDTD(name, publicId, systemId);
			}
		};
	}

	public static SAXEvent endDTD() {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).endDTD();
			}
		};
	}

	public static SAXEvent startEntity(final String name) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).startEntity(name);
			}
		};
	}

	public static SAXEvent endEntity(final String name) {
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).endEntity(name);
			}
		};
	}

	public static SAXEvent comment(char[] ch, int off, int len) {
		final char[] fch = new char[len];
		// chはバッファなので後で変更される可能性がある
		System.arraycopy(ch, off, fch, 0, len);
		return new SAXEvent() {
			public void doEvent(ContentHandler handler) throws SAXException {
				((XMLHandler) handler).comment(fch, 0, fch.length);
			}
		};
	}
}
