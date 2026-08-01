package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * 単一行row・definite basisの配置テストです(Flex F1d——
 * consult-codex-2026-08-02-flexbox.txt F1d)。3 item(basis 60/80/100pt)が
 * 同一行に主軸順で並び、record数=bind数、fallback非発火を固定する。
 */
public class FlexRowFixedBasisTest extends AbstractTestCase {
	public FlexRowFixedBasisTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		final long anonBefore = FlexBuilder.fallbacksByReason(FlexBuilder.FallbackReason.ANONYMOUS_ITEM);
		final long basisBefore = FlexBuilder.fallbacksByReason(FlexBuilder.FallbackReason.INDEFINITE_BASIS);
		File file = new File("files/unittest/0510-flex/row-fixed-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("record数=3", recordsBefore + 3, FlexBuilder.FLEX_ITEM_RECORDS.get());
		assertEquals("bind数=3", bindsBefore + 3, FlexBuilder.FLEX_ITEM_BINDS.get());
		assertEquals("匿名fallback非発火", anonBefore,
				FlexBuilder.fallbacksByReason(FlexBuilder.FallbackReason.ANONYMOUS_ITEM));
		assertEquals("basis fallback非発火", basisBefore,
				FlexBuilder.fallbacksByReason(FlexBuilder.FallbackReason.INDEFINITE_BASIS));
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** 2番目のitemは主軸+60pt(1番目のbasis幅)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 3番目のitemは主軸+140pt(60+80)。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 140, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 後続ブロックはコンテナ高(=行のcross size最大30pt)の直後。 */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 30, y, 0.1);
			return true;
		}
		return false;
	}
}
