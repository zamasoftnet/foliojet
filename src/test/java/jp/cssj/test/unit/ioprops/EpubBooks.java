package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Builds small EPUB books for tests and converts them through a direct session.
 *
 * <p>
 * The package document lists the items in spine order. Each item is an XHTML string; other entries (style
 * sheets, images) are added with {@link #file}.
 * </p>
 */
final class EpubBooks {
	/** One spine item: XHTML, or another file ({@code data}) whose media type follows its extension. */
	record Item(String id, String href, String properties, String xhtml, byte[] data) {
	}

	private final List<Item> items = new ArrayList<>();
	private final Map<String, byte[]> files = new LinkedHashMap<>();
	private String metadata = "<dc:title>Test Book</dc:title><dc:language>en</dc:language>";
	private String progression = null;
	private byte[] raw = null;

	EpubBooks metadata(final String dcElements) {
		this.metadata = dcElements;
		return this;
	}

	/** {@code ltr}, {@code rtl} or {@code null} for the default. */
	EpubBooks progression(final String direction) {
		this.progression = direction;
		return this;
	}

	EpubBooks item(final String id, final String href, final String properties, final String xhtml) {
		this.items.add(new Item(id, href, properties, xhtml, null));
		return this;
	}

	/** A spine item that is not XHTML (an SVG cover). */
	EpubBooks spineFile(final String id, final String href, final String properties, final byte[] data) {
		this.items.add(new Item(id, href, properties, null, data));
		return this;
	}

	/** Converts these bytes instead of a built book (input that is not an EPUB). */
	EpubBooks raw(final byte[] bytes) {
		this.raw = bytes;
		return this;
	}

	EpubBooks item(final String id, final String href, final String xhtml) {
		return this.item(id, href, null, xhtml);
	}

	EpubBooks file(final String href, final byte[] data) {
		this.files.put(href, data);
		return this;
	}

	EpubBooks file(final String href, final String text) {
		return this.file(href, text.getBytes(StandardCharsets.UTF_8));
	}

	/** An XHTML item with the given head markup and body markup. */
	static String xhtml(final String title, final String head, final String body) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
				+ "<head><title>" + title + "</title>" + head + "</head><body>" + body + "</body></html>";
	}

	byte[] build() throws Exception {
		if (this.raw != null) {
			return this.raw;
		}
		final StringBuilder opf = new StringBuilder();
		opf.append("<?xml version=\"1.0\"?>")
				.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\">")
				.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
				.append("<dc:identifier id=\"uid\">urn:uuid:test-book</dc:identifier>").append(this.metadata)
				.append("</metadata><manifest>");
		for (final Item item : this.items) {
			opf.append("<item id=\"").append(item.id()).append("\" href=\"").append(item.href())
					.append("\" media-type=\"")
					.append(item.xhtml() != null ? "application/xhtml+xml" : mediaType(item.href())).append("\"/>");
		}
		int n = 0;
		for (final String href : this.files.keySet()) {
			opf.append("<item id=\"f").append(++n).append("\" href=\"").append(href).append("\" media-type=\"")
					.append(mediaType(href)).append("\"/>");
		}
		opf.append("</manifest><spine");
		if (this.progression != null) {
			opf.append(" page-progression-direction=\"").append(this.progression).append('"');
		}
		opf.append('>');
		for (final Item item : this.items) {
			opf.append("<itemref idref=\"").append(item.id()).append('"');
			if (item.properties() != null) {
				opf.append(" properties=\"").append(item.properties()).append('"');
			}
			opf.append("/>");
		}
		opf.append("</spine></package>");

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			// OCF: mimetype comes first and is stored uncompressed.
			final byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
			final ZipEntry first = new ZipEntry("mimetype");
			first.setMethod(ZipEntry.STORED);
			first.setSize(mime.length);
			first.setCompressedSize(mime.length);
			final CRC32 crc = new CRC32();
			crc.update(mime);
			first.setCrc(crc.getValue());
			zip.putNextEntry(first);
			zip.write(mime);
			zip.closeEntry();
			entry(zip, "META-INF/container.xml", ("<?xml version=\"1.0\"?>"
					+ "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
					+ "<rootfiles><rootfile full-path=\"OEBPS/content.opf\""
					+ " media-type=\"application/oebps-package+xml\"/></rootfiles></container>")
					.getBytes(StandardCharsets.UTF_8));
			entry(zip, "OEBPS/content.opf", opf.toString().getBytes(StandardCharsets.UTF_8));
			for (final Item item : this.items) {
				entry(zip, "OEBPS/" + item.href(),
						item.xhtml() != null ? item.xhtml().getBytes(StandardCharsets.UTF_8) : item.data());
			}
			for (final Map.Entry<String, byte[]> file : this.files.entrySet()) {
				entry(zip, "OEBPS/" + file.getKey(), file.getValue());
			}
		}
		return bytes.toByteArray();
	}

	private static String mediaType(final String href) {
		if (href.endsWith(".css")) {
			return "text/css";
		}
		if (href.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (href.endsWith(".png")) {
			return "image/png";
		}
		if (href.endsWith(".otf")) {
			return "font/otf";
		}
		return "application/xhtml+xml";
	}

	private static void entry(final ZipOutputStream zip, final String name, final byte[] data) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	/** The result of a conversion: the output files in order and the messages. */
	static final class Converted {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();
		final List<String> messages = new ArrayList<>();

		byte[] first() {
			return this.data.get(this.order.get(0)).toByteArray();
		}
	}

	/**
	 * Converts the book. {@code properties} alternates names and values.
	 *
	 * @throws TranscoderException when the conversion fails
	 */
	Converted convert(final String... properties) throws Exception {
		final Converted converted = new Converted();
		final File file = File.createTempFile("epub-books", ".epub");
		try {
			java.nio.file.Files.write(file.toPath(), this.build());
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new Results() {
					@Override
					public boolean hasNext() {
						return true;
					}

					@Override
					public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
						final String uri = metadata.getURI().toString();
						final ByteArrayOutputStream out = new ByteArrayOutputStream();
						converted.data.put(uri, out);
						converted.order.add(uri);
						return new StreamFragmentedOutput(out);
					}

					@Override
					public void end() {
						// Nothing to do.
					}
				});
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						converted.messages.add(Integer.toHexString(code) + " " + message);
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				for (int i = 0; i + 1 < properties.length; i += 2) {
					session.property(properties[i], properties[i + 1]);
				}
				session.transcode(file.toURI());
			} finally {
				session.close();
			}
		} finally {
			file.delete();
		}
		return converted;
	}
}
