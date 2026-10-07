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
 * Reads an EPUB file.
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
	 * Creates an instance for reading the given EPUB file.
	 * 
	 * @param archive
	 *            The EPUB file.
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
	 * Reads META-INF/container.xml.
	 * 
	 * @return The information in META-INF/container.xml.
	 * @throws FileNotFoundException
	 *             If META-INF/container.xml does not exist.
	 * @throws IOException
	 *             If an error occurs while reading the file.
	 * @throws SAXException
	 *             If the file format is invalid.
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
	 * Reads the OPF.
	 * 
	 * @param root
	 *            The root file whose mimeType is "application/oebps-package+xml".
	 * @return The parsed OPF.
	 * @throws IOException
	 *             If an error occurs while reading the file.
	 * @throws SAXException
	 *             If the file format is invalid.
	 */
	public Contents readContents(Rootfile root) throws IOException, SAXException {
		try (InputStream in = new BufferedInputStream(this.archive.getInputStream(root.fullPath))) {
			SAXParser parser = this.getSAXParser();
			OpfHandler handler = new OpfHandler(root.fullPath);
			parser.parse(new InputSource(in), handler);
			// TODO readTitle is unusually slow
			// for (Item item : handler.items) {
			// if (item.title == null) {
			// item.title = this.readTitle(item);
			// }
			// }
			return handler.getContents();
		}
	}

	/**
	 * Gets the table of contents in NCX format.
	 * 
	 * @param contents
	 *            The parsed OPF.
	 * @return The parsed NCX.
	 * @throws IOException
	 *             If an error occurs while reading the file.
	 * @throws SAXException
	 *             If the file format is invalid.
	 */
	public Toc readToc(Contents contents) throws IOException, SAXException {
		if (contents.toc == null) {
			// Search for the ncx if spine@toc is absent.
			// This behavior falls outside the specification to support invalid OPF files
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
