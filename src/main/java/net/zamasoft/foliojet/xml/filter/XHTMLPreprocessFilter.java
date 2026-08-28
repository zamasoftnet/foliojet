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

	/** ドキュメントのデフォルトのスタイル付け方式。 */
	private String defaultStyleType = Constants.CSS_MIME_TYPE;

	private final UserAgent ua;

	private int dtdPos = 0;

	private List<SAXEventRecorder.SAXEvent> events = new ArrayList<SAXEventRecorder.SAXEvent>();

	private List<String[]> pis = new ArrayList<String[]>();

	private StringBuilder contentBuff;

	private AttributesImpl atts = new AttributesImpl();

	private boolean useMetaInfo = true;

	/**
	 * viewportの数値寸法を読みます。device-width/device-heightは、印刷時の
	 * 物理デバイス寸法を持たないため「指定なし」と同じ扱いにします。
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
		// System.err.println(lName);
		if (uri.equals(XHTML.URI)) {
			String[] pi = null;
			if (XHTML.H1_ELEM.lName.equals(lName) || XHTML.H2_ELEM.lName.equals(lName)
					|| XHTML.H3_ELEM.lName.equals(lName) || XHTML.H4_ELEM.lName.equals(lName)
					|| XHTML.H5_ELEM.lName.equals(lName) || XHTML.H6_ELEM.lName.equals(lName)) {
				// ヘッダー
				if (CSSJML.HEADER_ATTR.getValue(atts) == null) {
					this.atts.setAttributes(atts);
					atts = this.atts;
					CSSJML.HEADER_ATTR.addValue(this.atts, lName.substring(1));
				}
			} else if (lName.equals(XHTML.A_ELEM.lName)) {
				// リンク
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
				// 埋め込みスタイルシート
				String disabled = atts.getValue(XHTML.URI, "disabled");// disabled
				// はIE4からの機能
				if (disabled == null) {
					String type = atts.getValue(XHTML.URI, "type");
					String media = atts.getValue(XHTML.URI, "media");
					if ((type == null && this.defaultStyleType.equalsIgnoreCase(Constants.CSS_MIME_TYPE))
							|| Constants.CSS_MIME_TYPE.equalsIgnoreCase(type)) {
						if (media != null) {
							media = media.toLowerCase();
						} else {
							media = "all";
						}
						if (this.ua.is(media)) {
							this.contentBuff = new StringBuilder();
						}
					}
				}
			} else if (lName.equals(XHTML.TITLE_ELEM.lName)) {
				// タイトル
				this.contentBuff = new StringBuilder();
			} else if (lName.equals(XHTML.META_ELEM.lName)) {
				// 文字コード
				final String charset = atts.getValue(XHTML.URI, "charset");
				if (charset != null) {
					pi = new String[] { CSSJML.PI_DEFAULT_ENCODING, charset };
				}

				final String content = atts.getValue(XHTML.URI, "content");
				if (content != null) {
					String httpEquiv = atts.getValue(XHTML.URI, "http-equiv");
					if (httpEquiv != null) {
						// 文字コード
						httpEquiv = httpEquiv.trim().toLowerCase();
						if (httpEquiv.equals("content-type")) {
							String contentTypeCharset = MimeTypeHelper.getParameter(content, "charset");
							if (contentTypeCharset != null) {
								pi = new String[] { CSSJML.PI_DEFAULT_ENCODING, contentTypeCharset };
							}
						} else if (httpEquiv.equals("content-style-type")) {
							// デフォルトのスタイル
							this.defaultStyleType = content;
							pi = new String[] { CSSJML.PI_DEFAULT_STYLE_TYPE, content };
						}
					}

					String name = atts.getValue(XHTML.URI, "name");
					if (name != null) {
						name = name.trim().toLowerCase();
						if (name.equals("viewport") && UAProps.INPUT_VIEWPORT.getBoolean(this.ua)) {
							AttributesImpl attsi = new AttributesImpl();
							try {
								XMLUtils.parsePseudoAttributes(content, attsi);
								final Double width = parseViewportDimension(attsi.getValue("width"), "device-width");
								final Double height = parseViewportDimension(attsi.getValue("height"), "device-height");
								// HTMLのviewportでは片方だけの指定が一般的。指定された軸だけを
								// 上書きし、省略軸は既定ページ寸法を保つ。
								if (width != null) {
									this.ua.setProperty(UAProps.OUTPUT_PAGE_WIDTH.name, width + "px");
								}
								if (height != null) {
									this.ua.setProperty(UAProps.OUTPUT_PAGE_HEIGHT.name, height + "px");
								}
							} catch (Exception e) {
								// 不正なviewport指定は無視して既定のページ寸法で続行
								LOG.log(java.util.logging.Level.WARNING, "Ignoring malformed viewport PI: " + content,
										e);
							}
						} else if (this.useMetaInfo) {
							// 文書情報
							String data = "name='" + XMLUtils.escapePseudeAttr(name) + "' value='"
									+ XMLUtils.escapePseudeAttr(content) + "'";
							pi = new String[] { CSSJML.PI_DOCUMENT_INFO, data };
						}
						this.contentBuff = null;
					}
				}
			} else if (lName.equals(XHTML.LINK_ELEM.lName)) {
				// 外部スタイルシート (SPEC ASSX1.0)
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
				// baseタグ
				String href = atts.getValue(XHTML.URI, "href");
				if (href != null) {
					pi = new String[] { CSSJML.PI_BASE_URI, href };
				}
			} else if (lName.equals(XHTML.BODY_ELEM.lName)) {
				// bodyタグ
				if (this.events != null) {
					this.startBody();
				}
				super.startElement(uri, lName, qName, atts);
				return;
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

	private void startBody() throws SAXException {
		List<SAXEventRecorder.SAXEvent> events = this.events;
		this.events = null;
		for (int i = 0; i < this.dtdPos; ++i) {
			SAXEvent event = (SAXEvent) events.get(i);
			event.doEvent(this.outHandler);
		}
		// HTMLヘッダから作られたPIをルート要素の直前に置く
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
		// System.out.println(new String(ch, off, len));
		if (this.events == null) {
			super.characters(ch, off, len);
		} else {
			this.events.add(SAXEventRecorder.characters(ch, off, len));
		}
		if (this.contentBuff != null) {
			this.contentBuff.append(ch, off, len);
		}
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		// System.err.println("/"+lName);
		if (uri.equals(XHTML.URI)) {
			String[] pi = null;
			if (lName.equals(XHTML.STYLE_ELEM.lName)) {
				if (this.contentBuff != null) {
					pi = new String[] { CSSJML.PI_STYLESHEET,
							"[" + XMLUtils.escapePseudeData(this.contentBuff.toString()) + "]" };
					this.contentBuff = null;
				}
			} else if (lName.equals(XHTML.TITLE_ELEM.lName)) {
				if (this.useMetaInfo && this.contentBuff != null) {
					String data = "name='title' value='" + XMLUtils.escapePseudeAttr(this.contentBuff.toString()) + "'";
					pi = new String[] { CSSJML.PI_DOCUMENT_INFO, data };
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
		}
	}
}
