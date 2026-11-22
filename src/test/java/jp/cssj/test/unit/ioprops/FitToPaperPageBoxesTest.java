package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * A page fitted to a paper of another size ({@code output.fit-to-paper}) is drawn scaled, and its TrimBox and
 * BleedBox are the scaled page.
 *
 * <p>
 * The boxes used the page's own size: a 200pt page on a 100pt paper got a 200pt TrimBox, outside the paper, and
 * PDF/X output stopped with "TrimBox must lie within the BleedBox" when the page was closed.
 * </p>
 */
public class FitToPaperPageBoxesTest extends TestCase {
	private static final String HTML = "<!doctype html><title>Fit test</title>"
			+ "<style>@page{size:200pt 200pt;margin:0}body{margin:0}div{width:180pt;height:180pt;background:black}</style>"
			+ "<div></div>";

	/** Shrunk to half: the finished page is the whole 100pt paper. */
	public void testTrimBoxFollowsThePageShrunkToThePaper() throws Exception {
		final byte[] pdf = convert(props("output.paper-width", "100pt", "output.paper-height", "100pt",
				"output.fit-to-paper", "true"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals(1, doc.getNumberOfPages());
			assertBox("MediaBox", doc, COSName.MEDIA_BOX, 0, 0, 100, 100);
			assertBox("TrimBox", doc, COSName.TRIM_BOX, 0, 0, 100, 100);
			assertBox("BleedBox", doc, COSName.BLEED_BOX, 0, 0, 100, 100);
		}
	}

	/** Enlarged to double: the finished page is the whole 400pt paper, not its 200pt corner. */
	public void testTrimBoxFollowsThePageEnlargedToThePaper() throws Exception {
		final byte[] pdf = convert(props("output.paper-width", "400pt", "output.paper-height", "400pt",
				"output.fit-to-paper", "true"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertBox("MediaBox", doc, COSName.MEDIA_BOX, 0, 0, 400, 400);
			assertBox("TrimBox", doc, COSName.TRIM_BOX, 0, 0, 400, 400);
		}
	}

	/** Without fitting, the page keeps its size and its place in the middle of the paper. */
	public void testTrimBoxOfAnUnscaledPageIsUnchanged() throws Exception {
		final byte[] pdf = convert(props("output.paper-width", "400pt", "output.paper-height", "400pt"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertBox("MediaBox", doc, COSName.MEDIA_BOX, 0, 0, 400, 400);
			assertBox("TrimBox", doc, COSName.TRIM_BOX, 100, 100, 300, 300);
		}
	}

	/** PDF/X checks the boxes when the page is closed: the conversion finishes and the boxes are valid. */
	public void testPdfXWithAPageShrunkToThePaperFinishes() throws Exception {
		final byte[] pdf = convert(props("output.paper-width", "100pt", "output.paper-height", "100pt",
				"output.fit-to-paper", "true", "output.pdf.version", "1.6X-4"));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals(1, doc.getNumberOfPages());
			assertBox("MediaBox", doc, COSName.MEDIA_BOX, 0, 0, 100, 100);
			assertBox("TrimBox", doc, COSName.TRIM_BOX, 0, 0, 100, 100);
		}
	}

	/** Compares the box as it is written in the first page's dictionary (PDFBox's getters clip it to the MediaBox). */
	private static void assertBox(final String name, final PDDocument doc, final COSName key, final double llx,
			final double lly, final double urx, final double ury) {
		final COSArray array = doc.getPage(0).getCOSObject().getCOSArray(key);
		assertNotNull(name + " is missing", array);
		final float[] box = array.toFloatArray();
		final String message = name + " " + java.util.Arrays.toString(box);
		assertEquals(message, 4, box.length);
		assertEquals(message, llx, box[0], 0.01);
		assertEquals(message, lly, box[1], 0.01);
		assertEquals(message, urx, box[2], 0.01);
		assertEquals(message, ury, box[3], 0.01);
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static byte[] convert(final Map<String, String> properties) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			for (final Map.Entry<String, String> e : properties.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///fit-to-paper.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
