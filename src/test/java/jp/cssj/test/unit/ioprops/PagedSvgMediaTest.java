package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code <audio>} and {@code <video>} in paged SVG (2026-10-15, TECH-20261015-005): replaced elements whose place and
 * files go into the page data, so that the cti.li reader can play them.
 */
public class PagedSvgMediaTest extends TestCase {
	private static final Pattern MEDIA = Pattern.compile(
			"\\{\"tag\":\"(audio|video)\",\"sources\":\\[(.*?)\\],\"controls\":(\\w+),\"autoplay\":(\\w+),\"loop\":(\\w+),"
					+ "\"muted\":(\\w+),\"poster\":(\\w+)(?:,\"label\":\"([^\"]*)\")?,\"bounds\":\\[([-\\d.]+),([-\\d.]+),([-\\d.]+),([-\\d.]+)\\]\\}");
	private static final Pattern SOURCE = Pattern.compile("\\{\"src\":\"([^\"]+)\",\"type\":\"([^\"]+)\"\\}");

	private File dir;
	private byte[] mp3, mp4;

	@Override
	protected void setUp() throws Exception {
		this.dir = Files.createTempDirectory("paged-svg-media").toFile();
		this.mp3 = new byte[2048];
		for (int i = 0; i < this.mp3.length; ++i) {
			this.mp3[i] = (byte) (i * 7);
		}
		this.mp4 = new byte[4096];
		for (int i = 0; i < this.mp4.length; ++i) {
			this.mp4[i] = (byte) (i * 13 + 1);
		}
		Files.write(new File(this.dir, "voice.mp3").toPath(), this.mp3);
		Files.write(new File(this.dir, "clip.mp4").toPath(), this.mp4);
	}

	@Override
	protected void tearDown() throws Exception {
		for (final File f : this.dir.listFiles()) {
			f.delete();
		}
		this.dir.delete();
	}

	private static final String HTML = """
			<!DOCTYPE html><html><head><meta charset="UTF-8"><style>
			@page { size: 400pt 600pt; margin: 0 } body { margin: 0; font: 10pt serif } div { margin: 0 }
			.icon { width: 20pt; height: 10pt; padding: 1pt; border: 2pt solid black }
			</style></head><body>
			<div><audio controls src="voice.mp3" class="icon" title="Hello">FALLBACK-A</audio></div>
			<div><audio src="voice.mp3">FALLBACK-B</audio></div>
			<div><audio controls autoplay loop muted src="voice.mp3"></audio></div>
			<div><video controls width="160" height="90"><source src="missing.webm" type="video/webm">
			<source src="clip.mp4" type="video/mp4; codecs=avc1">FALLBACK-V</video></div>
			</body></html>
			""";

	public void testPageDataAndFiles() throws Exception {
		final Map<String, byte[]> entries = this.convert(HTML);
		final String page = new String(entries.get("pages/0001.json"), StandardCharsets.UTF_8);
		final List<String[]> found = new ArrayList<>();
		final Matcher all = MEDIA.matcher(page);
		while (all.find()) {
			final String[] g = new String[13];
			for (int i = 1; i <= 12; ++i) {
				g[i] = all.group(i);
			}
			found.add(g);
		}
		assertEquals("controls の無い audio は出さず、3 つ: " + page, 3, found.size());

		// The icon: its content box (20x10pt inside 1pt padding and 2pt border), the title as label.
		final String[] icon = found.get(0);
		assertEquals("audio", icon[1]);
		assertEquals("Hello", icon[8]);
		assertEquals("ポスターなし", "false", icon[7]);
		assertEquals("内容の箱の左", 3, Double.parseDouble(icon[9]), 0.01);
		assertEquals("内容の箱の幅", 20, Double.parseDouble(icon[11]) - Double.parseDouble(icon[9]), 0.01);
		assertEquals("内容の箱の高さ", 10, Double.parseDouble(icon[12]) - Double.parseDouble(icon[10]), 0.01);
		final Matcher src = SOURCE.matcher(icon[2]);
		assertTrue(src.find());
		final String mp3Uri = "assets/media/" + sha256(this.mp3) + ".mp3";
		assertEquals("頁から見た相対参照", "../" + mp3Uri, src.group(1));
		assertEquals("audio/mpeg", src.group(2));

		// The plain player: the default size of audio controls (300x54px), its flags.
		final String[] plain = found.get(1);
		assertEquals("既定の幅", 225, Double.parseDouble(plain[11]) - Double.parseDouble(plain[9]), 0.01);
		assertEquals("既定の高さ", 40.5, Double.parseDouble(plain[12]) - Double.parseDouble(plain[10]), 0.01);
		assertEquals(List.of("true", "true", "true", "true"), List.of(plain[3], plain[4], plain[5], plain[6]));

		// The video: width/height attributes, only the readable source, the type without parameters.
		final String[] video = found.get(2);
		assertEquals("video", video[1]);
		assertEquals("width 属性", 120, Double.parseDouble(video[11]) - Double.parseDouble(video[9]), 0.01);
		final Matcher vsrc = SOURCE.matcher(video[2]);
		assertTrue(vsrc.find());
		assertEquals("../assets/media/" + sha256(this.mp4) + ".mp4", vsrc.group(1));
		assertEquals("type は書いたまま(codecs も)", "video/mp4; codecs=avc1", vsrc.group(2));
		assertFalse("読めない source は出さない: " + video[2], vsrc.find());

		// One file per content, listed in the manifest.
		assertTrue("mp3 の資材: " + entries.keySet(), java.util.Arrays.equals(this.mp3, entries.get(mp3Uri)));
		assertEquals("同じファイルは 1 回: " + entries.keySet(), 2,
				entries.keySet().stream().filter(k -> k.startsWith("assets/media/")).count());
		final String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8);
		assertTrue("manifest の media: " + manifest, manifest.contains("\"uri\":\"" + mp3Uri + "\",\"sha256\":\""
				+ sha256(this.mp3) + "\",\"mediaType\":\"audio/mpeg\",\"bytes\":" + this.mp3.length));

		// Fallback content is not laid out, as in a browser; it names the element when nothing else does.
		final String text = page.substring(page.indexOf("\"text\":["), page.indexOf("\"links\":["));
		assertFalse("代替の文字は組まない: " + text, text.contains("FALLBACK"));
		assertEquals("代替の文字は名前に", "FALLBACK-V", video[8]);
	}

	/** A file larger than input.resource-size-limit is not emitted (the element keeps its place). */
	public void testResourceSizeLimit() throws Exception {
		final Map<String, byte[]> entries = this.convert(HTML, "input.resource-size-limit", "1000");
		assertFalse("上限を超えたファイルは出さない: " + entries.keySet(),
				entries.keySet().stream().anyMatch(k -> k.startsWith("assets/media/")));
		assertFalse(new String(entries.get("pages/0001.json"), StandardCharsets.UTF_8).contains("\"media\""));
	}

	/** Without media the page data and manifest have no media key (their bytes are unchanged). */
	public void testNoMediaNoKey() throws Exception {
		final Map<String, byte[]> entries = this.convert(
				"<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><p>X</p></body></html>");
		assertFalse(new String(entries.get("pages/0001.json"), StandardCharsets.UTF_8).contains("\"media\""));
		assertFalse(new String(entries.get("manifest.json"), StandardCharsets.UTF_8).contains("\"media\""));
	}

	private static String sha256(final byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private Map<String, byte[]> convert(final String html, final String... properties) throws Exception {
		final File input = new File(this.dir, "input.html");
		Files.writeString(input.toPath(), html);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/vnd.copper.paged-svg+zip");
			for (int i = 0; i + 1 < properties.length; i += 2) {
				session.property(properties[i], properties[i + 1]);
			}
			CTISessionHelper.transcodeFile(session, input, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		final Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
				entries.put(e.getName(), zip.readAllBytes());
			}
		}
		assertFalse("ZIPが空です", entries.isEmpty());
		return entries;
	}
}
