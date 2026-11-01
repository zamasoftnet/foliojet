package jp.cssj.test.unit.displaylist;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.Results;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Measures how time, allocation and live memory grow with the size of each document shape, and how throughput grows
 * with concurrent conversions (2026-10-31, performance audit). Runs only when asked:
 *
 * <ul>
 * <li>{@code -Dfoliojet.scaling=all|shape,shape} converts each shape at its base size and at {@code scalingFactor}
 * (4) times it, and prints the growth exponent. {@code -Dfoliojet.scalingScale=0.5} scales the base sizes,
 * {@code -Dfoliojet.scalingJfr=true} samples the larger run with JFR for every shape (otherwise only for exponents
 * over 1.25), {@code -Dfoliojet.scalingHisto=true} takes a class histogram halfway through the larger run.</li>
 * <li>{@code -Dfoliojet.concurrency=shape} converts that shape on 1, 2, 4, 8, 16 threads and records lock
 * contention at 16.</li>
 * <li>{@code -Dfoliojet.retention=shape} repeats a shape in one JVM: what conversions leave behind, and whether it adds
 * up. {@code -Dfoliojet.digest=output.type} digests the outputs that the digest ledgers do not see, to compare two
 * builds.</li>
 * </ul>
 */
public class ScalingProbeTest extends TestCase {
	private static final String TEXT = "吾輩は猫である。名前はまだ無い。どこで生れたかとんと見当がつかぬ。"
			+ "The quick brown fox jumps over the lazy dog, again and again. ";

	/**
	 * A document shape. {@code props} are I/O properties of the conversion; {@code input}, if not null, writes the input
	 * for a size itself (an EPUB, an XML document with its style sheet), and {@code style} and {@code body} are unused.
	 */
	private record Shape(String name, int base, IntFunction<String> style, IntFunction<String> body,
			Map<String, String> props, Input input) {
		Shape(final String name, final int base, final IntFunction<String> style, final IntFunction<String> body) {
			this(name, base, style, body, Map.of(), null);
		}

		Shape(final String name, final int base, final IntFunction<String> style, final IntFunction<String> body,
				final Map<String, String> props) {
			this(name, base, style, body, props, null);
		}

		Shape(final String name, final int base, final Map<String, String> props, final Input input) {
			this(name, base, null, null, props, input);
		}
	}

	@FunctionalInterface
	private interface Input {
		File write(int n) throws Exception;
	}

	private static String repeat(final int n, final IntFunction<String> item) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < n; ++i) s.append(item.apply(i));
		return s.toString();
	}

	private static String nest(final int depth, final String open, final String inner, final String close) {
		return open.repeat(depth) + inner + close.repeat(depth);
	}

	private static final Map<Integer, String> PNG = new HashMap<>();

	/** A distinct 8x8 PNG for each i, as a data URI. */
	private static String png(final int i) {
		return PNG.computeIfAbsent(i, k -> {
			try {
				final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
				for (int y = 0; y < 8; ++y) for (int x = 0; x < 8; ++x) image.setRGB(x, y, (k * 7919 + x * 31 + y) & 0xFFFFFF);
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(image, "png", out);
				return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/**
	 * A distinct {@code size}-square PNG for each i in a file next to the documents, as a relative URI: images too big
	 * for data URIs in the document. The pixels are a gradient with some noise, so that compression neither vanishes nor
	 * dominates. {@code alpha} draws a disc on a transparent ground, for {@code shape-outside}.
	 */
	private static String imageFile(final int i, final int size, final boolean alpha) {
		final String name = "img/" + size + (alpha ? "a-" : "-") + i + ".png";
		final File file = new File("local/scaling/" + name);
		if (!file.exists()) {
			try {
				file.getParentFile().mkdirs();
				final Random random = new Random(i);
				final BufferedImage image = new BufferedImage(size, size,
						alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
				final double r = size * (0.3 + 0.2 * random.nextDouble());
				for (int y = 0; y < size; ++y) {
					for (int x = 0; x < size; ++x) {
						final int c = (x * 255 / size) << 16 | (y * 255 / size) << 8 | (i * 37 + random.nextInt(16)) & 0xFF;
						final boolean inside = Math.hypot(x - size / 2.0, y - size / 2.0) < r;
						image.setRGB(x, y, alpha ? (inside ? 0xFF000000 | c : 0) : c);
					}
				}
				ImageIO.write(image, "png", file);
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		}
		return name;
	}

	private static final String BARLOW = "files/unittest/1080-FONT/BarlowCondensed-Bold.woff2";

	private static final String IPAM = "files/unittest/1080-FONT/ipam.otf";

	/** {@code count} kanji from the {@code from}-th of the unified block, for documents that use many glyphs. */
	private static String kanji(final int from, final int count) {
		final StringBuilder s = new StringBuilder(count);
		for (int i = 0; i < count; ++i) s.append((char) (0x4E00 + (from + i) % 20000));
		return s.toString();
	}

	private static void store(final ZipOutputStream zip, final String name, final byte[] data, final boolean stored)
			throws Exception {
		final ZipEntry entry = new ZipEntry(name);
		if (stored) {
			final CRC32 crc = new CRC32();
			crc.update(data);
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(data.length);
			entry.setCompressedSize(data.length);
			entry.setCrc(crc.getValue());
		}
		zip.putNextEntry(entry);
		zip.write(data);
		zip.closeEntry();
	}

	/**
	 * An EPUB of {@code chapters} spine items, each with a bit of text in IPA Mincho on {@code glyphs} kanji of its own
	 * and {@code refs} references to targets in other chapters.
	 */
	private static File epub(final String name, final int chapters, final int glyphs, final int refs) throws Exception {
		final File file = new File("local/scaling/" + name + "-" + chapters + ".epub");
		file.getParentFile().mkdirs();
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
			store(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII), true);
			store(zip, "META-INF/container.xml",
					("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
							+ "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
							+ "</rootfiles></container>").getBytes(StandardCharsets.UTF_8), false);
			final StringBuilder manifest = new StringBuilder(), spine = new StringBuilder(), nav = new StringBuilder();
			for (int i = 0; i < chapters; ++i) {
				manifest.append("<item id=\"c").append(i).append("\" href=\"c").append(i)
						.append(".xhtml\" media-type=\"application/xhtml+xml\"/>");
				spine.append("<itemref idref=\"c").append(i).append("\"/>");
				nav.append("<li><a href=\"c").append(i).append(".xhtml\">Chapter ").append(i).append("</a></li>");
				final int chapter = i;
				final String links = repeat(refs, r -> "<p><a href=\"c" + (chapter + r + 1) % chapters + ".xhtml#t" + r + "\">see</a></p>");
				store(zip, "OEBPS/c" + i + ".xhtml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
						+ "<head><title>c</title><style>@font-face{font-family:m;src:url(ipam.otf)}body{font-family:m}"
						+ "a::after{content:' p.' target-counter(attr(href),page)}</style></head><body><h1>Chapter " + i + "</h1><p>"
						+ kanji(i * 97, glyphs) + "</p>" + repeat(10, p -> "<p>" + TEXT + "</p>") + links
						+ repeat(refs, r -> "<p id=\"t" + r + "\">target</p>") + "</body></html>").getBytes(StandardCharsets.UTF_8), false);
			}
			store(zip, "OEBPS/nav.xhtml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\" "
					+ "xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>n</title></head><body><nav epub:type=\"toc\"><ol>" + nav
					+ "</ol></nav></body></html>").getBytes(StandardCharsets.UTF_8), false);
			store(zip, "OEBPS/ipam.otf", Files.readAllBytes(Path.of(IPAM)), false);
			store(zip, "OEBPS/content.opf", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" "
					+ "version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
					+ "<dc:identifier id=\"id\">scaling</dc:identifier><dc:title>t</dc:title><dc:language>ja</dc:language></metadata><manifest>"
					+ "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
					+ "<item id=\"font\" href=\"ipam.otf\" media-type=\"font/otf\"/>" + manifest + "</manifest><spine>" + spine
					+ "</spine></package>").getBytes(StandardCharsets.UTF_8), false);
		}
		return file;
	}

	/** An XML document transformed by a style sheet that reads a {@code document()} of {@code n} items (CSS-08). */
	private static File xsltDocument(final int n) throws Exception {
		write("xslt-data-" + n, ".xml", "<items>" + repeat(n, i -> "<item>" + i + " " + TEXT + "</item>") + "</items>");
		write("xslt-" + n, ".xsl", "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
				+ "<xsl:template match=\"/\"><html><body><xsl:for-each select=\"document('xslt-data-" + n + ".xml')/items/item[position() mod 100 = 0]\">"
				+ "<p><xsl:value-of select=\".\"/></p></xsl:for-each></body></html></xsl:template></xsl:stylesheet>");
		return write("xslt-" + n, ".xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><?xml-stylesheet type=\"text/xsl\" href=\"xslt-" + n + ".xsl\"?><root/>");
	}

	private static final String PNG_OUT = "image/png", PAGED_SVG = "application/vnd.copper.paged-svg";

	private static final List<Shape> SHAPES = List.of(
			new Shape("paras", 2000, n -> "", n -> repeat(n, i -> "<p>" + i + " " + TEXT + TEXT + "</p>")),
			new Shape("longpara", 20000, n -> "", n -> "<p>" + repeat(n, i -> "word" + i + " ") + "</p>"),
			new Shape("tableAuto", 4000, n -> "td{border:0.5pt solid}",
					n -> "<table>" + repeat(n, i -> "<tr><td>" + i + "</td><td>" + TEXT + "</td><td>" + i * 7 + "</td></tr>") + "</table>"),
			new Shape("tableFixed", 4000, n -> "table{table-layout:fixed;width:100%}td{border:0.5pt solid}",
					n -> "<table>" + repeat(n, i -> "<tr><td>" + i + "</td><td>" + TEXT + "</td><td>" + i * 7 + "</td></tr>") + "</table>"),
			new Shape("tableCollapsed", 4000, n -> "table{border-collapse:collapse}td{border:0.5pt solid}",
					n -> "<table>" + repeat(n, i -> "<tr><td>" + i + "</td><td>" + TEXT + "</td><td>" + i * 7 + "</td></tr>") + "</table>"),
			new Shape("tbodyHeight", 2000, n -> "table{table-layout:fixed;width:100%;border-collapse:collapse}tbody{height:1pt}td{border:0.5pt solid}",
					n -> "<table><tbody>" + repeat(n, i -> "<tr><td>" + i + "</td><td>row</td></tr>") + "</tbody></table>"),
			new Shape("inlineStyles", 3000, n -> "",
					n -> "<table>" + repeat(n, i -> "<tr>" + repeat(4, c -> "<td style='color:#333;padding:1pt 2pt;border:0.5pt solid #999;text-align:right;font-size:9pt'>"
							+ i + "</td>") + "</tr>") + "</table>"),
			new Shape("whereRules", 1000, n -> repeat(n, i -> ":where(.c" + i + "){color:#" + String.format("%06x", i * 2654435 & 0xFFFFFF) + "}"),
					n -> repeat(n, i -> "<p class=c" + i + ">" + i + "</p>")),
			new Shape("vars", 3000, n -> ":root{--c:#123456;--m:2pt;--s:10pt;--l:1.4;--b:0.5pt solid #ccc}p{color:var(--c);margin:var(--m) 0;font-size:var(--s);line-height:var(--l);border-bottom:var(--b)}",
					n -> repeat(n, i -> "<p>" + i + " " + TEXT + "</p>")),
			new Shape("hyphens", 2000, n -> ".h{hyphens:auto;text-align:justify}",
					n -> "<div lang=en class=h>" + repeat(n, i -> "<p>" + i + " Internationalization considerations notwithstanding, "
							+ "the extraordinarily comprehensive documentation characterizes incomprehensibilities.</p>") + "</div>"),
			new Shape("tableCols", 60, n -> "td{border:0.5pt solid;font-size:5pt}",
					n -> "<table>" + repeat(30, r -> "<tr>" + repeat(n, c -> "<td>" + r + "." + c + "</td>") + "</tr>") + "</table>"),
			new Shape("bigCell", 1500, n -> "td{border:0.5pt solid}",
					n -> "<table><tr><td>" + repeat(n, i -> "<p>" + i + " " + TEXT + "</p>") + "</td></tr></table>"),
			new Shape("nestedDiv", 400, n -> "div{margin-left:0.01pt}.x div p{color:#333}",
					n -> "<div class=x>" + nest(n, "<div><p>level</p>", "<p>" + TEXT + "</p>", "</div>") + "</div>"),
			new Shape("nestedTable", 6, n -> "td{border:0.5pt solid}",
					n -> nest(n, "<table><tr><td>a</td><td>", TEXT, "</td></tr></table>")),
			new Shape("floats", 1000, n -> ".f{float:left;width:20pt;height:12pt;background:#ccc}",
					n -> repeat(n, i -> "<p><span class=f></span>" + i + " " + TEXT + "</p>")),
			new Shape("footnotes", 800, n -> ".fn{float:footnote}",
					n -> repeat(n, i -> "<p>" + i + " " + TEXT + "<span class=fn>note " + i + "</span></p>")),
			new Shape("pageFloats", 300, n -> ".t{float:top;height:30pt;background:#eee}",
					n -> repeat(n, i -> "<p>" + i + " " + TEXT + TEXT + "</p><div class=t>figure " + i + "</div>")),
			new Shape("pages", 1000, n -> ".b{break-before:page}", n -> repeat(n, i -> "<div class=b>page " + i + "</div>")),
			new Shape("nthChild", 3000,
					n -> "p:nth-child(3n+1){color:red}p:nth-last-child(2n){color:blue}p:last-of-type{font-weight:bold}p:nth-of-type(5n){font-style:italic}",
					n -> "<div>" + repeat(n, i -> "<p>" + i + "</p>") + "</div>"),
			new Shape("generalSibling", 3000, n -> ".a~p{color:red}.a~.z{color:blue}",
					n -> "<div><p class=a>a</p>" + repeat(n, i -> "<p>" + i + "</p>") + "</div>"),
			new Shape("rules", 1000, n -> repeat(n, i -> ".c" + i + "{color:#" + String.format("%06x", i * 2654435 & 0xFFFFFF) + "}"),
					n -> repeat(n, i -> "<p class=c" + i + ">" + i + "</p>")),
			new Shape("spans", 5000, n -> "", n -> "<p>" + repeat(n, i -> "<span>w" + i + " </span>") + "</p>"),
			new Shape("ruby", 1500, n -> "body{writing-mode:vertical-rl}",
					n -> repeat(n / 10, p -> "<p>" + repeat(10, i -> "<ruby>漢字<rt>かんじ</rt></ruby>の" + TEXT.substring(0, 16)) + "</p>")),
			new Shape("vertical", 1500, n -> "body{writing-mode:vertical-rl}", n -> repeat(n, i -> "<p>" + i + " " + TEXT + "</p>")),
			new Shape("multicol", 2000, n -> ".m{column-count:3}", n -> "<div class=m>" + repeat(n, i -> "<p>" + i + " " + TEXT + "</p>") + "</div>"),
			new Shape("flex", 3000, n -> ".f{display:flex;flex-wrap:wrap}.f>div{width:80pt}",
					n -> "<div class=f>" + repeat(n, i -> "<div>item " + i + "</div>") + "</div>"),
			new Shape("grid", 3000, n -> ".g{display:grid;grid-template-columns:repeat(4,1fr)}",
					n -> "<div class=g>" + repeat(n, i -> "<div>item " + i + "</div>") + "</div>"),
			new Shape("targetCounter", 500, n -> "a::after{content:' p.' target-counter(attr(href),page)}.b{break-before:page}",
					n -> repeat(n, i -> "<p><a href=#t" + i + ">ref " + i + "</a></p>") + repeat(n, i -> "<p class=" + (i % 10 == 0 ? "b" : "x")
							+ " id=t" + i + ">target " + i + " " + TEXT + "</p>")),
			new Shape("list", 3000, n -> "", n -> "<ol>" + repeat(n, i -> "<li>" + TEXT + "</li>") + "</ol>"),
			new Shape("absolute", 1000, n -> ".a{position:absolute}",
					n -> repeat(n, i -> "<div class=a style='top:" + i % 700 + "pt;left:" + i % 400 + "pt'>a" + i + "</div>")),
			new Shape("images", 600, n -> "img{width:10pt;height:10pt}", n -> "<p>" + repeat(n, i -> "<img src='" + png(i) + "'> ") + "</p>"),
			new Shape("stringSet", 400, n -> "h2{string-set:x content();break-before:page}@page{@top-center{content:string(x)}}",
					n -> repeat(n, i -> "<h2>Section " + i + "</h2><p>" + TEXT + TEXT + "</p>")),
			new Shape("links", 3000, n -> "", n -> repeat(n / 10, p -> "<p>" + repeat(10, i -> "<a href='https://example.com/" + p + "/" + i + "'>link" + i + "</a> ") + "</p>")),
			// The paths the audit of 2026-10-31 did not reach (its sections 5 and 6): rare paths and outputs other than PDF
			new Shape("tiny", 1, n -> "", n -> repeat(n, i -> "<p>" + TEXT + "</p>")),
			new Shape("sameId", 8000, n -> "a::after{content:target-counter(attr(href),page)}",
					n -> "<p><a href=#x>first</a></p>" + repeat(n, i -> "<p id=x>" + i + "</p>")),
			new Shape("sameIdPass2", 8000, n -> "a::after{content:target-counter(attr(href),page)}",
					n -> "<p><a href=#x>first</a></p>" + repeat(n, i -> "<p id=x>" + i + "</p>"), Map.of("processing.pass-count", "2")),
			new Shape("cascadeSort", 128, n -> repeat(n, i -> "p{margin-left:" + i % 3 + "pt}"), n -> repeat(2000, i -> "<p>" + i + "</p>")),
			new Shape("xsltDocument", 8000, Map.of(), ScalingProbeTest::xsltDocument),
			new Shape("svgSmall", 1000, n -> "svg{width:30pt;height:30pt}",
					n -> repeat(n, i -> "<p><svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'><circle cx='5' cy='5' r='" + (i % 5 + 1)
							+ "' fill='red'/><path d='M0 0L10 " + i % 10 + "'/></svg></p>")),
			new Shape("svgBig", 4000, n -> "svg{width:400pt;height:400pt}",
					n -> "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1000 1000'>" + repeat(n, i -> "<rect x='" + i % 1000 + "' y='" + i / 10 % 1000
							+ "' width='5' height='5' fill='#" + String.format("%06x", i * 2654435 & 0xFFFFFF) + "'/>") + "</svg>"),
			new Shape("mathml", 400, n -> "", n -> repeat(n, i -> "<p><math xmlns='http://www.w3.org/1998/Math/MathML'><mfrac><mi>x</mi><mn>" + i
					+ "</mn></mfrac><mo>+</mo><msqrt><mi>y</mi></msqrt></math></p>")),
			new Shape("forms", 1000, n -> "", n -> "<form>" + repeat(n, i -> "<p><label>L" + i + " <input name=a" + i
					+ " value=v></label><select><option>a<option>b</select><textarea>t</textarea><button>b</button><input type=checkbox></p>") + "</form>"),
			new Shape("manyFonts", 64, n -> repeat(n, i -> "@font-face{font-family:f" + i + ";src:url(../../" + BARLOW + ")}"),
					n -> repeat(n, i -> "<p style='font-family:f" + i + "'>Hello " + i + "</p>")),
			new Shape("subsetFont", 1000, n -> "@font-face{font-family:m;src:url(../../" + IPAM + ")}body{font-family:m}", n -> "<p>" + kanji(0, n) + "</p>"),
			new Shape("bigImages", 150, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(i, 200, false) + "'></p>")),
			new Shape("sameImage", 1000, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(0, 200, false) + "'></p>")),
			new Shape("shapeImage", 100, n -> ".s{float:left;width:80pt;height:80pt;shape-margin:6pt}",
					n -> repeat(n, i -> "<p><img class=s style=\"shape-outside:url('" + imageFile(i, 400, true) + "')\" src='" + imageFile(i, 400, true)
							+ "'>" + TEXT.repeat(8) + "</p>")),
			new Shape("filterImages", 100, n -> "img{width:60pt;height:60pt;filter:blur(2px)}", n -> repeat(n, i -> "<p><img src='" + imageFile(i, 200, false) + "'></p>")),
			new Shape("filterBoxes", 100, n -> ".f{filter:blur(2px);width:100pt;height:50pt;background:#39c}", n -> repeat(n, i -> "<div class=f>" + i + "</div>")),
			new Shape("boxShadow", 300, n -> ".f{box-shadow:2pt 2pt 8pt #333;width:100pt;height:30pt;margin:8pt}", n -> repeat(n, i -> "<div class=f>" + i + "</div>")),
			new Shape("textShadow", 300, n -> "p{text-shadow:1pt 1pt 3pt #333}", n -> repeat(n, i -> "<p>" + i + " " + TEXT + "</p>")),
			new Shape("pagesMany", 8000, n -> ".b{break-before:page}", n -> repeat(n, i -> "<div class=b>page " + i + "</div>")),
			new Shape("pngPages", 25, n -> ".b{break-before:page}", n -> repeat(n, i -> "<div class=b><p>" + TEXT.repeat(20) + "</p></div>"), Map.of("output.type", PNG_OUT)),
			new Shape("pagedSvg", 500, n -> ".b{break-before:page}", n -> repeat(n, i -> "<div class=b><p>" + TEXT.repeat(10) + "</p></div>"), Map.of("output.type", PAGED_SVG)),
			new Shape("pagedSvgImages", 150, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(i, 200, false) + "'></p>"), Map.of("output.type", PAGED_SVG)),
			new Shape("pagedSvgSameImage", 1000, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(0, 200, false) + "'></p>"), Map.of("output.type", PAGED_SVG)),
			new Shape("pagedSvgRecode", 150, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(i % 8, 200, false) + "'></p>"),
					Map.of("output.type", PAGED_SVG, "output.paged-svg.image.compression", "jpeg")),
			new Shape("svgOut", 300, n -> "", n -> repeat(n, i -> "<p>" + TEXT + "</p>"), Map.of("output.type", "image/svg+xml")),
			new Shape("pagedSvgLogo", 50, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(0, 1000, false) + "'></p>"), Map.of("output.type", PAGED_SVG)),
			new Shape("pdfLogo", 50, n -> "img{width:60pt;height:60pt}", n -> repeat(n, i -> "<p><img src='" + imageFile(0, 1000, false) + "'></p>")),
			new Shape("parasNoZip", 2000, n -> "", n -> repeat(n, i -> "<p>" + i + " " + TEXT + TEXT + "</p>"), Map.of("output.pdf.compression", "none")),
			new Shape("epubPdf", 25, Map.of(), n -> epub("epubPdf", n, 200, 2)),
			new Shape("epubSvg", 25, Map.of("output.type", PAGED_SVG), n -> epub("epubSvg", n, 200, 2)),
			new Shape("epubSvgGlyphs", 25, Map.of("output.type", PAGED_SVG), n -> epub("epubSvgGlyphs", n, 1500, 2)),
			new Shape("epubSvgRefs", 100, Map.of("output.type", PAGED_SVG, "processing.pass-count", "2"), n -> epub("epubSvgRefs", n, 10, 20)));

	private static String html(final Shape shape, final int n) {
		return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>@page{size:A4;margin:15mm}body{margin:0;font:10pt/1.5 serif}"
				+ shape.style().apply(n) + "</style></head><body>" + shape.body().apply(n) + "</body></html>";
	}

	private static final AtomicLong peakAfterGc = new AtomicLong();
	private static boolean listening;

	private static synchronized void listenToGc() {
		if (listening) return;
		listening = true;
		for (final GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			((NotificationEmitter) gc).addNotificationListener((notification, handback) -> {
				if (!notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) return;
				final var info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
				final long used = info.getGcInfo().getMemoryUsageAfterGc().values().stream().mapToLong(MemoryUsage::getUsed).sum();
				peakAfterGc.accumulateAndGet(used, Math::max);
			}, null, null);
		}
	}

	private static long usedAfterFullGc() {
		System.gc();
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}

	private static long allocated() {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getTotalThreadAllocatedBytes();
	}

	private static File input(final Shape shape, final int n) throws Exception {
		return shape.input() != null ? shape.input().write(n) : write(shape.name() + "-" + n, html(shape, n));
	}

	/** An output that counts what would be written. */
	private static final class CountingStream extends OutputStream {
		long count;

		@Override
		public void write(final int b) {
			++this.count;
		}

		@Override
		public void write(final byte[] b, final int off, final int len) {
			this.count += len;
		}
	}

	/** Takes any number of results (image pages, a paged SVG bundle). */
	private static final class CountingResults implements Results {
		final CountingStream out = new CountingStream();

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			return new StreamFragmentedOutput(this.out);
		}

		@Override
		public void end() {
			// Nothing to do
		}
	}

	private static String mimeType(final File file) {
		final String name = file.getName();
		return name.endsWith(".epub") ? "application/epub+zip" : name.endsWith(".xml") ? "text/xml" : "text/html";
	}

	private static String lastFailure;

	/** One conversion; returns {wall ms, allocated MB, peak live MB over the baseline, retained MB after, output MB, CPU s}. */
	private static double[] convert(final File file, final Map<String, String> props, final long histoAtMillis,
			final StringBuilder histo) throws Exception {
		final long base = usedAfterFullGc();
		peakAfterGc.set(base);
		final long alloc0 = allocated();
		final var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		final long cpu0 = os.getProcessCpuTime();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		final Thread watchdog = new Thread(() -> {
			try {
				if (histoAtMillis > 0) {
					Thread.sleep(histoAtMillis);
					histo.append(classHistogram());
					Thread.sleep(Math.max(0, 120_000L - histoAtMillis));
				} else {
					Thread.sleep(120_000L);
				}
				session.abort(CTISession.ABORT_FORCE);
			} catch (final Exception e) {
				// Finished first
			}
		}, "scaling-watchdog");
		watchdog.setDaemon(true);
		final long t0 = System.nanoTime();
		watchdog.start();
		final CountingResults results = new CountingResults();
		String failure = null;
		try {
			session.setResults(results);
			session.setMessageHandler((code, args, mes) -> {
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			for (final Map.Entry<String, String> e : props.entrySet()) session.property(e.getKey(), e.getValue());
			CTISessionHelper.transcodeFile(session, file, mimeType(file), null);
		} catch (final Exception e) {
			failure = e.toString();
		} finally {
			watchdog.interrupt();
		}
		final double ms = (System.nanoTime() - t0) / 1e6;
		final double allocMb = (allocated() - alloc0) / 1048576.0;
		final double peakMb = (peakAfterGc.get() - base) / 1048576.0;
		final double cpu = (os.getProcessCpuTime() - cpu0) / 1e9;
		session.close();
		final double retainedMb = (usedAfterFullGc() - base) / 1048576.0;
		lastFailure = failure;
		return new double[] { ms, allocMb, peakMb, retainedMb, results.out.count / 1048576.0, cpu };
	}

	private static String classHistogram() {
		try {
			final String all = (String) ManagementFactory.getPlatformMBeanServer().invoke(
					new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
					new Object[] { new String[0] }, new String[] { String[].class.getName() });
			final String[] lines = all.split("\n");
			final StringBuilder s = new StringBuilder();
			for (int i = 0; i < Math.min(lines.length, 32); ++i) s.append("\n[scale]   ").append(lines[i]);
			return s.toString();
		} catch (final Exception e) {
			return "\n[scale]   histogram failed: " + e;
		}
	}

	/** Top frames of the JFR samples: the innermost engine frame and the innermost frame of all. */
	private static String hotFrames(final Path jfr, final String eventName, final boolean weighByDuration) throws Exception {
		final Map<String, Double> ours = new HashMap<>(), leaf = new HashMap<>(), path = new HashMap<>();
		// -Dfoliojet.scalingFrames=net.zamasoft.foliojet.layout: also count the innermost frame under that prefix with
		// its two callers
		final String prefix = System.getProperty("foliojet.scalingFrames");
		double total = 0;
		for (final RecordedEvent e : RecordingFile.readAllEvents(jfr)) {
			if (!e.getEventType().getName().equals(eventName) || e.getStackTrace() == null) continue;
			final double w = weighByDuration ? e.getDuration().toNanos() / 1e6 : 1;
			total += w;
			final List<RecordedFrame> frames = e.getStackTrace().getFrames();
			if (!frames.isEmpty()) leaf.merge(name(frames.get(0)), w, Double::sum);
			for (int i = 0; prefix != null && i < frames.size(); ++i) {
				if (frames.get(i).getMethod().getType().getName().startsWith(prefix)) {
					final StringBuilder p = new StringBuilder(name(frames.get(i)));
					for (int j = i + 1; j < Math.min(frames.size(), i + 3); ++j) p.append(" < ").append(name(frames.get(j)));
					path.merge(p.toString(), w, Double::sum);
					break;
				}
			}
			for (final RecordedFrame f : frames) {
				final String type = f.getMethod().getType().getName();
				if (type.startsWith("net.zamasoft.") || type.startsWith("jp.cssj.")) {
					String key = name(f);
					if (weighByDuration && e.hasField("monitorClass") && e.getClass("monitorClass") != null) {
						key = e.getClass("monitorClass").getName() + " @ " + key;
					}
					ours.merge(key, w, Double::sum);
					break;
				}
			}
		}
		final StringBuilder s = new StringBuilder();
		s.append(String.format("%n[scale]   %s: %.0f %s", eventName, total, weighByDuration ? "ms" : "samples"));
		top(s, "engine", ours, total);
		if (!weighByDuration) top(s, "leaf", leaf, total);
		if (prefix != null) top(s, "path", path, total);
		return s.toString();
	}

	private static String name(final RecordedFrame f) {
		final String type = f.getMethod().getType().getName();
		return type.substring(type.lastIndexOf('.') + 1) + "." + f.getMethod().getName() + ":" + f.getLineNumber();
	}

	private static void top(final StringBuilder s, final String label, final Map<String, Double> counts, final double total) {
		counts.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(8)
				.forEach(e -> s.append(String.format("%n[scale]     %-6s %5.1f%% %s", label, 100 * e.getValue() / total, e.getKey())));	}

	private static File write(final String name, final String html) throws Exception {
		return write(name, ".html", html);
	}

	private static File write(final String name, final String suffix, final String text) throws Exception {
		final File f = new File("local/scaling/" + name + suffix);
		f.getParentFile().mkdirs();
		Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
		return f;
	}

	public void testScaling() throws Exception {
		final String which = System.getProperty("foliojet.scaling");
		if (which == null) return;
		final double scale = Double.parseDouble(System.getProperty("foliojet.scalingScale", "1"));
		final int factor = Integer.getInteger("foliojet.scalingFactor", 4);
		final boolean jfrAll = Boolean.getBoolean("foliojet.scalingJfr"), histo = Boolean.getBoolean("foliojet.scalingHisto");
		listenToGc();
		// Warm up: the first conversions spend most of their time loading classes
		final File warm = write("warm", html(SHAPES.get(0), 300));
		for (int i = 0; i < 3; ++i) convert(warm, Map.of(), 0, null);
		final List<String> rows = new ArrayList<>();
		for (final Shape shape : SHAPES) {
			if (!which.equals("all") && !List.of(which.split(",")).contains(shape.name())) continue;
			final int n = Math.max(1, (int) Math.round(shape.base() * scale)), big = n * factor;
			final File small = input(shape, n), large = input(shape, big);
			final double[] a = convert(small, shape.props(), 0, null), a2 = convert(small, shape.props(), 0, null);
			if (a2[0] < a[0]) System.arraycopy(a2, 0, a, 0, a.length);
			final Recording rec = new Recording();
			rec.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
			rec.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20));
			rec.start();
			final StringBuilder h = new StringBuilder();
			final double[] b = convert(large, shape.props(), histo ? (long) (a[0] * factor * 0.5) : 0, h);
			rec.stop();
			final double k = Math.log(b[0] / a[0]) / Math.log(factor), ka = Math.log(b[1] / a[1]) / Math.log(factor);
			final String row = String.format("[scale] %-14s n=%6d/%7d time %8.0f/%9.0f ms k=%.2f  alloc %7.0f/%8.0f MB k=%.2f  peak %6.0f/%7.0f MB  retained %5.1f/%6.1f MB  out %.1f/%.1f MB  cpu %.1f/%.1f s",
					shape.name(), n, big, a[0], b[0], k, a[1], b[1], ka, a[2], b[2], a[3], b[3], a[4], b[4], a[5], b[5]);
			System.out.println(row);
			rows.add(lastFailure == null ? row : row + "  FAILED " + lastFailure);
			if (jfrAll || k > 1.25 || ka > 1.25) {
				final Path p = Files.createTempFile("scaling", ".jfr");
				rec.dump(p);
				for (final String event : new String[] { "jdk.ExecutionSample", "jdk.NativeMethodSample" }) {
					final String hot = hotFrames(p, event, false);
					System.out.println(hot.substring(1));
					rows.add(hot.substring(1));
				}
				Files.delete(p);
			}
			rec.close();
			if (h.length() > 0) {
				System.out.println(h.substring(1));
				rows.add(h.substring(1));
			}
			Files.delete(small.toPath());
			Files.delete(large.toPath());
		}
		Files.write(new File("local/scaling/result.txt").toPath(), rows, StandardCharsets.UTF_8);
	}

	/**
	 * {@code -Dfoliojet.retention=shape}: converts the shape {@code foliojet.retentionRuns} times (8) in one JVM and
	 * records the heap after a full GC each time, then a class histogram: what conversions leave behind, and whether it
	 * adds up. The result is in {@code local/scaling/retention.txt}.
	 */
	public void testRetention() throws Exception {
		final String which = System.getProperty("foliojet.retention");
		if (which == null) return;
		final Shape shape = SHAPES.stream().filter(s -> s.name().equals(which)).findFirst().orElseThrow();
		final double scale = Double.parseDouble(System.getProperty("foliojet.scalingScale", "1"));
		final File file = input(shape, Math.max(1, (int) Math.round(shape.base() * scale)));
		final List<String> rows = new ArrayList<>();
		rows.add(String.format("[retain] before %.1f MB", usedAfterFullGc() / 1048576.0));
		for (int i = 0; i < Integer.getInteger("foliojet.retentionRuns", 8); ++i) {
			convert(file, shape.props(), 0, null);
			rows.add(String.format("[retain] %s run %d: %.1f MB", which, i, usedAfterFullGc() / 1048576.0));
		}
		rows.add(classHistogram());
		Files.write(new File("local/scaling/retention.txt").toPath(), rows, StandardCharsets.UTF_8);
	}

	/** The SHA-256 of every result of a conversion, by URI. */
	private static final class DigestResults implements Results {
		final Map<String, java.security.DigestOutputStream> digests = new java.util.TreeMap<>();

		private int next;

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public FragmentedOutput nextBuilder(final SourceMetadata metadata) {
			try {
				final var out = new java.security.DigestOutputStream(OutputStream.nullOutputStream(),
						java.security.MessageDigest.getInstance("SHA-256"));
				final URI uri = metadata == null ? null : metadata.getURI();
				this.digests.put(uri == null ? String.format("%06d", this.next++) : uri.toString(), out);
				return new StreamFragmentedOutput(out);
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public void end() {
			// Nothing to do
		}

		String digest() {
			final var all = new StringBuilder();
			this.digests.forEach((uri, out) -> all.append(uri).append(' ')
					.append(java.util.HexFormat.of().formatHex(out.getMessageDigest().digest())).append('\n'));
			return Integer.toHexString(all.toString().hashCode()) + " " + this.digests.size();
		}
	}

	/**
	 * {@code -Dfoliojet.digest=output.type[,output.type]}: converts the shapes that are not PDF and the documents under
	 * {@code files/visual} and {@code files/unittest} to those types and records a digest of all results for each, in
	 * {@code local/scaling/digest.txt}: the digest ledgers and the display list goldens do not see these outputs. Compare
	 * the file of a build before a change with that of a build after it.
	 */
	public void testOutputDigest() throws Exception {
		final String types = System.getProperty("foliojet.digest");
		if (types == null) return;
		final List<String> rows = new ArrayList<>();
		for (final Shape shape : SHAPES) {
			if (!shape.props().containsKey("output.type")) continue;
			rows.add(shape.name() + " " + digest(input(shape, Math.max(1, shape.base() / 5)), shape.props()));
		}
		final List<Path> documents;
		try (var files = Files.walk(Path.of("files"))) {
			documents = files.filter(f -> f.toString().matches(".*/(visual|unittest)/.*\\.x?html?")).sorted().toList();
		}
		for (final String type : types.split(",")) {
			for (final Path document : documents) {
				rows.add(type + " " + document + " " + digest(document.toFile(), Map.of("output.type", type)));
			}
		}
		Files.write(new File("local/scaling/digest.txt").toPath(), rows, StandardCharsets.UTF_8);
	}

	private static String digest(final File file, final Map<String, String> props) throws Exception {
		final DigestResults results = new DigestResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(results);
			session.setMessageHandler((code, args, mes) -> {
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			for (final Map.Entry<String, String> e : props.entrySet()) session.property(e.getKey(), e.getValue());
			CTISessionHelper.transcodeFile(session, file, mimeType(file), null);
			return results.digest();
		} catch (final Exception e) {
			return "failed " + e.getClass().getSimpleName();
		} finally {
			session.close();
		}
	}

	public void testConcurrency() throws Exception {
		final String which = System.getProperty("foliojet.concurrency");
		if (which == null) return;
		final Shape shape = SHAPES.stream().filter(s -> s.name().equals(which)).findFirst().orElseThrow();
		final double scale = Double.parseDouble(System.getProperty("foliojet.scalingScale", "1"));
		final File file = write("conc-" + which, html(shape, Math.max(1, (int) Math.round(shape.base() * scale))));
		final int perThread = Integer.getInteger("foliojet.concurrencyDocs", 4);
		final List<String> rows = new ArrayList<>();
		for (int i = 0; i < 3; ++i) convert(file, shape.props(), 0, null);
		double single = 0;
		final var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		for (final int threads : new int[] { 1, 2, 4, 8, 16 }) {
			Recording rec = null;
			if (threads == 16) {
				rec = new Recording();
				rec.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace();
				rec.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(5)).withStackTrace();
				rec.start();
			}
			final long cpu0 = os.getProcessCpuTime(), t0 = System.nanoTime();
			final List<Thread> workers = new ArrayList<>();
			for (int t = 0; t < threads; ++t) {
				final Thread w = new Thread(() -> {
					for (int d = 0; d < perThread; ++d) {
						try {
							final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
							session.setResults(new CountingResults());
							session.setMessageHandler((code, args, mes) -> {
							});
							session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
							session.property("input.include", "**");
							CTISessionHelper.transcodeFile(session, file, "text/html", null);
							session.close();
						} catch (final Exception e) {
							System.out.println("[conc]   failed: " + e);
						}
					}
				}, "conc-" + t);
				workers.add(w);
				w.start();
			}
			for (final Thread w : workers) w.join();
			final double sec = (System.nanoTime() - t0) / 1e9, cpu = (os.getProcessCpuTime() - cpu0) / 1e9;
			final double perSec = threads * perThread / sec;
			if (threads == 1) single = perSec;
			final String row = String.format("[conc] %-10s threads=%2d docs=%3d %6.1f s  %6.2f docs/s  speedup %5.2f  cpu %5.1f cores",
					which, threads, threads * perThread, sec, perSec, perSec / single, cpu / sec);
			System.out.println(row);
			rows.add(row);
			if (rec != null) {
				rec.stop();
				final Path p = Files.createTempFile("conc", ".jfr");
				rec.dump(p);
				for (final String ev : new String[] { "jdk.JavaMonitorEnter", "jdk.ThreadPark" }) {
					final String hot = hotFrames(p, ev, true);
					System.out.println(hot.substring(1));
					rows.add(hot.substring(1));
				}
				Files.delete(p);
				rec.close();
			}
		}
		Files.write(new File("local/scaling/concurrency-" + which + ".txt").toPath(), rows, StandardCharsets.UTF_8);
	}
}
