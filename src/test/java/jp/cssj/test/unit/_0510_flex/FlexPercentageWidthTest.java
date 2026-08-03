package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * flexアイテムの<b>割合指定</b>({@code width: 50%}・{@code padding-left: 10%})が
 * 効くことのテストです(2026-08-03新設)。
 *
 * <p>
 * 純粋な割合({@code LengthType.RELATIVE})は値の欄に割合が入るのに、
 * {@code FlexBuilder}が「長さ+割合×基準」で読んでいた。その結果
 * {@code width: 50%} が「0.5pt」と読まれ、自動最小サイズによってmin-content
 * 幅へ潰れていた。<b>Bootstrap 5のグリッドは
 * {@code .row > * { width: 100% }} と {@code .col-N { width: X% }} で
 * 組まれているため、Bootstrapで作られた文書は全部が1語ずつ改行される版面に
 * なっていた。</b>
 *
 * <p>
 * 実物大の文書(Bootstrapの公式サンプル)を取り込んだ第0波の1件目で発覚した
 * ——掃過2000万文書は一度も捕まえていない(生成器がflexアイテムに割合の幅を
 * 書かないため)。PLAN §3。
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

	/** 1つ目のitem(width:50%)。以降の基準にする。 */
	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			return true;
		}
		return false;
	}

	/** 紙面400ptの50%=200pt右。潰れていれば min-content 幅(20pt前後)になる。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 200, x, 0.1);
			return true;
		}
		return false;
	}

	/** flexの指定が無く width だけの場合も同じ(75%)。 */
	public boolean check_s(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 300, x, 0.1);
			return true;
		}
		return false;
	}

	/** 割合のpaddingを含めても外寸は50%(=200pt)のまま。 */
	public boolean check_u(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 200, x, 0.1);
			return true;
		}
		return false;
	}
}
