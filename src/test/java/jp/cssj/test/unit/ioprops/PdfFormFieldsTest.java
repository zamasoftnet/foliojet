package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The fields of interactive PDF forms ({@code output.pdf.forms=true}) as a form processor reads them.
 *
 * <ul>
 * <li>A {@code <select>} shows the text of its options and holds their {@code value}: the field listed the
 * values alone, so a viewer showed "gb" for {@code <option value="gb">United Kingdom</option>}.</li>
 * <li>The widgets lie where the controls are drawn when the page is scaled or moved on the paper
 * ({@code output.fit-to-paper}): they stayed at the place of the unscaled page.</li>
 * <li>Radio buttons of one name are one field, also when they are on different pages: each page made its own
 * field of that name, with its own selection.</li>
 * </ul>
 */
public class PdfFormFieldsTest extends TestCase {

	public void testSelectShowsLabelsAndHoldsValues() throws Exception {
		final byte[] pdf = convert("<!doctype html><select name=\"country\">"
				+ "<option value=\"gb\" selected>United Kingdom</option><option value=\"jp\">Japan</option></select>",
				props());
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDChoice country = (PDChoice) field(doc, "country");
			assertEquals(List.of("United Kingdom", "Japan"), country.getOptionsDisplayValues());
			assertEquals(List.of("gb", "jp"), country.getOptionsExportValues());
			assertEquals(List.of("gb"), country.getValue());
		}
	}

	/** Without a value attribute the text of an option is its value. */
	public void testSelectOptionWithoutValue() throws Exception {
		final byte[] pdf = convert("<!doctype html><select name=\"city\"><option selected>Tokyo</option>"
				+ "<option value=\"none\">None of these</option></select>", props());
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDChoice city = (PDChoice) field(doc, "city");
			assertEquals(List.of("Tokyo", "None of these"), city.getOptionsDisplayValues());
			assertEquals(List.of("Tokyo", "none"), city.getOptionsExportValues());
			assertEquals(List.of("Tokyo"), city.getValue());
		}
	}

	/** A 100pt page fitted to a 200pt paper: every kind of widget is twice as large and twice as far from the corner. */
	public void testWidgetsFollowThePageFittedToThePaper() throws Exception {
		final String html = "<!doctype html><style>@page{size:100pt 100pt;margin:0}body{margin:0;font-size:6pt}"
				+ "p{margin:2pt 0 0 10pt}</style>"
				+ "<p><input name=\"text\" value=\"ABC\" style=\"width:30pt\"></p>"
				+ "<p><input type=\"checkbox\" name=\"check\"> <input type=\"radio\" name=\"radio\" value=\"a\"></p>"
				+ "<p><select name=\"select\"><option>One</option></select></p>";
		final byte[] plain = convert(html, props());
		final byte[] fitted = convert(html, props("output.paper-width", "200pt", "output.paper-height", "200pt",
				"output.fit-to-paper", "true"));
		try (PDDocument plainDoc = Loader.loadPDF(plain); PDDocument fittedDoc = Loader.loadPDF(fitted)) {
			assertEquals(100f, plainDoc.getPage(0).getMediaBox().getWidth(), 0.01f);
			assertEquals(200f, fittedDoc.getPage(0).getMediaBox().getWidth(), 0.01f);
			for (final String name : new String[] { "text", "check", "radio", "select" }) {
				final PDRectangle a = widget(plainDoc, name);
				final PDRectangle b = widget(fittedDoc, name);
				final String message = name + ": " + a + " -> " + b;
				assertTrue(message, a.getWidth() > 1 && a.getHeight() > 1);
				assertEquals(message, a.getLowerLeftX() * 2, b.getLowerLeftX(), 0.05);
				assertEquals(message, a.getLowerLeftY() * 2, b.getLowerLeftY(), 0.05);
				assertEquals(message, a.getUpperRightX() * 2, b.getUpperRightX(), 0.05);
				assertEquals(message, a.getUpperRightY() * 2, b.getUpperRightY(), 0.05);
			}
		}
	}

	/** Two radio buttons of one name on two pages: one field with two widgets, one on each page. */
	public void testRadioGroupAcrossPagesIsOneField() throws Exception {
		final byte[] pdf = convert("<!doctype html><style>@page{size:200pt 200pt}section{break-before:page}</style>"
				+ "<label><input type=\"radio\" name=\"answer\" value=\"yes\" checked>Yes</label>"
				+ "<section><label><input type=\"radio\" name=\"answer\" value=\"no\">No</label></section>", props());
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertEquals(2, doc.getNumberOfPages());
			final PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
			assertNotNull("the PDF has no form", form);
			assertEquals("the buttons of one name are one field", 1, form.getFields().size());
			final PDRadioButton answer = (PDRadioButton) form.getField("answer");
			assertNotNull(answer);
			assertEquals(2, answer.getWidgets().size());
			assertEquals("yes", answer.getValue());
			assertEquals(java.util.Set.of("yes", "no"), answer.getOnValues());
			assertEquals(1, doc.getPage(0).getAnnotations().size());
			assertEquals(1, doc.getPage(1).getAnnotations().size());
		}
	}

	/** Buttons without a value get on-values that differ across the pages, so that each can be chosen by itself. */
	public void testRadioButtonsWithoutValuesAcrossPages() throws Exception {
		final byte[] pdf = convert("<!doctype html><style>@page{size:200pt 200pt}section{break-before:page}</style>"
				+ "<p><input type=\"radio\" name=\"pick\"> <input type=\"radio\" name=\"pick\"></p>"
				+ "<section><p><input type=\"radio\" name=\"pick\" checked></p></section>", props());
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
			assertNotNull("the PDF has no form", form);
			assertEquals(1, form.getFields().size());
			final PDRadioButton pick = (PDRadioButton) form.getField("pick");
			assertNotNull(pick);
			assertEquals(3, pick.getWidgets().size());
			assertEquals("each button needs its own on-value: " + pick.getOnValues(), 3, pick.getOnValues().size());
			// The checked button is the one on the second page
			final String value = pick.getValue();
			assertTrue(value + " is not one of " + pick.getOnValues(), pick.getOnValues().contains(value));
			assertEquals(1, doc.getPage(1).getAnnotations().size());
			assertEquals(value, doc.getPage(1).getAnnotations().get(0).getAppearanceState().getName());
			assertEquals(2, doc.getPage(0).getAnnotations().size());
			for (final PDAnnotation other : doc.getPage(0).getAnnotations()) {
				assertEquals("Off", other.getAppearanceState().getName());
			}
		}
	}

	private static PDField field(final PDDocument doc, final String name) {
		final PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
		assertNotNull("the PDF has no form", form);
		final PDField field = form.getField(name);
		assertNotNull("the form has no field " + name, field);
		return field;
	}

	/** The rectangle of a field's only widget. */
	private static PDRectangle widget(final PDDocument doc, final String name) {
		final PDTerminalField field = (PDTerminalField) field(doc, name);
		assertEquals(name, 1, field.getWidgets().size());
		return field.getWidgets().get(0).getRectangle();
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private static byte[] convert(final String html, final Map<String, String> properties) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("output.type", "application/pdf");
			session.property("output.pdf.forms", "true");
			for (final Map.Entry<String, String> e : properties.entrySet()) {
				session.property(e.getKey(), e.getValue());
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///form.html"), "text/html", "UTF-8");
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
