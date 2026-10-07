package jp.cssj.test.unit._0150_text_shadow;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies that <b>the x/y offsets of text-shadow work independently</b>
 * (added on 2026-08-18).
 *
 * <p>
 * A copy error made the y calculation in {@code css.impl.property.text.TextShadow.get()} read
 * {@code src[i].x}, so y always equaled x. The shadow for {@code text-shadow: 0 1px}
 * (a common Prism color scheme) fell at <b>exactly the same coordinates</b> as the text itself,
 * drawing all text twice. This was a real defect: the type-area audit reported 319 overlapping pairs
 * in code blocks on the reveal.js documentation site.
 * </p>
 */
public class OffsetTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0150-text-shadow/offsets.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public OffsetTest(String name) {
		super(name);
	}

	private static TextShadow shadowOf(IBox box) {
		final TextShadow[] shadows = ((AbstractTextParams) box.getParams()).textShadows;
		assertNotNull("影が無い", shadows);
		assertEquals(1, shadows.length);
		return shadows[0];
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		final TextShadow shadow = shadowOf(box);
		assertEquals(2.0, shadow.x, 0.01);
		assertEquals(3.0, shadow.y, 0.01);
		return true;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		final TextShadow shadow = shadowOf(box);
		assertEquals(0.0, shadow.x, 0.01);
		assertEquals(1.0, shadow.y, 0.01);
		return true;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		final TextShadow shadow = shadowOf(box);
		assertEquals(4.0, shadow.x, 0.01);
		assertEquals(0.0, shadow.y, 0.01);
		return true;
	}
}
