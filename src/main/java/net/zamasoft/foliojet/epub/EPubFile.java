package net.zamasoft.foliojet.epub;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.zamasoft.foliojet.epub.Container.Rootfile;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * EPUBファイルを読み込みます。
 * 
 * @author MIYABE Tatsuhiko
 */
public class EPubFile {
	private static final Logger LOG = Logger.getLogger(EPubFile.class.getName());

	public static final String OPF_URI = "http://www.idpf.org/2007/opf";
	public static final String DC_URI = "http://purl.org/dc/elements/1.1/";
	public static final String NCX_URI = "http://www.daisy.org/z3986/2005/ncx/";
	public static final String OPS_URI = "http://www.idpf.org/2007/ops";
	public static final String XHTML_NS = "http://www.w3.org/1999/xhtml";

	public final ArchiveFile archive;
	private SAXParserFactory pf = SAXParserFactory.newInstance();
	{
		this.pf.setValidating(false);
		setFeature(this.pf, "http://xml.org/sax/features/external-general-entities", false);
		setFeature(this.pf, "http://xml.org/sax/features/external-parameter-entities", false);
		setFeature(this.pf, "http://xml.org/sax/features/namespaces", true);
		setFeature(this.pf, "http://xml.org/sax/features/validation", false);
		setFeature(this.pf, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		setFeature(this.pf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	}

	/**
	 * 与えられたEPUBファイルを読み込むためのインスタンスを作ります。
	 * 
	 * @param archive
	 *            EPUBファイル。
	 */
	public EPubFile(ArchiveFile archive) {
		this.archive = archive;
	}

	private SAXParser getSAXParser() {
		try {
			final SAXParser parser = this.pf.newSAXParser();
			return parser;
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		}
	}

	private static void setFeature(SAXParserFactory pf, String key, boolean b) {
		try {
			if (pf.getFeature(key) == b) {
				return;
			}
			pf.setFeature(key, b);
		} catch (Exception e) {
			LOG.log(Level.FINE, "サポートされない機能です", e);
		}
	}

	/**
	 * META-INF/container.xmlを読み込みます。
	 * 
	 * @return META-INF/container.xmlの情報。
	 * @throws FileNotFoundException
	 *             META-INF/container.xmlが存在しない場合。
	 * @throws IOException
	 *             ファイルの読み込みエラーがあった場合。
	 * @throws SAXException
	 *             ファイルの形式に問題があった場合。
	 */
	public Container readContainer() throws FileNotFoundException, IOException, SAXException {
		try (InputStream in = this.archive.getInputStream("META-INF/container.xml")) {
			SAXParser parser = this.getSAXParser();
			ContainerHandler handler = new ContainerHandler();
			parser.parse(new InputSource(in), handler);
			return handler.getContainer();
		}
	}

	/**
	 * OPFを読み込みます。
	 * 
	 * @param root
	 *            mimeTypeが"application/oebps-package+xml"のルートファイル。
	 * @return 解析済みOPF。
	 * @throws IOException
	 *             ファイルの読み込みエラーがあった場合。
	 * @throws SAXException
	 *             ファイルの形式に問題があった場合。
	 */
	public Contents readContents(Rootfile root) throws IOException, SAXException {
		try (InputStream in = new BufferedInputStream(this.archive.getInputStream(root.fullPath))) {
			SAXParser parser = this.getSAXParser();
			OpfHandler handler = new OpfHandler(root.fullPath);
			parser.parse(new InputSource(in), handler);
			// TODO readTitle が異常に遅い
			// for (Item item : handler.items) {
			// if (item.title == null) {
			// item.title = this.readTitle(item);
			// }
			// }
			return handler.getContents();
		}
	}

	/**
	 * NCX形式の目次を取得します。
	 * 
	 * @param contents
	 *            解析済みOPF。
	 * @return 解析済みNCX。
	 * @throws IOException
	 *             ファイルの読み込みエラーがあった場合。
	 * @throws SAXException
	 *             ファイルの形式に問題があった場合。
	 */
	public Toc readToc(Contents contents) throws IOException, SAXException {
		if (contents.toc == null) {
			// spine@tocがない場合、ncxを検索する。
			// 不正なOPFに対応するための仕様外の動作です
			for (int i = 0; i < contents.items.length; ++i) {
				if ("application/x-dtbncx+xml".equals(contents.items[i].mediaType)) {
					contents.toc = contents.items[i];
					break;
				}
			}
		}
		if (contents.toc == null) {
			return null;
		}
		if ("application/xhtml+xml".equals(contents.toc.mediaType)) {
			// EPUB3 NAV
			NavHandler handler = new NavHandler(contents);
			try (final InputStream in = this.archive.getInputStream(contents.toc.fullPath)) {
				final SAXParser parser = this.getSAXParser();
				parser.parse(new InputSource(in), handler);
			}
			return handler.getToc();
		}
		if ("application/x-dtbncx+xml".equals(contents.toc.mediaType)) {
			// EPUB2 NCX
			NcxHandler handler = new NcxHandler(contents);
			try (final InputStream in = this.archive.getInputStream(contents.toc.fullPath)) {
				final SAXParser parser = this.getSAXParser();
				parser.parse(new InputSource(in), handler);
			}
			return handler.getToc();
		} else {
			return null;
		}
	}
}
