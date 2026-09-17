package jp.cssj.test.unit._0280_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * <b>直交フローの表の百分率の基準をフラグメンテナへ落とす</b>ことの回帰です(2026-09-16)。
 *
 * <p>
 * 縦組みの本文に横組みの表を置くと、幅の百分率の基準を探す
 * {@code getFixedWidth()}(明示寸法の祖先を遡る仕組み)に該当が無く 0 が返っていた。
 * その結果 {@code max-width: 50%} が 0 になり、<b>幅 0 の表から内容が紙面外へあふれていた</b>
 * (掃過の「全描画が紙面外」。用紙 200pt の文書で x=200.5 から描かれた)。
 * 用紙寸法は確定値なので、css-writing-modes-4 §7.3 のとおり最後の基準として使う。
 * </p>
 */
public class OrthogonalTablePercentTest extends AbstractTestCase {
	public OrthogonalTablePercentTest(final String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		final File file = new File("files/unittest/0280-height/orthogonal-table-percent.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 表は用紙幅 200pt の 50% = 100pt に収まり、縦組みなので右端から置かれる。 */
	public boolean check_t(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.TABLE) {
			// 同じ id で匿名ブロックの箱も回ってくる(寸法を持つのは表の箱)
			return false;
		}
		assertEquals("ページ", 1, pageNumber);
		assertEquals("表の幅は用紙幅の 50%", 100.0, box.getWidth(), 0.5);
		// 縦組み(vertical-rl)なので block-start は右端。100pt の表は x=100 から始まる
		assertEquals("表の左端", 100.0, x, 0.5);
		assertTrue("表の右端が紙面内", x + box.getWidth() <= 200.5);
		return true;
	}

	/** 幅 auto+`max-width: 50%` の表も、基準が用紙幅なので 100pt までに収まる。 */
	public boolean check_m(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.TABLE) {
			return false;
		}
		assertTrue("max-width が効いて 100pt 以内(実測 " + box.getWidth() + ")", box.getWidth() <= 100.5);
		assertTrue("表が紙面内(x=" + x + " w=" + box.getWidth() + ")", x >= -0.5 && x + box.getWidth() <= 200.5);
		return true;
	}
}
