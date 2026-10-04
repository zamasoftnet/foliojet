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
 * grid・flex の箱の margin-bottom が効くことを固定します(2026-10-04、TECH-20261003-004 の⑰)。
 * 箱を開くとき上の余白が「子と相殺する待ち」のまま残り、閉じるときにそれと下の余白を
 * 相殺していたので、下の余白が「下−上」に減っていた(上下同じなら 0)。grid・flex は独立した
 * 整形文脈で、余白は中身と相殺しない(css-flexbox-1 §3、css-grid-1 §3)。
 */
public class FlexGridMarginTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 行の高さ 18pt。上下の余白 10pt の箱の後ろの段落は、箱の行から 28pt 下にあるはず。 */
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
		// item の余白(3pt)は箱の中に残り、箱の余白とは相殺しない
		assertEquals("item の余白は箱の中", 31f, y.get("Fc") - y.get("P3"), 0.05f);
		assertEquals("item の余白+箱の下の余白", 31f, y.get("P4") - y.get("Fc"), 0.05f);
		assertEquals("block は今までどおり", 28f, y.get("P5") - y.get("Bd"), 0.05f);
	}

	/**
	 * overflow:hidden・display:flow-root の箱も独立した整形文脈で、上の余白は最初の子の余白と
	 * 相殺しない(CSS 2.1 §8.3.1。2026-10-04、ユーザー決定)。普通の block は今までどおり相殺する。
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
					// 同じ行の語は同じ基準線(grid の 2 列は 1 行に出る)
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
