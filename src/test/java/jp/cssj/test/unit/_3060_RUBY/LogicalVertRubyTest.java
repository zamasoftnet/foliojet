package jp.cssj.test.unit._3060_RUBY;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Regression test for ruby geometry (vertical writing, logical properties).
 *
 * <p>
 * Under the annotated-text approach (specification decision on 2026-07-25), rb/rt do not become boxes;
 * one ruby unit (base text + reading) becomes one {@code RubyUnitBox}.
 * Only {@code ruby} itself remains as a DOM element, so put the id on {@code ruby} and measure its
 * inline box (the same position and line-axis dimension as the unit).
 * The reading extends into the inter-line space and is not counted in the dimensions.
 * The display-list golden (3060-RUBY/ruby-annotation.html) directly verifies the unit's content
 * and dimensions.
 * </p>
 */
public class LogicalVertRubyTest extends AbstractTestCase {
	public LogicalVertRubyTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3060-RUBY/logical-vert-ruby.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	/**
	 * Inspects a ruby element's inline box.
	 *
	 * <p>
	 * <b>Dimensions are values measured with pinned Noto.</b> On 2026-08-03, test fonts switched to
	 * automatic download of public Noto (previously, installed fonts made baselines machine-dependent).
	 * Where base text is kanji, it remains exactly 1em (24/36). <b>Only where the reading (kana)
	 * determines the width</b>, the size shrinks by the safe overhang allowed by {@code ruby-overhang:auto}.
	 * Update to 21.0 (2026-08-28): the old value 20.892 encoded spurious kerning in the embedded subset
	 * (subset GIDs incorrectly indexed a GPOS pair table keyed by font GIDs;
	 * no actual GPOS pair exists for this kana pair).
	 * </p>
	 *
	 * @param lineExtent line-axis dimension = max(base text width, reading width)
	 */
	private boolean check(IBox box, double x, double y, double expectedX, double expectedY, double lineExtent) {
		if (box.getType() != BoxType.INLINE) {
			return false;
		}
		assertEquals(expectedX, x, 1);
		assertEquals(expectedY, y, 1);
		// Allow only rounding error. Values are measured with the pinned font.
		assertEquals(lineExtent, box.getHeight(), 0.001);
		return true;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 178.29, 44.89, 24);
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 178.29, 93.78, 36);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 120.04, 115.604, 21.0);
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 139.46, 104.827, 24);
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 100.63, 44.89, 24);
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		return this.check(box, x, y, 42.38, 115.604, 21.0);
	}
}
