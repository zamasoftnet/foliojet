package jp.cssj.homare.impl.formatter.epub;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.formatter.Formatter;
import jp.cssj.homare.impl.formatter.document.TranscoderHandler;
import jp.cssj.homare.message.MessageCodeUtils;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.ua.AbortException;
import jp.cssj.homare.ua.UserAgent;
import jp.cssj.homare.ua.props.BooleanPropManager;
import jp.cssj.homare.ua.props.OutputPrintMode;
import jp.cssj.homare.ua.props.UAProps;
import jp.cssj.homare.xml.DefaultXMLHandlerFilter;
import jp.cssj.homare.xml.Parser;
import jp.cssj.homare.xml.ParserFactory;
import jp.cssj.homare.xml.XMLHandler;
import jp.cssj.homare.xml.XMLHandlerWrapper;
import jp.cssj.plugin.PluginLoader;
import jp.cssj.print.epub.Container;
import jp.cssj.print.epub.Container.Rootfile;
import jp.cssj.print.epub.Contents;
import jp.cssj.print.epub.EPubFile;
import jp.cssj.print.epub.Item;
import jp.cssj.print.epub.ItemRef;
import jp.cssj.print.epub.ZipArchiveFile;
import jp.cssj.print.epub.util.WritingModeHandler;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.zstream.resolver.protocol.zip.ZIPFileSource;
import net.zamasoft.zstream.resolver.protocol.zip.ZIPFileSourceResolver;
import net.zamasoft.pdfg2d.gc.GC;

import org.apache.commons.io.IOUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * EPubをフォーマットします。
 */
public class EPubFormatter implements Formatter {
	private static final Logger LOG = Logger.getLogger(EPubFormatter.class.getName());

	public static final BooleanPropManager REPLACE_NUMBERS = new BooleanPropManager(
			"x.jp.cssj.homare.impl.formatter.epub.replace-numbers", false);

	public boolean match(final Source key) {
		final Source source = (Source) key;
		try {
			final String uri = source.getURI().toString();
			if (uri.length() >= 5 && uri.substring(uri.length() - 5).equalsIgnoreCase(".epub")) {
				return true;
			}
			final String mimeType = source.getMimeType();
			if (mimeType != null && mimeType.equals("application/epub+zip")) {
				return true;
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING, "変換元文書のMIME型を取得できませんでした", e);
		}
		return false;
	}

	private CSSElement getPageSide(UserAgent ua, boolean leftBind) {
		CSSElement pageElement = ua.getPassContext().getPageSide();
		switch (UAProps.OUTPUT_PRINT_MODE.getCode(ua)) {
		case OutputPrintMode.DOUBLE_SIDE:
		case OutputPrintMode.LEFT_SIDE:
		case OutputPrintMode.RIGHT_SIDE:
			// 両面
			if (leftBind) {
				// 横書き
				if (pageElement == null) {
					pageElement = CSSElement.PAGE_FIRST_RIGHT;
				} else if (pageElement == CSSElement.PAGE_FIRST_RIGHT) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				} else if (pageElement == CSSElement.PAGE_LEFT_EVEN) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				} else if (pageElement == CSSElement.PAGE_RIGHT_ODD) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				}
			} else {
				// 縦書き
				if (pageElement == null) {
					pageElement = CSSElement.PAGE_FIRST_LEFT;
				} else if (pageElement == CSSElement.PAGE_FIRST_LEFT) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				} else if (pageElement == CSSElement.PAGE_RIGHT_ODD) {
					pageElement = CSSElement.PAGE_LEFT_EVEN;
				} else if (pageElement == CSSElement.PAGE_LEFT_EVEN) {
					pageElement = CSSElement.PAGE_RIGHT_ODD;
				}
			}
			break;

		case OutputPrintMode.SINGLE_SIDE:
			// 片面
			if (pageElement == null) {
				pageElement = CSSElement.PAGE_SINGLE_FIRST;
			} else {
				pageElement = CSSElement.PAGE_SINGLE;
			}
			break;

		default:
			throw new IllegalStateException();
		}
		return pageElement;
	}

	public void format(final Source source, final UserAgent ua) throws AbortException, TranscoderException {
		try {
			final File epubFile;
			if (source.isFile()) {
				epubFile = source.getFile();
			} else {
				epubFile = File.createTempFile("epub", ".epub");
				try (final OutputStream out = new FileOutputStream(epubFile)) {
					final InputStream in = source.getInputStream();
					IOUtils.copy(in, out);
				}
			}
			try {
				try (final ZipFile zip = new ZipFile(epubFile)) {
					// データ源をZIPファイルに設定
					final CompositeSourceResolver resolver = new CompositeSourceResolver();
					resolver.addSourceResolver("zip", new ZIPFileSourceResolver(zip));
					resolver.setDefaultSourceResolver(ua.getSourceResolver());
					resolver.setDefaultScheme("zip");
					ua.setSourceResolver(resolver);

					// メタ情報解析
					final EPubFile epub = new EPubFile(new ZipArchiveFile(epubFile, zip));
					Container container = epub.readContainer();

					final Rootfile root = container.rootfiles[0];
					Contents contents = epub.readContents(root);

					// ページ進行方向
					boolean leftBind = true;
					switch (contents.pageProgressionDirection) {
					case Contents.PAGE_PROGRESSION_DIRECTION_LTR:
						ua.setProperty(UAProps.OUTPUT_PRINT_MODE.getName(), "left-side");
						break;
					case Contents.PAGE_PROGRESSION_DIRECTION_RTL:
						ua.setProperty(UAProps.OUTPUT_PRINT_MODE.getName(), "right-side");
						leftBind = false;
						break;
					}

					// ファイルパスと項目の関係を取得
					final Map<URI, Item> fullPathToItem = new HashMap<URI, Item>();
					for (int i = 0; i < contents.spine.length; ++i) {
						final ItemRef ir = contents.spine[i];
						fullPathToItem.put(URI.create(ir.item.fullPath), ir.item);
					}

					// 各項目のフォーマット
					for (int i = 0; i < contents.spine.length; ++i) {
						final ItemRef ir = contents.spine[i];
						switch (ir.pageSpread) {
						case ItemRef.PAGE_SPREAD_LEFT: {
							CSSElement e = this.getPageSide(ua, leftBind);
							if (e.isPseudoClass(CSSElement.PC_LEFT)) {
								String ws = UAProps.OUTPUT_PAGE_WIDTH.getString(ua);
								AbsoluteLengthValue wl = ValueUtils.toAbsoluteLength(ua, false, ws);
								String hs = UAProps.OUTPUT_PAGE_HEIGHT.getString(ua);
								AbsoluteLengthValue hl = ValueUtils.toAbsoluteLength(ua, false, hs);
								ws = UAProps.OUTPUT_PAPER_WIDTH.getString(ua);
								if (ws != null) {
									wl = ValueUtils.toAbsoluteLength(ua, false, ws);
								}
								hs = UAProps.OUTPUT_PAPER_HEIGHT.getString(ua);
								if (hs != null) {
									hl = ValueUtils.toAbsoluteLength(ua, false, hs);
								}
								GC gc = ua.nextPage(wl.getLength(), hl.getLength());
								ua.closePage(gc);
							}
						}
							break;
						case ItemRef.PAGE_SPREAD_RIGHT:
							CSSElement e = this.getPageSide(ua, leftBind);
							if (e.isPseudoClass(CSSElement.PC_RIGHT)) {
								String ws = UAProps.OUTPUT_PAGE_WIDTH.getString(ua);
								AbsoluteLengthValue wl = ValueUtils.toAbsoluteLength(ua, false, ws);
								String hs = UAProps.OUTPUT_PAGE_HEIGHT.getString(ua);
								AbsoluteLengthValue hl = ValueUtils.toAbsoluteLength(ua, false, hs);
								ws = UAProps.OUTPUT_PAPER_WIDTH.getString(ua);
								if (ws != null) {
									wl = ValueUtils.toAbsoluteLength(ua, false, ws);
								}
								hs = UAProps.OUTPUT_PAPER_HEIGHT.getString(ua);
								if (hs != null) {
									hl = ValueUtils.toAbsoluteLength(ua, false, hs);
								}
								GC gc = ua.nextPage(wl.getLength(), hl.getLength());
								ua.closePage(gc);
							}
							break;
						}

						ua.getPassContext().resetExcluidePageCountes();
						final URI path = URIHelper.create("UTF-8", ir.item.fullPath);
						ua.getDocumentContext().setBaseURI(path);
						final Source zSource = new ZIPFileSource(zip, path, ir.item.mediaType);
						final String mimeType = zSource.getMimeType();
						if (mimeType.equals("application/xhtml+xml")) {
							ParserFactory pf = (ParserFactory) PluginLoader.getPluginLoader()
									.search(ParserFactory.class, mimeType);
							Parser parser = pf.createParser();
							XMLHandler entryPoint = new TranscoderHandler(ua);
							entryPoint = new LinkHandler(entryPoint, ir.item, fullPathToItem);
							boolean replaceNumbers = REPLACE_NUMBERS.getBoolean(ua);
							WritingModeHandler xhandler = new WritingModeHandler(entryPoint, ir.item, replaceNumbers);
							entryPoint = new XMLHandlerWrapper(xhandler, null);
							parser.parse(ua, zSource, entryPoint);
						} else {
							Formatter formatter = (Formatter) PluginLoader.getPluginLoader().search(Formatter.class,
									zSource);
							formatter.format(zSource, ua);
						}
					}
				}
			} finally {
				if (!source.isFile()) {
					epubFile.delete();
				}
			}
		} catch (Exception e) {
			short code = MessageCodes.ERROR_PLUGIN;
			String[] args = new String[] { "jp.cssj.plugins.epub", e.getLocalizedMessage() };
			String mes = MessageCodeUtils.toString(code, args);
			ua.message(code, args);
			LOG.log(Level.WARNING, mes, e);
			throw new TranscoderException(code, args, mes);
		}
	}
}

class LinkHandler extends DefaultXMLHandlerFilter {
	final AttributesImpl attsi = new AttributesImpl();
	final Item item;
	final Map<URI, Item> fullPathToItem;
	final URI base;

	LinkHandler(XMLHandler handler, Item item, Map<URI, Item> fullPathToItem) {
		super(handler);
		this.item = item;
		this.base = URI.create(item.fullPath);
		this.fullPathToItem = fullPathToItem;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (lName.equals("body")) {
			super.startElement(uri, lName, qName, atts);

			this.attsi.clear();
			this.attsi.addAttribute("", "id", "id", "CDATA", this.item.fullPath);
			this.attsi.addAttribute("", "name", "name", "CDATA", "x-epub-" + this.item.fullPath);
			super.startElement(uri, "a", "a", this.attsi);
			super.endElement(uri, "a", "a");
			return;
		} else if (lName.equals("a")) {
			int href = atts.getIndex("href");
			if (href != -1) {
				String ref = atts.getValue(href);
				try {
					URI fullPath = URIHelper.resolve("UTF-8", this.base, ref);
					Item item = this.fullPathToItem.get(fullPath);
					this.attsi.setAttributes(atts);
					if (item != null) {
						atts = this.attsi;
						this.attsi.setValue(href, "#x-epub-" + item.fullPath);
					}
				} catch (URISyntaxException e) {
					throw new SAXException(e);
				}
			}
		}
		super.startElement(uri, lName, qName, atts);
	}
}
