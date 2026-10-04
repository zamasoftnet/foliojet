package jp.cssj.test.unit.ioprops;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import jp.cssj.cti2.helpers.CTISessionHelper;
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
 * PNG/JPEG でも1パスで{@code target-counter()}の番号が出ることを固定します(2026-10-04、
 * docs/design/one-pass-target-counter-design.md §8)。欄のある頁は描画を記録して取っておき、
 * 参照先が揃ってから画像に描く。結果の番号は出した順なので、頁の順に出す。
 */
public class ImageOnePassTargetCounterTest extends TestCase {
	private static final String STYLE = """
			<style>
			@page { size: 200pt 200pt; margin: 20pt }
			body { margin: 0; font-size: 12pt }
			nav a::after { content: leader(".") target-counter(attr(href), page) }
			.back::after { content: " (p. " target-counter(attr(href), page) ")" }
			h1 { break-before: page; font-size: 14pt; margin: 0 }
			</style>""";

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

		BufferedImage image(final String uri) throws Exception {
			final ByteArrayOutputStream out = this.data.get(uri);
			assertNotNull(uri + " must be emitted: " + this.order, out);
			return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
		}
	}

	private static CapturingResults convert(final String body, final List<String> warnings, final String... props)
			throws Exception {
		final String html = "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ STYLE + "</head><body>" + body + "</body></html>";
		final CapturingResults results = new CapturingResults();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(results);
			session.setMessageHandler(new MessageHandler() {
				@Override
				public void message(final short code, final String[] args, final String message) {
					if (code == 0x2822 || code == 0x2823) {
						warnings.add(Integer.toHexString(code) + " " + message);
					}
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("output.type", "image/png");
			for (int i = 0; i < props.length; i += 2) {
				session.property(props[i], props[i + 1]);
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///image-one-pass.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		return results;
	}

	/** 濃い画素の数(x0〜x1、y0〜y1)。 */
	private static int ink(final BufferedImage image, final int x0, final int y0, final int x1, final int y1) {
		int n = 0;
		for (int y = y0; y < y1; ++y) {
			for (int x = x0; x < x1; ++x) {
				final int rgb = image.getRGB(x, y);
				if (((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + (rgb & 0xff) < 384) {
					++n;
				}
			}
		}
		return n;
	}

	/** 目次の頁は参照先が組まれるまで待ち、番号を入れて頁の順に出る。 */
	public void testTableOfContentsHasNumbersAndPagesStayInOrder() throws Exception {
		final String chapters = """
				<h1 id="one">Chapter One</h1><p>first</p>
				<h1 id="two">Chapter Two</h1><p>second</p>""";
		final List<String> warnings = new ArrayList<>();
		final CapturingResults found = convert(
				"<nav><p><a href=\"#one\">One</a></p><p><a href=\"#two\">Two</a></p></nav>" + chapters, warnings);
		assertEquals(List.of("#1", "#2", "#3"), found.order);
		assertEquals(warnings.toString(), List.of(), warnings);

		final CapturingResults missing = convert(
				"<nav><p><a href=\"#none1\">One</a></p><p><a href=\"#none2\">Two</a></p></nav>" + chapters,
				new ArrayList<>());
		assertEquals(List.of("#1", "#2", "#3"), missing.order);

		// 行末の番号の欄。参照先があれば字が描かれ、無ければ空
		final BufferedImage withNumbers = found.image("#1");
		final BufferedImage blank = missing.image("#1");
		final int w = withNumbers.getWidth();
		final int h = withNumbers.getHeight();
		final int right = w * 180 / 200;
		final int numbers = ink(withNumbers, right - w * 10 / 200, 0, right, h / 2);
		final int none = ink(blank, right - w * 10 / 200, 0, right, h / 2);
		assertTrue(numbers + " > " + none, numbers > none);
	}

	/**
	 * 記録して描き直した頁は、直接描いた頁と同じ画素になる(欄の行を除く)。記録器が Java2D と
	 * 違う能力を答えると近似の描き方へ入り、描き直しても戻らない。
	 */
	public void testRecordedPageMatchesDirectDrawing() throws Exception {
		final String body = """
				<div id="top" style="height: 70pt; background: linear-gradient(45deg, #c00, #00c);
				  border-radius: 12pt; box-shadow: 4pt 4pt 6pt rgba(0,0,0,.5); opacity: .8">
				  <p style="transform: rotate(-8deg); color: white; filter: blur(0.5pt)">Rotated text</p>
				</div>
				<p style="mix-blend-mode: multiply; background: #ff0">blend</p>
				<p style="position: absolute; top: 140pt"><a class="back" href="#top">back</a></p>""";
		final BufferedImage recorded = convert(body, new ArrayList<>(), "processing.pass-count", "1").image("#1");
		final BufferedImage direct = convert(body, new ArrayList<>(), "processing.pass-count", "2").image("#1");
		assertEquals(direct.getWidth(), recorded.getWidth());
		assertEquals(direct.getHeight(), recorded.getHeight());
		final int limit = direct.getHeight() * 130 / 200;
		int differ = 0;
		for (int y = 0; y < limit; ++y) {
			for (int x = 0; x < direct.getWidth(); ++x) {
				if (direct.getRGB(x, y) != recorded.getRGB(x, y)) {
					++differ;
				}
			}
		}
		assertEquals("pixels that differ above the reference line", 0, differ);
	}
}
