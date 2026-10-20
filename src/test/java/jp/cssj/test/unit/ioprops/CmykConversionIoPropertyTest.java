package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code output.pdf.cmyk.neutral-tolerance} and {@code output.pdf.cmyk.ink-limit} reach the CMYK colors of the
 * content stream (2026-10-20, a publishing request: the book's text color #1d1a22 became four-color black, about
 * 304% of ink, in small type).
 */
public class CmykConversionIoPropertyTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final Pattern FILL = Pattern.compile("(-?[\\d.]+) (-?[\\d.]+) (-?[\\d.]+) (-?[\\d.]+) k\\b");

	private static final String TEXT = "<!DOCTYPE html><html><body style='margin:0'>"
			+ "<p style='color:#1d1a22;font-size:9pt'>Text</p></body></html>";

	private static final String DARK = "<!DOCTYPE html><html><body style='margin:0'>"
			+ "<div style='background:#1a0d26;height:20pt'></div></body></html>";

	public void testNeutralToleranceWritesNearBlackTextWithKOnly() throws Exception {
		assertFalse("許容幅が無ければ #1d1a22 は4色の黒", hasKOnly(fills(this.convert(TEXT, Map.of()))));
		final var fills = fills(this.convert(TEXT, Map.of("output.pdf.cmyk.neutral-tolerance", "8")));
		assertTrue("許容幅 8 で #1d1a22 が K だけになる: " + fills, hasKOnly(fills));
		assertFalse("許容幅 7 では K だけにならない",
				hasKOnly(fills(this.convert(TEXT, Map.of("output.pdf.cmyk.neutral-tolerance", "7")))));
	}

	public void testInkLimitBoundsTheTotalOfConvertedColors() throws Exception {
		final var plain = fills(this.convert(DARK, Map.of()));
		assertTrue("上限が無ければ 260% を超える: " + plain, max(plain) > 2.6);
		final var limited = fills(this.convert(DARK, Map.of("output.pdf.cmyk.ink-limit", "260")));
		assertFalse(limited.isEmpty());
		assertTrue("総インキ量が 260% 以下: " + limited, max(limited) <= 2.6 + .015);
	}

	public void testInkLimitBelowOneHundredIsIgnored() throws Exception {
		final var fills = fills(this.convert(DARK, Map.of("output.pdf.cmyk.ink-limit", "50")));
		assertTrue("100 未満は警告して上限なし: " + fills, max(fills) > 2.6);
	}

	private static List<float[]> fills(final String pdf) {
		final var fills = new ArrayList<float[]>();
		final var m = FILL.matcher(pdf);
		while (m.find()) {
			fills.add(new float[] { Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2)),
					Float.parseFloat(m.group(3)), Float.parseFloat(m.group(4)) });
		}
		return fills;
	}

	private static boolean hasKOnly(final List<float[]> fills) {
		for (final var f : fills) {
			if (f[0] == 0 && f[1] == 0 && f[2] == 0 && f[3] > .85f && f[3] < .92f) {
				return true;
			}
		}
		return false;
	}

	private static double max(final List<float[]> fills) {
		var max = 0.0;
		for (final var f : fills) {
			max = Math.max(max, f[0] + f[1] + f[2] + f[3]);
		}
		return max;
	}

	/** Converts with output.color=cmyk and compression disabled; returns the PDF as a string. */
	private String convert(final String html, final Map<String, String> properties) throws Exception {
		final File document = new File("local/unittest/pdf/" + this.getClass().getSimpleName() + ".html");
		document.getParentFile().mkdirs();
		Files.writeString(document.toPath(), html);
		final File out = new File("local/unittest/pdf/" + this.getClass().getSimpleName() + ".pdf");
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("output.pdf.compression", "none");
				session.property("output.color", "cmyk");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
