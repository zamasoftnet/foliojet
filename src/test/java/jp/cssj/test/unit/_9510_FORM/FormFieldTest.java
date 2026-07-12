package jp.cssj.test.unit._9510_FORM;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies that with {@code output.pdf.forms=true}, HTML form controls
 * (input/textarea) become interactive PDF AcroForm fields. Only form parts
 * change; documents without form controls are unaffected (the property
 * defaults to off). Assertions read the PDF bytes so they are independent of
 * any rasterizer.
 */
public class FormFieldTest extends AbstractTestCase {

	public FormFieldTest(String name) {
		super(name);
	}

	private static final File FORM = new File("files/unittest/9510-FORM/form.html");

	private boolean closed = false;

	private String transcodeAndRead() throws Exception {
		CTISessionHelper.transcodeFile(this.session, FORM, "text/html", null);
		this.session.close();
		this.closed = true;
		return new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);
	}

	@Override
	protected void tearDown() throws Exception {
		if (!this.closed) {
			super.tearDown();
		}
	}

	protected void transcode() throws Exception {
		// Not used; the test drives its own transcode.
	}

	public void testFormsDisabledByDefault() throws Exception {
		// Without the property, the output stays free of interactive forms.
		final String pdf = this.transcodeAndRead();
		assertFalse("forms must be opt-in: no AcroForm without output.pdf.forms", pdf.contains("/AcroForm"));
	}

	public void testFormControlsBecomeFields() throws Exception {
		this.session.property("output.pdf.forms", "true");
		final String pdf = this.transcodeAndRead();
		assertTrue("the catalog must carry an AcroForm dictionary", pdf.contains("/AcroForm"));
		assertTrue("controls must produce widget annotations", pdf.contains("/Subtype /Widget"));
		assertTrue("text input and textarea must be text fields", pdf.contains("/FT /Tx"));
		assertTrue("checkbox/radio/submit must be button fields", pdf.contains("/FT /Btn"));
		assertTrue("the text input's maxlength must reach the field", pdf.contains("/MaxLen 40"));
		assertTrue("select must become a choice field", pdf.contains("/FT /Ch"));
		assertTrue("the select must carry its options", pdf.contains("/Opt"));
		assertTrue("radios must be grouped into one field with kids", pdf.contains("/Kids"));
		assertTrue("radio widgets must reference their parent field", pdf.contains("/Parent"));
	}

	// Override the geometry driver: this suite checks PDF bytes instead.
	public void testDocument() throws Exception {
		// no-op
	}
}
