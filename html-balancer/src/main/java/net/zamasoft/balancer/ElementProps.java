package net.zamasoft.balancer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.xerces.parsers.NonValidatingConfiguration;
import org.apache.xerces.parsers.SAXParser;
import org.cyberneko.html.HTMLElements;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 要素のバランスのための情報です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: ElementProps.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ElementProps {
	/**
	 * 文書の内容です。(BODY)
	 */
	public static final int FLAG_BODY = 0x00000001;

	/**
	 * ヘッダの内容です。(LINK, META, LINKなど)
	 */
	public static final int FLAG_HEAD = 0x00000002;

	/**
	 * 空タグです。 (BR, HR, COLなど)
	 */
	public static final int FLAG_EMPTY = 0x00000004;

	/**
	 * 終了タグを空タグに変換します。 (BR, P)
	 */
	public static final int FLAG_END_TO_EMPTY = 0x00000008;

	/**
	 * 直下のテキストを無視します。
	 */
	public static final int FLAG_IGNORE_TEXT = 0x00000010;

	/**
	 * テキストにより閉じられます。
	 */
	public static final int FLAG_CLOSE_BY_TEXT = 0x00000020;

	/**
	 * 代替要素です(TDに対するTHなど)。 この要素に対する、代替要素の閉じタグがあれば、この要素に入れ替えられます。
	 */
	public static final int SET_ALTERNATES = 0;

	/**
	 * 指定の要素が見つかるまで先祖を掘り下げます。 要素がない場合は掘り下げません。
	 */
	public static final int SET_DIGS_FOR = 1;

	/**
	 * この要素の親に自動的に挿入される要素です(TDに対するTRなど)。 親が指定の要素であれば挿入されません。
	 */
	public static final int SET_INSERT_PARENTS = 2;

	/**
	 * この要素の開始タグにより閉じる先祖要素です。 指定の要素まで掘り下げられます。
	 */
	public static final int SET_OPEN_CLOSES = 3;

	/**
	 * この要素の終了タグにより閉じられた場合に継続しない要素です。 タグの不一致がある場合、終了タグで親の要素は閉じて開かれますが、この要素は再開しません。
	 */
	public static final int SET_CLOSE_CLOSES = 4;

	/**
	 * この要素の直下で開始タグを無視する要素です。
	 */
	public static final int SET_DISCARDS_OPEN = 5;

	/**
	 * この要素の直下で終了タグを無視する要素です。
	 */
	public static final int SET_DISCARDS_CLOSE = 6;

	/**
	 * この要素の開始タグにより閉じて直後で開く親要素です。
	 */
	public static final int SET_OPEN_SPLITS = 7;

	/**
	 * SET_OPEN_CLOSESによる掘り下げを停止する先祖要素です。
	 */
	public static final int SET_STOP_CLOSE_BY = 8;

	/**
	 * テキストにより挿入する要素です。
	 */
	public static final int SET_INSERT_BY_TEXT = 9;

	public static class ElementProp {
		/**
		 * 要素のコードです。
		 */
		public final short code;

		/**
		 * フラグです。
		 */
		public final int flags;

		public final short[][] tags = new short[SET_INSERT_BY_TEXT + 1][];

		public ElementProp(short code, int flags) {
			this.code = code;
			this.flags = flags;
		}

		public boolean is(int flag) {
			return (this.flags & flag) != 0;
		}

		public boolean contains(int set) {
			return this.tags[set] != null;
		}

		public boolean contains(int set, short code) {
			short[] tags = this.tags[set];
			for (int i = 0; i < tags.length; ++i) {
				if (tags[i] == code) {
					return true;
				}
			}
			return false;
		}
	}

	private static final Map<String, ElementProps> CONFIGS = new HashMap<String, ElementProps>();

	public static final ElementProps getElementProps(String name) {
		try {
			ElementProps config = (ElementProps) CONFIGS.get(name);
			if (config == null) {
				try (InputStream in = ElementProps.class.getResourceAsStream(name)) {
					config = new ElementProps(new InputSource(in));
				}
				CONFIGS.put(name, config);
			}
			return config;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

	private final ElementProp DEFAULT_ELEMENT_PROP;
	private final ElementProp[] PROPS = new ElementProp[150];

	private ElementProps(InputSource is) throws IOException, SAXException {
		SAXParser parser = new SAXParser(new NonValidatingConfiguration());
		parser.setContentHandler(new DefaultHandler() {
			ElementProp elem;
			StringBuffer buff = null;
			String tagsetName;
			Map<String, List<String>> tagsets = new HashMap<String, List<String>>();

			public void startElement(String uri, String qName, String lName, Attributes atts) throws SAXException {
				if (qName.equals("tagset")) {
					this.tagsetName = atts.getValue("name");
				} else if (qName.equals("tag")) {
					HTMLElements.Element e = HTMLElements.getElement(atts.getValue("name"));
					int flags = 0;
					String flagStr = atts.getValue("flags");
					if (flagStr != null) {
						for (StringTokenizer st = new StringTokenizer(flagStr, "|"); st.hasMoreTokens();) {
							String flag = st.nextToken().trim();
							if (flag.length() == 0) {
								continue;
							}
							if (flag.equals("BODY")) {
								flags |= FLAG_BODY;
							} else if (flag.equals("HEAD")) {
								flags |= FLAG_HEAD;
							} else if (flag.equals("EMPTY")) {
								flags |= FLAG_EMPTY;
							} else if (flag.equals("END_TO_EMPTY")) {
								flags |= FLAG_END_TO_EMPTY;
							} else if (flag.equals("IGNORE_TEXT")) {
								flags |= FLAG_IGNORE_TEXT;
							} else if (flag.equals("CLOSE_BY_TEXT")) {
								flags |= FLAG_CLOSE_BY_TEXT;
							} else {
								throw new SAXException("Unexpected flag: " + flag);
							}
						}
					}
					this.elem = new ElementProp(e.code, flags);
					PROPS[e.code] = this.elem;
				}
				this.buff = null;
			}

			public void characters(char[] ch, int off, int len) throws SAXException {
				if (this.buff == null) {
					this.buff = new StringBuffer();
				}
				this.buff.append(ch, off, len);
			}

			public void endElement(String uri, String qName, String lName) throws SAXException {
				if (this.buff == null) {
					return;
				}
				List<String> list = new ArrayList<String>();
				for (StringTokenizer st = new StringTokenizer(this.buff.toString(), ","); st.hasMoreTokens();) {
					String str = st.nextToken().trim();
					if (str.length() == 0) {
						continue;
					}
					if (str.startsWith("-")) {
						// 除外
						str = str.substring(1);
						list.remove(str);
					} else if (str.startsWith("$")) {
						// タグセット
						str = str.substring(1);
						List<String> tagset = this.tagsets.get(str);
						if (tagset != null) {
							list.addAll(tagset);
						}
					} else {
						// タグ
						list.add(str);
					}
				}
				this.buff = null;
				if (!list.isEmpty()) {
					short[] codes = new short[list.size()];
					for (int i = 0; i < list.size(); ++i) {
						HTMLElements.Element close = HTMLElements.getElement((String) list.get(i));
						codes[i] = close.code;
					}
					if (qName.equals("tagset")) {
						this.tagsets.put(this.tagsetName, list);
					} else if (qName.equals("alternates")) {
						this.elem.tags[SET_ALTERNATES] = codes;
					} else if (qName.equals("digsFor")) {
						this.elem.tags[SET_DIGS_FOR] = codes;
					} else if (qName.equals("insertParents")) {
						this.elem.tags[SET_INSERT_PARENTS] = codes;
					} else if (qName.equals("openCloses")) {
						this.elem.tags[SET_OPEN_CLOSES] = codes;
					} else if (qName.equals("closeCloses")) {
						this.elem.tags[SET_CLOSE_CLOSES] = codes;
					} else if (qName.equals("discardsOpen")) {
						this.elem.tags[SET_DISCARDS_OPEN] = codes;
					} else if (qName.equals("discardsClose")) {
						this.elem.tags[SET_DISCARDS_CLOSE] = codes;
					} else if (qName.equals("openSplits")) {
						this.elem.tags[SET_OPEN_SPLITS] = codes;
					} else if (qName.equals("stopCloseBy")) {
						this.elem.tags[SET_STOP_CLOSE_BY] = codes;
					} else if (qName.equals("insertByText")) {
						this.elem.tags[SET_INSERT_BY_TEXT] = codes;
					}
				}
			}
		});
		parser.parse(is);
		DEFAULT_ELEMENT_PROP = PROPS[HTMLElements.UNKNOWN];
	}

	public ElementProp getElementProp(short code) {
		if (code >= PROPS.length) {
			return DEFAULT_ELEMENT_PROP;
		}
		ElementProp element = PROPS[code];
		if (element == null) {
			return DEFAULT_ELEMENT_PROP;
		}
		return element;
	}
}
