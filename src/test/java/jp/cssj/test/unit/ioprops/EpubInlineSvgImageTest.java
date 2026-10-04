package jp.cssj.test.unit.ioprops;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

import javax.imageio.ImageIO;

import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * EPUB の項目の中のインライン SVG から、相対 URI で画像を参照できることを固定します
 * (2026-10-04、TECH-20261003-004 の⑲)。
 *
 * <p>
 * 項目の文書 URI は書庫の中のパス(EPUB/text/book.xhtml)で相対。インライン SVG は Batik へ
 * 合成 URI で渡すので、{@code <image xlink:href="../images/x.png">}が合成 URI の下で解決され、
 * 取得が拒まれて(2814)画像が抜けていた。同じ文書の{@code <img>}は出ていた。
 * </p>
 */
public class EpubInlineSvgImageTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static byte[] redPng() throws Exception {
		final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		final java.awt.Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, 8, 8);
		g.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static byte[] epub() throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
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
					+ "<rootfiles><rootfile full-path=\"EPUB/package.opf\""
					+ " media-type=\"application/oebps-package+xml\"/></rootfiles></container>")
					.getBytes(StandardCharsets.UTF_8));
			entry(zip, "EPUB/package.opf", ("<?xml version=\"1.0\"?>"
					+ "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\">"
					+ "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
					+ "<dc:identifier id=\"uid\">urn:uuid:svg-image</dc:identifier>"
					+ "<dc:title>t</dc:title><dc:language>ja</dc:language></metadata><manifest>"
					+ "<item id=\"book\" href=\"text/book.xhtml\" media-type=\"application/xhtml+xml\" properties=\"svg\"/>"
					+ "<item id=\"red\" href=\"images/red.png\" media-type=\"image/png\"/>"
					+ "</manifest><spine><itemref idref=\"book\"/></spine></package>").getBytes(StandardCharsets.UTF_8));
			entry(zip, "EPUB/text/book.xhtml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title>"
					+ "<style type=\"text/css\">@page{size:100pt 100pt;margin:0}body{margin:0}svg{display:block}</style>"
					+ "</head><body>"
					+ "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\""
					+ " width=\"100pt\" height=\"100pt\" viewBox=\"0 0 100 100\">"
					+ "<image xlink:href=\"../images/red.png\" x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
					+ "</svg></body></html>").getBytes(StandardCharsets.UTF_8));
			entry(zip, "EPUB/images/red.png", redPng());
		}
		return bytes.toByteArray();
	}

	private static void entry(final ZipOutputStream zip, final String name, final byte[] data) throws Exception {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	private static final class CapturingResults implements Results {
		final Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();
		final List<String> order = new ArrayList<>();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			final String uri = metadata.getURI().toString();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			this.data.put(uri, out);
			this.order.add(uri);
			return new StreamFragmentedOutput(out);
		}

		@Override
		public void end() {
			// 何もしない
		}
	}

	public void testRelativeImageInInlineSvgIsLoaded() throws Exception {
		final File file = File.createTempFile("svg-image-in-epub", ".epub");
		final List<String> messages = new ArrayList<>();
		final CapturingResults results = new CapturingResults();
		try {
			java.nio.file.Files.write(file.toPath(), epub());
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(results);
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						messages.add(Integer.toHexString(code) + " " + message);
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("output.type", "image/png");
				session.transcode(file.toURI());
			} finally {
				session.close();
			}
		} finally {
			file.delete();
		}
		assertFalse(results.order.toString(), results.order.isEmpty());
		final BufferedImage page = ImageIO
				.read(new ByteArrayInputStream(results.data.get(results.order.get(0)).toByteArray()));
		final int rgb = page.getRGB(page.getWidth() / 2, page.getHeight() / 2) & 0xffffff;
		assertEquals(messages.toString(), 0xff0000, rgb);
	}
}
