package net.zamasoft.foliojet.xml.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Constants;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.vocab.CSSJML;
import net.zamasoft.foliojet.xml.util.SAXEventRecorder;
import net.zamasoft.foliojet.xml.util.SAXEventRecorder.SAXEvent;
import net.zamasoft.foliojet.xml.util.XMLUtils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import net.zamasoft.foliojet.xml.vocab.XHTML;

public class XHTMLPreprocessFilter extends DefaultXMLHandlerFilter {
	private static final java.util.logging.Logger LOG = java.util.logging.Logger
			.getLogger(XHTMLPreprocessFilter.class.getName());

	/** The document's default styling method. */
	private String defaultStyleType = Constants.CSS_MIME_TYPE;

	private final UserAgent ua;

	private int dtdPos = 0;

	/**
	 * The events of the head, kept until the content starts ({@link #startBody}), so that the processing
	 * instructions made from the head (style sheets, document information) go out before the root element. Null
	 * once the content has started.
	 *
	 * <p>
	 * The content starts at the body element, at an element of another namespace, and, for a document without a
	 * body, at the first element or text that is not part of the head (see {@link #metadataDepth}) or at the end of
	 * the document. XHTML and XML input need not have a body ({@code <html><head/><div>...</div></html>}, or
	 * {@code <doc><p>...</p></doc>} with every element taken as XHTML): such a document used to be kept here
	 * whole and was never passed on, so the result was an empty page.
	 * </p>
	 */
	private List<SAXEventRecorder.SAXEvent> events = new ArrayList<SAXEventRecorder.SAXEvent>();

	/**
	 * While the head is kept: the number of open elements from a metadata element down, that is, of
	 * {@code head}, {@code title}, {@code meta}, {@code link}, {@code style}, {@code base}, {@code script} and of
	 * whatever is inside them. 0 outside them, where an element (other than {@code html}) or text is content.
	 */
	private int metadataDepth = 0;

	private List<String[]> pis = new ArrayList<String[]>();

	private StringBuilder contentBuff;

	/** The media attribute of the style element being read, passed on for CSSProcessor to evaluate (null: none). */
	private String styleMedia;

	private AttributesImpl atts = new AttributesImpl();

	private boolean useMetaInfo = true;

	/**
	 * Reads numeric viewport dimensions. Treats device-width/device-height as unspecified,
	 * because physical device dimensions are unavailable for printing.
	 */
	private static Double parseViewportDimension(final String value, final String deviceKeyword) {
		if (value == null) {
			return null;
		}
		final String normalized = value.trim();
		if (normalized.isEmpty() || deviceKeyword.equalsIgnoreCase(normalized)) {
			return null;
		}
		final double dimension = Double.parseDouble(normalized);
		if (!Double.isFinite(dimension) || dimension <= 0) {
			throw new NumberFormatException("viewport dimension must be finite and positive: " + value);
		}
		return dimension;
	}

	public XHTMLPreprocessFilter(UserAgent ua) {
		this.ua = ua;
		this.useMetaInfo = UAProps.OUTPUT_USE_META_INFO.getBoolean(ua);
	}

	public void startCDATA() throws SAXException {
		if (this.events == null) {
			super.startCDATA();
		} else {
			this.events.add(SAXEventRecorder.startCDATA());
		}
	}

	public void endCDATA() throws SAXException {
		if (this.events == null) {
			super.endCDATA();
		} else {
			this.events.add(SAXEventRecorder.endCDATA());
		}
	}

	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		if (this.events == null) {
			super.startDTD(name, publicId, systemId);
		} else {
			this.events.add(SAXEventRecorder.startDTD(name, publicId, systemId));
			this.dtdPos = this.events.size();
		}
	}

	public void endDTD() throws SAXException {
		if (this.events == null) {
			super.endDTD();
		} else {
			this.events.add(SAXEventRecorder.endDTD());
		}
	}

	public void startEntity(String name) throws SAXException {
		if (this.events == null) {
			super.startEntity(name);
		} else {
			this.events.add(SAXEventRecorder.startEntity(name));
		}
	}

	public void endEntity(String name) throws SAXException {
		if (this.events == null) {
			super.endEntity(name);
		} else {
			this.events.add(SAXEventRecorder.endEntity(name));
		}
	}

	public void comment(char[] ch, int off, int len) throws SAXException {
		if (this.events == null) {
			super.comment(ch, off, len);
		} else {
			this.events.add(SAXEventRecorder.comment(ch, off, len));
		}
	}

	public void processingInstruction(String target, String data) throws SAXException {
		if (this.pis == null) {
			super.processingInstruction(target, data);
		} else {
			this.pis.add(new String[] { target, data });
		}
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		if (this.events == null) {
			super.startPrefixMapping(prefix, uri);
		} else {
			this.events.add(SAXEventRecorder.startPrefixMapping(prefix, uri));
		}
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		if (this.events == null) {
			super.endPrefixMapping(prefix);
		} else {
			this.events.add(SAXEventRecorder.endPrefixMapping(prefix));
		}
	}

	public void skippedEntity(String name) throws SAXException {
		if (this.events == null) {
			super.skippedEntity(name);
		} else {
			this.events.add(SAXEventRecorder.skippedEntity(name));
		}
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		if (this.events == null) {
			super.ignorableWhitespace(ch, start, length);
		} else {
			this.events.add(SAXEventRecorder.ignorableWhitespace(ch, start, length));
		}
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (uri.equals(XHTML.URI)) {
			String[] pi = null;
			if (XHTML.H1_ELEM.lName.equals(lName) || XHTML.H2_ELEM.lName.equals(lName)
					|| XHTML.H3_ELEM.lName.equals(lName) || XHTML.H4_ELEM.lName.equals(lName)
					|| XHTML.H5_ELEM.lName.equals(lName) || XHTML.H6_ELEM.lName.equals(lName)) {
				// Header
				if (CSSJML.HEADER_ATTR.getValue(atts) == null) {
					this.atts.setAttributes(atts);
					atts = this.atts;
					CSSJML.HEADER_ATTR.addValue(this.atts, lName.substring(1));
				}
			} else if (lName.equals(XHTML.A_ELEM.lName)) {
				// Links
				String href = XHTML.HREF_ATTR.getValue(atts);
				if (href != null) {
					if (Constants.XLINK_HREF_ATTR.getValue(atts) == null) {
						this.atts.setAttributes(atts);
						atts = this.atts;
						Constants.XLINK_HREF_ATTR.addValue(this.atts, href);
					}
				}
				String name = XHTML.NAME_ATTR.getValue(atts);
				if (name != null) {
					if (XHTML.ID_ATTR.getValue(atts) == null) {
						if (atts != this.atts) {
							this.atts.setAttributes(atts);
							atts = this.atts;
						}
						XHTML.ID_ATTR.addValue(this.atts, name);
					}
				}
			} else if (lName.equals(XHTML.STYLE_ELEM.lName)) {
				// Embedded style sheet
				String disabled = atts.getValue(XHTML.URI, "disabled");// disabled
				// is a feature introduced in IE4.
				if (disabled == null) {
					String type = atts.getValue(XHTML.URI, "type");
					String media = atts.getValue(XHTML.URI, "media");
					if ((type == null && this.defaultStyleType.equalsIgnoreCase(Constants.CSS_MIME_TYPE))
							|| Constants.CSS_MIME_TYPE.equalsIgnoreCase(type)) {
						// CSSProcessor evaluates the media as @media does (2026-11-25): a query of features alone
						// ("(min-width: 768px)") applies when it holds
						this.styleMedia = media;
						this.contentBuff = new StringBuilder();
					}
				}
			} else if (lName.equals(XHTML.TITLE_ELEM.lName)) {
				// Title
				this.contentBuff = new StringBuilder();
			} else if (lName.equals(XHTML.META_ELEM.lName)) {
				// Character encoding
				final String charset = atts.getValue(XHTML.URI, "charset");
				if (charset != null) {
					pi = new String[] { CSSJML.PI_DEFAULT_ENCODING, charset };
				}

				final String content = atts.getValue(XHTML.URI, "content");
				if (content != null) {
					String httpEquiv = atts.getValue(XHTML.URI, "http-equiv");
					if (httpEquiv != null) {
						// Character encoding
						httpEquiv = httpEquiv.trim().toLowerCase(java.util.Locale.ROOT);
						if (httpEquiv.equals("content-type")) {
							String contentTypeCharset = MimeTypeHelper.getParameter(content, "charset");
							if (contentTypeCharset != null) {
								pi = new String[] { CSSJML.PI_DEFAULT_ENCODING, contentTypeCharset };
							}
						} else if (httpEquiv.equals("content-style-type")) {
							// Default style
							this.defaultStyleType = content;
							pi = new String[] { CSSJML.PI_DEFAULT_STYLE_TYPE, content };
						}
					}

					String name = atts.getValue(XHTML.URI, "name");
					if (name != null) {
						name = name.trim().toLowerCase(java.util.Locale.ROOT);
						if (name.equals("viewport") && UAProps.INPUT_VIEWPORT.getBoolean(this.ua)) {
							AttributesImpl attsi = new AttributesImpl();
							try {
								XMLUtils.parsePseudoAttributes(content, attsi);
								final Double width = parseViewportDimension(attsi.getValue("width"), "device-width");
								final Double height = parseViewportDimension(attsi.getValue("height"), "device-height");
								// HTML viewports commonly specify only one dimension. Override only the specified axis
								// and retain the default page dimension for the omitted axis.
								if (width != null) {
									this.ua.setProperty(UAProps.OUTPUT_PAGE_WIDTH.name, width + "px");
								}
								if (height != null) {
									this.ua.setProperty(UAProps.OUTPUT_PAGE_HEIGHT.name, height + "px");
								}
							} catch (Exception e) {
								// Ignore invalid viewport settings and continue with default page dimensions.
								LOG.log(java.util.logging.Level.WARNING, "Ignoring malformed viewport PI: " + content,
										e);
							}
						} else if (this.useMetaInfo) {
							// Document information
							String data = "name='" + XMLUtils.escapePseudeAttr(name) + "' value='"
									+ XMLUtils.escapePseudeAttr(content) + "'";
							pi = new String[] { CSSJML.PI_DOCUMENT_INFO, data };
						}
						this.contentBuff = null;
					}
				}
			} else if (lName.equals(XHTML.LINK_ELEM.lName)) {
				// External style sheet (SPEC ASSX1.0)
				String rel = atts.getValue(XHTML.URI, "rel");
				boolean valid = false;
				boolean alternate = false;
				if (rel != null) {
					for (StringTokenizer st = new StringTokenizer(rel); st.hasMoreTokens();) {
						String token = st.nextToken();
						if (token.equalsIgnoreCase(Constants.STYLESHEET_REL)) {
							valid = true;
						} else if (token.equalsIgnoreCase(Constants.ALTERNATE_REL)) {
							alternate = true;
						}
					}
				}
				if (valid) {
					String type = atts.getValue(XHTML.URI, "type");
					String href = atts.getValue(XHTML.URI, "href");
					String title = atts.getValue(XHTML.URI, "title");
					String mediaTypes = atts.getValue(XHTML.URI, "media");
					String charset = atts.getValue(XHTML.URI, "charset");
					StringBuilder data = new StringBuilder();
					if (type != null) {
						data.append(" type='");
						data.append(XMLUtils.escapePseudeAttr(type));
						data.append('\'');
					}
					if (href != null) {
						data.append(" href='");
						data.append(XMLUtils.escapePseudeAttr(href));
						data.append('\'');
					}
					if (title != null) {
						data.append(" title='");
						data.append(XMLUtils.escapePseudeAttr(title));
						data.append('\'');
					}
					if (mediaTypes != null) {
						data.append(" media='");
						data.append(XMLUtils.escapePseudeAttr(mediaTypes));
						data.append('\'');
					}
					if (charset != null) {
						data.append(" charset='");
						data.append(XMLUtils.escapePseudeAttr(charset));
						data.append('\'');
					}
					if (alternate) {
						data.append(" alternate='yes'");
					}
					pi = new String[] { Constants.LINK_PI, data.toString() };
				}
			} else if (lName.equals(XHTML.BASE_ELEM.lName)) {
				// base tag
				String href = atts.getValue(XHTML.URI, "href");
				if (href != null) {
					pi = new String[] { CSSJML.PI_BASE_URI, href };
				}
			} else if (lName.equals(XHTML.BODY_ELEM.lName)) {
				// body tag
				if (this.events != null) {
					this.startBody();
				}
				super.startElement(uri, lName, qName, atts);
				return;
			}
			if (this.events != null) {
				if (this.metadataDepth > 0 || isMetadata(lName)) {
					++this.metadataDepth;
				} else if (!lName.equals(XHTML.HTML_ELEM.lName)) {
					// Content of a document without a body element
					this.startBody();
				}
			}
			if (pi != null) {
				if (this.pis == null) {
					super.processingInstruction(pi[0], pi[1]);
				} else {
					this.pis.add(pi);
				}
			}
			if (this.events != null) {
				this.events.add(SAXEventRecorder.startElement(uri, lName, qName, atts));
			} else {
				super.startElement(uri, lName, qName, atts);
			}
		} else {
			if (this.events != null) {
				this.startBody();
			}
			super.startElement(uri, lName, qName, atts);
		}
	}

	/** Whether the element belongs to the head of a document, inside a head element or not. */
	private static boolean isMetadata(final String lName) {
		switch (lName) {
		case "head":
		case "title":
		case "meta":
		case "link":
		case "style":
		case "base":
		case "script":
			return true;
		default:
			return false;
		}
	}

	/** A document without a body element and without content: the head is all there is to pass on. */
	public void endDocument() throws SAXException {
		if (this.events != null) {
			this.startBody();
		}
		super.endDocument();
	}

	private void startBody() throws SAXException {
		List<SAXEventRecorder.SAXEvent> events = this.events;
		this.events = null;
		for (int i = 0; i < this.dtdPos; ++i) {
			SAXEvent event = (SAXEvent) events.get(i);
			event.doEvent(this.outHandler);
		}
		// Place processing instructions derived from the HTML header immediately before the root element.
		for (int i = 0; i < this.pis.size(); ++i) {
			String[] pi = (String[]) this.pis.get(i);
			super.processingInstruction(pi[0], pi[1]);
		}
		this.pis = null;
		for (int i = this.dtdPos; i < events.size(); ++i) {
			SAXEvent event = (SAXEvent) events.get(i);
			event.doEvent(this.outHandler);
		}
	}

	public void characters(char[] ch, int off, int len) throws SAXException {
		if (this.events != null && this.metadataDepth == 0) {
			for (int i = 0; i < len; ++i) {
				final char c = ch[off + i];
				if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
					// Text of a document without a body element (white space between the elements of the
					// head is not content)
					this.startBody();
					break;
				}
			}
		}
		if (this.events == null) {
			super.characters(ch, off, len);
		} else {
			this.events.add(SAXEventRecorder.characters(ch, off, len));
		}
		if (this.contentBuff != null) {
			this.contentBuff.append(ch, off, len);
		}
	}

	/**
	 * Like HTML {@code document.title}, trims leading and trailing ASCII whitespace and collapses
	 * consecutive whitespace to one space (2026-10-07). Previously, {@code <title>} contents became
	 * document metadata unchanged, leaving trailing spaces and newlines in PDF Title
	 * (inconsistent with validators that trim whitespace when reading XMP dc:title).
	 */
	private static String stripAndCollapse(final String s) {
		final StringBuilder buff = new StringBuilder(s.length());
		boolean space = false;
		for (int i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r') {
				space = buff.length() > 0;
			} else {
				if (space) {
					buff.append(' ');
					space = false;
				}
				buff.append(c);
			}
		}
		return buff.toString();
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		if (uri.equals(XHTML.URI)) {
			String[] pi = null;
			if (lName.equals(XHTML.STYLE_ELEM.lName)) {
				if (this.contentBuff != null) {
					final String media = this.styleMedia == null ? ""
							: "media='" + XMLUtils.escapePseudeAttr(this.styleMedia) + "' ";
					pi = new String[] { CSSJML.PI_STYLESHEET,
							media + "[" + XMLUtils.escapePseudeData(this.contentBuff.toString()) + "]" };
					this.contentBuff = null;
					this.styleMedia = null;
				}
			} else if (lName.equals(XHTML.TITLE_ELEM.lName)) {
				if (this.contentBuff != null) {
					if (this.useMetaInfo) {
						String data = "name='title' value='"
								+ XMLUtils.escapePseudeAttr(stripAndCollapse(this.contentBuff.toString())) + "'";
						pi = new String[] { CSSJML.PI_DOCUMENT_INFO, data };
					}
					// Release the buffer whether or not the title is used. Left in place when the document
					// information is off, it collected every later character of the document, and a later
					// style element that is to be skipped (another medium) was passed on as a style sheet
					// made of all that text.
					this.contentBuff = null;
				}
			}
			if (pi != null) {
				if (this.pis == null) {
					super.processingInstruction(pi[0], pi[1]);
				} else {
					this.pis.add(pi);
				}
			}
		}
		if (this.events == null) {
			super.endElement(uri, lName, qName);
		} else {
			this.events.add(SAXEventRecorder.endElement(uri, lName, qName));
			if (this.metadataDepth > 0) {
				// Every element that ends while the head is kept was counted at its start, except html
				--this.metadataDepth;
			}
		}
	}
}
