package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests that <b>percentage specifications</b> ({@code width: 50%} and {@code padding-left: 10%})
 * take effect on flex items (added on 2026-08-03).
 *
 * <p>
 * A pure percentage ({@code LengthType.RELATIVE}) stores the fraction in the value field,
 * but {@code FlexBuilder} read it as "length + fraction × basis".
 * As a result, {@code width: 50%} was read as "0.5 pt", and the automatic minimum size
 * collapsed it to min-content width. <b>Bootstrap 5's grid uses
 * {@code .row > * { width: 100% }} and {@code .col-N { width: X% }},
 * so all Bootstrap documents wrapped after every word.</b>
 *
 * <p>
 * Found in the first case of wave 0, which imported full-scale documents (official Bootstrap examples).
 * The 20-million-document sweep never caught it because the generator did not give flex items
 * percentage widths. PLAN §3.
 */
public class FlexPercentageWidthTest extends AbstractTestCase {
	public FlexPercentageWidthTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/percentage-width.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** First item (width:50%). Used as the reference for subsequent checks. */
	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			return true;
		}
		return false;
	}

	/** 50% of the 400 pt page = 200 pt to the right. If collapsed, it would be min-content width (around 20 pt). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 200, x, 0.1);
			return true;
		}
		return false;
	}

	/** The same applies with only width specified and no flex declaration (75%). */
	public boolean check_s(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 300, x, 0.1);
			return true;
		}
		return false;
	}

	/**
	 * Percentage padding is <b>added to outer dimensions</b> (box-sizing defaults to content-box).
	 * width:50%=200 pt + padding-left:10%=40 pt puts the next item 240 pt to the right.
	 *
	 * <p>
	 * Until 2026-08-04, this was 200 pt: <b>flex item boxes never resolved actual padding/margin sizes,
	 * so both disappeared entirely for row-direction flex items</b>.
	 * Found when the first character of labels was clipped in checkout-form in real-corpus wave 6.
	 * The old expected value reflected that defect.
	 */
	public boolean check_u(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 240, x, 0.1);
			return true;
		}
		return false;
	}
}
