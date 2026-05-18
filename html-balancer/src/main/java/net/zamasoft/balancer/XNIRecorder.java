package net.zamasoft.balancer;

import java.util.ArrayList;
import java.util.List;

import org.apache.xerces.util.XMLAttributesImpl;
import org.apache.xerces.util.XMLResourceIdentifierImpl;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLDocumentHandler;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;

/**
 * SAXEventインスタンスをイベントごとに生成します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XNIRecorder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class XNIRecorder {
	/**
	 * SAXイベントを保持し、再現します。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: XNIRecorder.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	protected static interface XNIEvent {
		/**
		 * イベントを実行します。
		 * 
		 * @param handler
		 */
		public void doEvent(XMLDocumentHandler handler);
	}

	protected List<XNIEvent> events = new ArrayList<XNIEvent>();

	private int[] pointer = new int[] { -1, -1, -1, -1, -1, };

	private int[] elementLevel = new int[] { 0, 0, 0, 0, 0, };

	private int level = 0;

	private boolean recording = false;

	public void mark() {
		int pointer;
		if (this.level <= 0) {
			pointer = 0;
		} else {
			pointer = this.pointer[this.level - 1] + 1;
		}
		this.pointer[this.level] = pointer;
		this.recording = true;
	}

	public int elementLevel() {
		if (this.level == 0) {
			return 0;
		}
		return this.elementLevel[this.level - 1];
	}

	public void refeed(XMLDocumentHandler handler) {
		this.recording = false;
		int level = this.level;
		int limit = (level == 0) ? this.events.size() : this.pointer[level - 1];
		++this.level;
		if (this.pointer.length <= this.level) {
			int[] pointer = new int[this.level * 3 / 2];
			int[] elementLevel = new int[this.level * 3 / 2];
			System.arraycopy(this.pointer, 0, pointer, 0, this.pointer.length);
			for (int i = this.level; i < pointer.length; ++i) {
				pointer[i] = -1;
			}
			System.arraycopy(this.elementLevel, 0, elementLevel, 0, this.elementLevel.length);
		}
		for (; this.pointer[level] < limit; ++this.pointer[level]) {
			int pointer = this.pointer[level];
			XNIEvent event = (XNIEvent) this.events.get(pointer);
			event.doEvent(handler);
			if (this.pointer[this.level] == -1 || pointer < this.pointer[this.level]) {
				this.events.set(pointer, null);
			}
		}
		--this.level;
		this.pointer[level] = -1;
		this.elementLevel[level] = 0;
		if (level == 0) {
			this.events.clear();
		}
	}

	public boolean isRecording() {
		return this.recording;
	}

	public boolean isEmpty() {
		return this.events.isEmpty();
	}

	public void characters(XMLString text, Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		char[] chars = new char[text.length];
		System.arraycopy(text.ch, text.offset, chars, 0, text.length);
		final XMLString text_ = new XMLString(chars, 0, chars.length);
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.characters(text_, augs_);
			}
		};
		this.events.add(event);
	}

	public void comment(XMLString text, Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		char[] chars = new char[text.length];
		System.arraycopy(text.ch, text.offset, chars, 0, text.length);
		final XMLString text_ = new XMLString(chars, 0, chars.length);
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.comment(text_, augs_);
			}
		};
		this.events.add(event);
	}

	public void endCDATA(Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.endCDATA(augs_);
			}
		};
		this.events.add(event);
	}

	public void endElement(QName element, Augmentations augs) {
		--this.elementLevel[this.level];
		if (this.level > 0) {
			return;
		}
		final QName element_ = new QName(element);
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.endElement(element_, augs_);
			}
		};
		this.events.add(event);
	}

	public void endGeneralEntity(final String name, Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.endGeneralEntity(name, augs_);
			}
		};
		this.events.add(event);
	}

	public void processingInstruction(final String target, XMLString data, Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		char[] chars = new char[data.length];
		System.arraycopy(data.ch, data.offset, chars, 0, data.length);
		final XMLString data_ = new XMLString(chars, 0, chars.length);
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.processingInstruction(target, data_, augs_);
			}
		};
		this.events.add(event);
	}

	public void startCDATA(Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.startCDATA(augs_);
			}
		};
		this.events.add(event);
	}

	public void startElement(QName element, XMLAttributes attributes, Augmentations augs) {
		++this.elementLevel[this.level];
		if (this.level > 0) {
			return;
		}
		final QName element_ = new QName(element);
		final XMLAttributes attributes_;
		if (attributes != null) {
			int length = attributes.getLength();
			if (length > 0) {
				attributes_ = new XMLAttributesImpl();
				QName aqname = new QName();
				for (int i = 0; i < length; i++) {
					attributes.getName(i, aqname);
					String type = attributes.getType(i);
					String value = attributes.getValue(i);
					String nonNormalizedValue = attributes.getNonNormalizedValue(i);
					boolean specified = attributes.isSpecified(i);
					attributes_.addAttribute(aqname, type, value);
					attributes_.setNonNormalizedValue(i, nonNormalizedValue);
					attributes_.setSpecified(i, specified);
				}
			} else {
				attributes_ = null;
			}
		} else {
			attributes_ = null;
		}
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.startElement(element_, attributes_, augs_);
			}
		};
		this.events.add(event);
	}

	public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		final QName element_ = new QName(element);
		final XMLAttributes attributes_;
		if (attributes != null) {
			int length = attributes.getLength();
			if (length > 0) {
				attributes_ = new XMLAttributesImpl();
				QName aqname = new QName();
				for (int i = 0; i < length; i++) {
					attributes.getName(i, aqname);
					String type = attributes.getType(i);
					String value = attributes.getValue(i);
					String nonNormalizedValue = attributes.getNonNormalizedValue(i);
					boolean specified = attributes.isSpecified(i);
					attributes_.addAttribute(aqname, type, value);
					attributes_.setNonNormalizedValue(i, nonNormalizedValue);
					attributes_.setSpecified(i, specified);
				}
			} else {
				attributes_ = null;
			}
		} else {
			attributes_ = null;
		}
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.emptyElement(element_, attributes_, augs_);
			}
		};
		this.events.add(event);
	}

	public void startGeneralEntity(final String name, XMLResourceIdentifier identifier, final String encoding,
			Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		final XMLResourceIdentifier identifier_ = new XMLResourceIdentifierImpl(identifier.getPublicId(),
				identifier.getLiteralSystemId(), identifier.getBaseSystemId(), identifier.getExpandedSystemId());
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.startGeneralEntity(name, identifier_, encoding, augs_);
			}
		};
		this.events.add(event);
	}

	public void textDecl(final String version, final String encoding, Augmentations augs) {
		if (this.level > 0) {
			return;
		}
		final Augmentations augs_ = augs == null ? null : new AugmentationsImpl(augs);
		XNIEvent event = new XNIEvent() {
			public void doEvent(XMLDocumentHandler handler) {
				handler.textDecl(version, encoding, augs_);
			}
		};
		this.events.add(event);
	}
}
