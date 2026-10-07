package jp.cssj.test.unit._0380_inline_block;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * The baseline of an inline-block is the baseline of its last line box (CSS 2.1 §10.8.1), wherever that line is
 * (2026-10-07, fit sweep seed 11942560). Lines are 7.2pt apart (6pt/1.2).
 *
 * <ul>
 * <li>A fixed block size larger than the content ({@code tb}, {@code lb}, {@code rb}) keeps the only line on the
 * baseline: the block-start edge matches the auto-sized box. It used to put the box's block-end edge there.</li>
 * <li>Three lines ({@code tc}, {@code lc}, {@code rc}) end where a one-line box ends.</li>
 * <li>Three lines in a box 8pt deep ({@code td}, {@code ld}, {@code rd}) overflow, and the last line still sits on
 * the baseline, two lines (14.4pt) past the one-line box's block start.</li>
 * <li>{@code vertical-lr}: the block end is the line-over side; it used to be taken as the line-under side, which
 * put the box on its first line.</li>
 * <li>{@code sideways-lr} ({@code sa}, {@code sb}, {@code sc}): the line-under side is the block end there, so the
 * value of {@code getLastDescent()} is the descent as it is.</li>
 * <li>{@code overflow: hidden} ({@code te}) puts the bottom margin edge on the baseline, above the bottom of a box
 * whose text sits on it.</li>
 * </ul>
 * Positions are collected by the hooks and compared after the conversion.
 */
public class InlineBlockBaselineTest extends AbstractTestCase {
	public InlineBlockBaselineTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0380-inline-block/baseline.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		// horizontal
		assertEquals(top("ta"), top("tb"), 0.1);
		assertEquals(bottom("ta"), bottom("tc"), 0.1);
		assertEquals(top("ta") - 14.4, top("td"), 0.1);
		assertTrue(bottom("te") + 0.5 < bottom("ta"));
		// vertical-lr: block start on the left
		assertEquals(left("la"), left("lb"), 0.1);
		assertEquals(right("la"), right("lc"), 0.1);
		assertEquals(left("la") - 14.4, left("ld"), 0.1);
		// vertical-rl: block start on the right
		assertEquals(right("ra"), right("rb"), 0.1);
		assertEquals(left("ra"), left("rc"), 0.1);
		assertEquals(right("ra") + 14.4, right("rd"), 0.1);
		// sideways-lr: block start on the left, but the line-under side is the right (the glyphs face left)
		assertEquals(left("sa"), left("sb"), 0.1);
		assertEquals(right("sa"), right("sc"), 0.1);
	}

	private double[] get(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id);
	}

	private double left(final String id) {
		return this.get(id)[0];
	}

	private double top(final String id) {
		return this.get(id)[1];
	}

	private double right(final String id) {
		return this.get(id)[0] + this.get(id)[2];
	}

	private double bottom(final String id) {
		return this.get(id)[1] + this.get(id)[3];
	}

	private boolean record(final String id, final IBox box, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.at.put(id, new double[] { x, y, box.getWidth(), box.getHeight() });
			return true;
		}
		return false;
	}

	public boolean check_ta(IBox box, int pageNumber, double x, double y) {
		return this.record("ta", box, x, y);
	}

	public boolean check_tb(IBox box, int pageNumber, double x, double y) {
		return this.record("tb", box, x, y);
	}

	public boolean check_tc(IBox box, int pageNumber, double x, double y) {
		return this.record("tc", box, x, y);
	}

	public boolean check_td(IBox box, int pageNumber, double x, double y) {
		return this.record("td", box, x, y);
	}

	public boolean check_te(IBox box, int pageNumber, double x, double y) {
		return this.record("te", box, x, y);
	}

	public boolean check_la(IBox box, int pageNumber, double x, double y) {
		return this.record("la", box, x, y);
	}

	public boolean check_lb(IBox box, int pageNumber, double x, double y) {
		return this.record("lb", box, x, y);
	}

	public boolean check_lc(IBox box, int pageNumber, double x, double y) {
		return this.record("lc", box, x, y);
	}

	public boolean check_ld(IBox box, int pageNumber, double x, double y) {
		return this.record("ld", box, x, y);
	}

	public boolean check_ra(IBox box, int pageNumber, double x, double y) {
		return this.record("ra", box, x, y);
	}

	public boolean check_rb(IBox box, int pageNumber, double x, double y) {
		return this.record("rb", box, x, y);
	}

	public boolean check_rc(IBox box, int pageNumber, double x, double y) {
		return this.record("rc", box, x, y);
	}

	public boolean check_rd(IBox box, int pageNumber, double x, double y) {
		return this.record("rd", box, x, y);
	}

	public boolean check_sa(IBox box, int pageNumber, double x, double y) {
		return this.record("sa", box, x, y);
	}

	public boolean check_sb(IBox box, int pageNumber, double x, double y) {
		return this.record("sb", box, x, y);
	}

	public boolean check_sc(IBox box, int pageNumber, double x, double y) {
		return this.record("sc", box, x, y);
	}
}
