package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that margin-bottom works on grid/flex boxes (2026-10-04, TECH-20261003-004 item ⑰).
 * On opening the box, its top margin remained pending collapse with a child; on closing,
 * it collapsed with the bottom margin, reducing the latter to "bottom−top" (0 if equal).
 * Grid/flex establish independent formatting contexts, so their margins do not collapse
 * with their contents (css-flexbox-1 §3, css-grid-1 §3).
 */
public class FlexGridMarginTest extends TestCase {
	/** Line height is 18 pt. A paragraph after a box with 10 pt top/bottom margins should be 28 pt below its line. */
	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><style>
			@page { size: 100mm 200mm; margin: 5mm }
			body { font-size: 10pt; line-height: 18pt }
			p { margin: 0 }
			.m { margin: 10pt 0 }
			.m2 { margin: 10pt 0 20pt 0 }
			</style></head><body>
			<p>P0</p>
			<div class="m" style="display:grid; grid-template-columns: 10mm 1fr"><div>Ga</div><div>Gb</div></div>
			<p>P1</p>
			<div class="m" style="display:flex"><div>Fa</div></div>
			<p>P2</p>
			<div class="m2" style="display:flex"><div>Fb</div></div>
			<p>P3</p>
			<div class="m" style="display:flex"><div style="margin: 3pt 0">Fc</div></div>
			<p>P4</p>
			<div class="m">Bd</div>
			<p>P5</p>
			</body></html>
			""";

	public void testBottomMarginOfGridAndFlex() throws Exception {
		final Map<String, Float> y = baselines();
		assertEquals("上の余白(基準)", 28f, y.get("Ga") - y.get("P0"), 0.05f);
		assertEquals("grid の下の余白", 28f, y.get("P1") - y.get("Ga"), 0.05f);
		assertEquals("flex の下の余白", 28f, y.get("P2") - y.get("Fa"), 0.05f);
		assertEquals("上下で違う余白(下 20pt)", 38f, y.get("P3") - y.get("Fb"), 0.05f);
		// The item's margin (3 pt) stays inside the box and does not collapse with the box's margin.
		assertEquals("item の余白は箱の中", 31f, y.get("Fc") - y.get("P3"), 0.05f);
		assertEquals("item の余白+箱の下の余白", 31f, y.get("P4") - y.get("Fc"), 0.05f);
		assertEquals("block は今までどおり", 28f, y.get("P5") - y.get("Bd"), 0.05f);
	}

	/**
	 * overflow:hidden/display:flow-root boxes also establish independent formatting contexts;
	 * their top margins do not collapse with the first child's margin (CSS 2.1 §8.3.1;
	 * user decision on 2026-10-04). Ordinary blocks continue to collapse margins as before.
	 */
	private static final String BFC_HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/><style>
			@page { size: 100mm 200mm; margin: 5mm }
			body { font-size: 10pt; line-height: 18pt }
			p { margin: 0 }
			.c { margin-top: 10pt }
			</style></head><body>
			<p>Q0</p>
			<div style="overflow:hidden; margin-top:10pt"><p class="c">Oa</p></div>
			<p>Q1</p>
			<div style="display:flow-root; margin-top:10pt"><p class="c">Ra</p></div>
			<p>Q2</p>
			<div style="margin-top:10pt"><p class="c">Ba</p></div>
			<p>Q3</p>
			<div style="overflow:hidden; margin-bottom:10pt"><p style="margin-bottom:10pt">Ob</p></div>
			<p>Q4</p>
			</body></html>
			""";

	public void testBlockFormattingContextRootsDoNotCollapseWithChildren() throws Exception {
		final Map<String, Float> y = baselines(BFC_HTML);
		assertEquals("overflow:hidden の上の余白+子の余白", 38f, y.get("Oa") - y.get("Q0"), 0.05f);
		assertEquals("flow-root の上の余白+子の余白", 38f, y.get("Ra") - y.get("Q1"), 0.05f);
		assertEquals("普通の block は相殺する", 28f, y.get("Ba") - y.get("Q2"), 0.05f);
		assertEquals("overflow:hidden の下の余白+子の余白", 38f, y.get("Q4") - y.get("Ob"), 0.05f);
	}

	private static Map<String, Float> baselines() throws Exception {
		return baselines(HTML);
	}

	private static Map<String, Float> baselines(final String html) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///flex-grid-margin.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		final Map<String, Float> y = new HashMap<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void writeString(final String text, final java.util.List<TextPosition> positions) {
					// Words on the same line share a baseline (the grid's two columns appear on one line).
					for (final String token : text.trim().split("\\s+")) {
						y.put(token, positions.get(0).getYDirAdj());
					}
				}
			};
			stripper.getText(doc);
		}
		return y;
	}
}
