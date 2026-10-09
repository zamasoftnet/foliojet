package jp.cssj.test.unit._1010_FORM;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
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
 * White space in form controls, as in the HTML Standard and Chrome (2026-10-09). Until then the UA style sheet gave
 * button, input and textarea {@code white-space: nowrap}: the label of a narrow button ran out of it (materialui's
 * cards), and a textarea put its text on one line, beyond its box.
 *
 * <p>
 * The fixture is drawn at 72 dpi (1 px = 1 pt); the boxes have no padding or border, 15pt lines (a line of the button
 * is 16pt here: its text and the strut have different fonts). Chrome 151 on the same
 * fixture (top + height, pt): button 10 + 30 (two lines), input button 70 + 30 (its value's line feed kept,
 * white-space: pre), text field 120 + 15 (the line feed taken out of the value), textarea 160 + 60 with "Ee" on its
 * first line (the line feed after the start tag dropped) and "Ff" on the next.
 * </p>
 */
public class FormWhiteSpaceTest extends TestCase {
	private static final File DOCUMENT = new File("files/unittest/1010-FORM/white-space.html");

	public void testWhiteSpace() throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, DOCUMENT, "text/html", "UTF-8");
		} finally {
			session.close();
		}
		try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
			final BufferedImage img = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
			final int[] button = rows(img, 255, 0, 0);
			assertEquals("the button's top", 10, button[0], 1);
			assertEquals("the button's label wraps onto two lines", 30, button[1] - button[0] + 1, 2.5);
			final int[] inputButton = rows(img, 0, 160, 0);
			assertEquals(70, inputButton[0], 1);
			assertEquals("the line feed of an input button's value makes two lines", 30,
					inputButton[1] - inputButton[0] + 1, 2.5);
			final int[] field = rows(img, 0, 0, 255);
			assertEquals(120, field[0], 1);
			assertEquals("a field's value stays on one line", 15, field[1] - field[0] + 1, 2.5);

			final List<TextPosition> positions = new ArrayList<>();
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void writeString(final String text, final List<TextPosition> textPositions)
						throws IOException {
					positions.addAll(textPositions);
				}
			};
			stripper.getText(pdf);
			final float ee = baseline(positions, 'E');
			final float ff = baseline(positions, 'F');
			assertTrue("the textarea's first line is Ee (the line feed after its start tag is dropped): " + ee,
					ee > 160 && ee <= 175);
			assertEquals("Ff on the next line", 15, ff - ee, 1);
			assertTrue("a field's value without its line feed: CcDd",
					new PDFTextStripper().getText(pdf).replaceAll("\\s+", "").contains("CcDd"));
		}
	}

	/** The first and last row (y) with pixels of the color. */
	private static int[] rows(final BufferedImage img, final int r, final int g, final int b) {
		int first = -1, last = -1;
		for (int y = 0; y < img.getHeight(); ++y) {
			for (int x = 10; x < 70; ++x) {
				final int rgb = img.getRGB(x, y);
				if (Math.abs(((rgb >> 16) & 0xFF) - r) < 30 && Math.abs(((rgb >> 8) & 0xFF) - g) < 30
						&& Math.abs((rgb & 0xFF) - b) < 30) {
					if (first < 0) {
						first = y;
					}
					last = y;
					break;
				}
			}
		}
		assertTrue("color " + r + "," + g + "," + b + " not found", first >= 0);
		return new int[] { first, last };
	}

	/** The baseline of the first glyph c. */
	private static float baseline(final List<TextPosition> positions, final char c) {
		for (final TextPosition p : positions) {
			if (p.getUnicode().equals(String.valueOf(c))) {
				return p.getYDirAdj();
			}
		}
		fail(c + " not found");
		return 0;
	}
}
