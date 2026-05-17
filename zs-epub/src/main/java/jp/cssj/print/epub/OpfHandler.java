package jp.cssj.print.epub;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

//OPFの読み込み
class OpfHandler extends DefaultHandler {
	final URI base;
	Contents contents = new Contents();

	boolean inMetadata = false;
	StringBuffer textBuff = null;
	String uniqueIdentifier, id, refines, propkey;

	Map<String, String> meta = new HashMap<String, String>();
	List<Item> items = new ArrayList<Item>();
	Map<String, Item> idToItem = new HashMap<String, Item>();
	Map<String, Item> hrefToItem = new HashMap<String, Item>();
	List<ItemRef> spine = new ArrayList<ItemRef>();
	List<Reference> guide = new ArrayList<Reference>();
	Map<String, PropertiedString> idToPropstr = new HashMap<String, PropertiedString>();

	public OpfHandler(String base) {
		this.base = URI.create(base);
		this.contents.base = base;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (this.inMetadata) {
			this.textBuff = new StringBuffer();
			this.id = atts.getValue("id");
			this.refines = atts.getValue("refines");
			if (uri.equals(EPubFile.OPF_URI)) {
				if (lName.equals("meta")) {
					this.propkey = atts.getValue("property");
					String name = atts.getValue("name");
					if (name != null) {
						String content = atts.getValue("content");
						if (content != null) {
							this.meta.put(name, content);
						}
					}
				}
			} else if (uri.equals(EPubFile.DC_URI)) {
				if (lName.equals("identifier")) {
					String scheme = atts.getValue(EPubFile.OPF_URI, "scheme");
					if (scheme != null) {
						textBuff.append(scheme.toLowerCase()).append(":");
					}
				}
			}
		} else if (uri.equals(EPubFile.OPF_URI)) {
			if (lName.equals("package")) {
				this.uniqueIdentifier = atts.getValue("unique-identifier");
			} else if (lName.equals("metadata")) {
				this.inMetadata = true;
			} else if (lName.equals("item")) {
				// 書籍に含まれるファイル情報を取得
				Item item = new Item();
				item.id = atts.getValue("id");
				item.href = atts.getValue("href");
				item.fullPath = this.base.resolve(item.href).getPath();
				item.mediaType = atts.getValue("media-type");
				{
					String properties = atts.getValue("properties");
					if (properties != null) {
						List<String> list = new ArrayList<String>();
						for (StringTokenizer st = new StringTokenizer(properties); st.hasMoreElements();) {
							list.add(st.nextToken().trim());
						}
						item.properties = Collections.unmodifiableList(list);
					} else {
						item.properties = Collections.emptyList();
					}
				}
				this.items.add(item);
				this.idToItem.put(item.id, item);
				this.hrefToItem.put(item.href, item);

				if (item.properties != null) {
					// EPUB3 表紙
					if (item.properties.contains("cover-image")) {
						this.contents.coverImage = item;
					}

					// EPUB3 目次
					if (item.properties.indexOf("nav") != -1) {
						this.contents.toc = item;
					}
				}
			} else if (lName.equals("spine")) {
				// EPUB2 目次
				if (this.contents.toc == null) {
					String toc = atts.getValue("toc");
					if (toc != null) {
						this.contents.toc = (Item) this.idToItem.get(toc);
					}
				}

				// 書籍全体のページの進行方向
				String pageProgressionDirection = atts.getValue("page-progression-direction");
				if (pageProgressionDirection != null) {
					if (pageProgressionDirection.equals("ltr")) {
						this.contents.pageProgressionDirection = Contents.PAGE_PROGRESSION_DIRECTION_LTR;
					} else if (pageProgressionDirection.equals("rtl")) {
						this.contents.pageProgressionDirection = Contents.PAGE_PROGRESSION_DIRECTION_RTL;
					}
				}
			} else if (lName.equals("itemref")) {
				String idref = atts.getValue("idref");
				Item item = (Item) this.idToItem.get(idref);
				if (item != null) {
					ItemRef itemRef = new ItemRef(item);
					this.spine.add(itemRef);

					String properties = atts.getValue("properties");
					if (properties != null) {
						List<String> list = new ArrayList<String>();
						for (StringTokenizer st = new StringTokenizer(properties); st.hasMoreElements();) {
							list.add(st.nextToken().trim());
						}
						itemRef.properties = Collections.unmodifiableList(list);

						// アイテムのページの進行方向
						if (itemRef.properties.contains("page-spread-left")) {
							itemRef.pageSpread = ItemRef.PAGE_SPREAD_LEFT;
						} else if (itemRef.properties.contains("page-spread-right")) {
							itemRef.pageSpread = ItemRef.PAGE_SPREAD_RIGHT;
						}
					} else {
						itemRef.properties = Collections.emptyList();
					}
				}
			} else if (lName.equals("reference")) {
				// EPUB2 リファレンス
				Reference reference = new Reference();
				reference.href = atts.getValue("href");
				reference.fullPath = this.base.resolve(reference.href).getPath();
				reference.type = atts.getValue("type");
				reference.title = atts.getValue("title");
				this.guide.add(reference);
				reference.item = (Item) this.hrefToItem.get(reference.href);
				if (reference.item != null) {
					reference.item.guide = reference;
					reference.item.title = reference.title;
					// EPUB2 カバー
					if (this.contents.coverImage == null) {
						if ("cover".equals(reference.type)) {
							this.contents.coverImage = reference.item;
						}
					}
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
		if (uri.equals(EPubFile.OPF_URI)) {
			if (lName.equals("metadata")) {
				this.inMetadata = false;
			} else if (lName.equals("manifest")) {
				if (this.contents.coverImage == null) {
					String cover = this.meta.get("cover");
					if (cover != null) {
						Item item = this.idToItem.get(cover);
						if (item != null) {
							this.contents.coverImage = item;
						}
					}
				}
			}
		}
		if (this.textBuff != null) {
			String text = this.textBuff.toString();
			this.textBuff = null;
			if (uri.equals(EPubFile.OPF_URI)) {
				if (lName.equals("meta")) {
					if (this.refines != null) {
						PropertiedString propstr = this.idToPropstr.get(this.refines.substring(1));
						if (propstr != null) {
							propstr.meta.put(this.propkey, text);
						}
					} else if (this.propkey != null) {
						this.meta.put(this.propkey, text);
					}
				}
			} else if (uri.equals(EPubFile.DC_URI)) {
				PropertiedString propstr = null;
				if (lName.equals("title")) {
					this.contents.title = propstr = new PropertiedString(text);
				} else if (lName.equals("description")) {
					this.contents.description = propstr = new PropertiedString(text);
				} else if (lName.equals("creator")) {
					this.contents.author.add(propstr = new PropertiedString(text));
				} else if (lName.equals("language")) {
					this.contents.language.add(propstr = new PropertiedString(text));
				} else if (lName.equals("identifier")) {
					propstr = new PropertiedString(text);
					this.contents.identifier.add(propstr);
					if (this.id != null && this.id.equals(this.uniqueIdentifier)) {
						this.contents.id = propstr;
					}
				} else if (lName.equals("rights")) {
					this.contents.rights.add(propstr = new PropertiedString(text));
				} else if (lName.equals("publisher")) {
					this.contents.publisher.add(propstr = new PropertiedString(text));
				}
				if (propstr != null && this.id != null) {
					this.idToPropstr.put(this.id, propstr);
				}
			}
		}
	}

	public Contents getContents() {
		this.contents.meta = Collections.unmodifiableMap(this.meta);

		this.contents.items = (Item[]) this.items.toArray(new Item[this.items.size()]);
		{
			Map<String, Item> map = new HashMap<String, Item>();
			for (Item item : this.contents.items) {
				map.put(item.fullPath, item);
			}
			this.contents.fullPathToItem = Collections.unmodifiableMap(map);
		}
		this.contents.spine = (ItemRef[]) this.spine.toArray(new ItemRef[this.spine.size()]);
		this.contents.guide = (Reference[]) this.guide.toArray(new Reference[this.spine.size()]);
		return this.contents;
	}
}
