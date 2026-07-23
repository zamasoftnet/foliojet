package jp.cssj.test.unit.builder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.builder.impl.TotalFitProjection;
import net.zamasoft.foliojet.layout.builder.impl.TotalFitProjection.Piece;
import net.zamasoft.foliojet.layout.builder.impl.TotalFitProjection.Plan;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit;

/**
 * Knuth-Plass投影({@code TotalFitProjection}、M3c増分3)の単体テスト
 * です。TextBuilderを起動せず、合成した計測済みイベント列(Piece列)
 * だけでbreakpoint選択とフォールバック判定を検証します。
 */
public class TotalFitProjectionTest extends TestCase {

	private static TotalFit.Parameters params() {
		return new TotalFit.Parameters(200, 10, 10000, 10000, 5000, TotalFit.LastLinePolicy.RAGGED);
	}

	/**
	 * 幅{@code word}の語を空白区切りで{@code count}個並べた欧文風の列
	 * ({@code em}はflushに与えるフォントサイズ相当。伸長の基準)。
	 */
	private static List<Piece> latinWords(final int count, final double word, final double space, final double em) {
		final List<Piece> pieces = new ArrayList<>();
		int ordinal = 0;
		for (int i = 0; i < count; ++i) {
			pieces.add(new Piece.Box(word));
			if (i < count - 1) {
				pieces.add(new Piece.Space(space));
				pieces.add(new Piece.Flush(ordinal++, em * 0.5));
			}
		}
		return pieces;
	}

	/** 幅{@code w}の不可分単位のあとに毎回flushが来る和文風の列。 */
	private static List<Piece> cjkUnits(final int count, final double w, final double stretch) {
		final List<Piece> pieces = new ArrayList<>();
		int ordinal = 0;
		for (int i = 0; i < count; ++i) {
			pieces.add(new Piece.Box(w));
			pieces.add(new Piece.Flush(ordinal++, stretch));
		}
		return pieces;
	}

	/**
	 * 選択されたbreakpointで区切ったときの各行幅を求めます(行末で破った
	 * 空白は幅に入れない=末尾つぶしのモデル)。
	 */
	private static List<Double> lineWidths(final List<Piece> pieces, final BitSet chosen) {
		final List<Double> widths = new ArrayList<>();
		double current = 0;
		boolean lineHead = true;
		for (int i = 0; i < pieces.size(); ++i) {
			switch (pieces.get(i)) {
			case Piece.Box box -> {
				current += box.width();
				lineHead = false;
			}
			case Piece.Space space -> {
				if (i + 1 < pieces.size() && pieces.get(i + 1) instanceof Piece.Flush f
						&& chosen.get(f.ordinal())) {
					widths.add(current);
					current = 0;
					lineHead = true;
					++i;
				} else if (!lineHead) {
					current += space.width();
				}
			}
			case Piece.Hyphen hyphen -> {
				if (i + 1 < pieces.size() && pieces.get(i + 1) instanceof Piece.Flush f
						&& chosen.get(f.ordinal())) {
					widths.add(current + hyphen.width());
					current = 0;
					lineHead = true;
					++i;
				}
			}
			case Piece.LineFeed lf -> {
				// 次のflushで強制改行(このテストでは直後にflushを置く)
			}
			case Piece.Flush flush -> {
				if (chosen.get(flush.ordinal())) {
					widths.add(current);
					current = 0;
					lineHead = true;
				}
			}
			}
		}
		widths.add(current);
		return widths;
	}

	public void testLatinParagraphBreaksWithinMeasure() {
		// 語30+空白5×10語、行幅100 → 3語ちょうど(100)の行が実行可能解
		final List<Piece> pieces = latinWords(10, 30, 5, 10);
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		final BitSet chosen = plan.chosenOrdinals();
		assertTrue(chosen.cardinality() >= 2);
		for (final double w : lineWidths(pieces, chosen)) {
			assertTrue("line overflow: " + w, w <= 100 + 0.5);
		}
	}

	public void testFirstLineIndentNarrowsFirstLineOnly() {
		// 先頭行だけ幅50(text-indent 50相当)。語30+空白5では先頭行に
		// 1語しか入らない(尾部Glueの伸長で1語の行も実行可能)
		final List<Piece> pieces = latinWords(6, 30, 5, 10);
		final Plan plan = TotalFitProjection.plan(pieces, 50, 100, params());
		assertNotNull(plan);
		final List<Double> widths = lineWidths(pieces, plan.chosenOrdinals());
		assertTrue("first line must fit 50: " + widths.get(0), widths.get(0) <= 50 + 0.5);
	}

	public void testForcedBreakIsAlwaysChosen() {
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(20));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(0, 0));
		pieces.add(new Piece.Box(20));
		pieces.add(new Piece.LineFeed());
		pieces.add(new Piece.Flush(1, 0));
		pieces.add(new Piece.Box(20));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(2, 0));
		pieces.add(new Piece.Box(20));
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		// 明示改行(ordinal 1)は必ず選ばれ、収まる行の途中(0, 2)は
		// 選ばれない
		final BitSet chosen = plan.chosenOrdinals();
		assertTrue(chosen.get(1));
		assertFalse(chosen.get(0));
		assertFalse(chosen.get(2));
	}

	public void testHyphenBreakAccountsHyphenWidth() {
		// [90][SHY(5)][flush][30] 行幅100: ハイフン分割なら90+5=95で収まる
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(90));
		pieces.add(new Piece.Hyphen(5));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Box(30));
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		assertTrue(plan.chosenOrdinals().get(0));
		for (final double w : lineWidths(pieces, plan.chosenOrdinals())) {
			assertTrue("line overflow: " + w, w <= 100 + 0.5);
		}
	}

	public void testCjkParagraphBreaksWithinMeasure() {
		// 10pt字×30字、行幅95 → 9字程度で折る(尾部Glueで実行可能に)
		final List<Piece> pieces = cjkUnits(30, 10, 5);
		final Plan plan = TotalFitProjection.plan(pieces, 95, 95, params());
		assertNotNull(plan);
		final BitSet chosen = plan.chosenOrdinals();
		assertTrue(chosen.cardinality() >= 2);
		for (final double w : lineWidths(pieces, chosen)) {
			assertTrue("line overflow: " + w, w <= 95 + 0.5);
		}
	}

	public void testSingleWordLinesFeasibleWithTailStretch() {
		// 1行に1語しか入らない(80/100)——尾部Glue(2em=20)の伸長の
		// 範囲内なので5行に組める
		final List<Piece> pieces = latinWords(5, 80, 5, 10);
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		final List<Double> widths = lineWidths(pieces, plan.chosenOrdinals());
		assertEquals(5, widths.size());
		for (final double w : widths) {
			assertTrue("line overflow: " + w, w <= 100 + 0.5);
		}
	}

	public void testInfeasibleLayoutFallsBack() {
		// 伸長ゼロ(em=0)では1語の行が実行可能解に入らず、全体が
		// 溢れ行に退化する——事後の幅検証で検出してlegacyへ
		final List<Piece> pieces = latinWords(5, 80, 5, 0);
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testOversizedUnbreakableRunFallsBack() {
		// breakpoint候補間の不可分連続(200)が行幅100を超える → legacyへ
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(200));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Box(30));
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testNowrapSpaceIsNotBreakable() {
		// flushを伴わない空白(nowrap)はbreakpoint候補にならず、不可分
		// 連続が行幅を超えるためフォールバックする
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(60));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Box(60));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Box(10));
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testNoBreakCandidatesFallsBack() {
		// breakpoint候補ゼロ(flushなし)は最適化しない
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(30));
		pieces.add(new Piece.Space(5));
		pieces.add(new Piece.Box(30));
		assertNull(TotalFitProjection.plan(pieces, 100, 100, params()));
	}

	public void testEmptyPiecesFallsBack() {
		assertNull(TotalFitProjection.plan(new ArrayList<>(), 100, 100, params()));
	}

	public void testMateriallessFlushIsNotACandidate() {
		// 材料のないflush(連続flush)にはpenaltyを置かない=空行の候補を
		// 作らない
		final List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece.Box(80));
		pieces.add(new Piece.Flush(0, 5));
		pieces.add(new Piece.Flush(1, 5));
		pieces.add(new Piece.Box(80));
		final Plan plan = TotalFitProjection.plan(pieces, 100, 100, params());
		assertNotNull(plan);
		assertTrue(plan.chosenOrdinals().get(0));
		assertFalse(plan.chosenOrdinals().get(1));
	}

	public void testPlanConsumeOnce() {
		final List<Piece> pieces = cjkUnits(30, 10, 5);
		final Plan plan = TotalFitProjection.plan(pieces, 95, 95, params());
		assertNotNull(plan);
		final int ordinal = plan.chosenOrdinals().nextSetBit(0);
		assertTrue(ordinal >= 0);
		plan.arriveFlush(ordinal);
		// 選ばれたflushで一度だけtrue(while(flush())ループの再入では
		// 二重改行しない)
		assertTrue(plan.takeBreakAtCursor());
		assertFalse(plan.takeBreakAtCursor());
		// 選ばれていないflushでは常にfalse
		int notChosen = 0;
		while (plan.chosenOrdinals().get(notChosen)) {
			++notChosen;
		}
		plan.arriveFlush(notChosen);
		assertFalse(plan.takeBreakAtCursor());
	}
}
