package jp.cssj.test.unit._0520_block_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * CSS Box Alignment Level 3 §5.1.1: {@code align-content:center} centers all content of a normal
 * block on the block axis. Verifies the contracts for horizontal writing, vertical writing RL/LR,
 * and safe start fallback when content overflows.
 */
public class BlockAlignContentTest extends AbstractTestCase {
	public BlockAlignContentTest(String name) {
		super(name);
	}

	private double hX, hY, rlX, rlY, lrX, lrY, safeX, safeY;
	private double charStartX, glyphStartX, charCenterX, cellX, cellY;

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0520-block-align/align-content.html"), "text/html", null);
	}

	private static boolean block(IBox box) {
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_h(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		this.hX = x;
		this.hY = y;
		return true;
	}

	public boolean check_hc(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		assertEquals(this.hX, x, .1);
		assertEquals(this.hY + 40, y, .1);
		return true;
	}

	public boolean check_rl(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		this.rlX = x;
		this.rlY = y;
		return true;
	}

	public boolean check_rlc(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		assertEquals(this.rlX + 40, x, .1);
		assertEquals(this.rlY, y, .1);
		return true;
	}

	public boolean check_lr(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		this.lrX = x;
		this.lrY = y;
		return true;
	}

	public boolean check_lrc(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		assertEquals(this.lrX + 40, x, .1);
		assertEquals(this.lrY, y, .1);
		return true;
	}

	public boolean check_safe(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		this.safeX = x;
		this.safeY = y;
		return true;
	}

	public boolean check_safec(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		// vertical-rl block-start is right. The 120 pt content aligns to the right edge and overflows 20 pt to the left.
		assertEquals(this.safeX - 20, x, .1);
		assertEquals(this.safeY, y, .1);
		return true;
	}

	public boolean check_charStart(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		this.charStartX = x;
		return true;
	}

	public boolean check_glyphStart(IBox box, int page, double x, double y) {
		if (box.getType() != BoxType.INLINE) return false;
		this.glyphStartX = x;
		return true;
	}

	public boolean check_charCenter(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		this.charCenterX = x;
		return true;
	}

	public boolean check_glyphCenter(IBox box, int page, double x, double y) {
		if (box.getType() != BoxType.INLINE) return false;
		// Actual inline-block path for "enclosing a single character in a square". A 10 pt line
		// inside a 15 pt box moves 2.5 pt from block-start (right) toward the center.
		assertEquals((this.glyphStartX - this.charStartX) - 2.5, x - this.charCenterX, .1);
		return true;
	}

	public boolean check_cell(IBox box, int page, double x, double y) {
		if (box.getType() != BoxType.TABLE_CELL) return false;
		this.cellX = x;
		this.cellY = y;
		return true;
	}

	public boolean check_cellc(IBox box, int page, double x, double y) {
		if (!block(box)) return false;
		assertEquals(this.cellX, x, .1);
		assertEquals(this.cellY + 40, y, .1);
		return true;
	}
}
