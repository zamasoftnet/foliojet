package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.text.pipeline.BreakNode;
import net.zamasoft.pdfg2d.gc.text.pipeline.LineMeasure;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit;

/**
 * TextBuilder構築セッションの計測済みイベント列({@link Piece}列)を
 * Knuth-Plassの{@link BreakNode}列へ投影し、{@link TotalFit}が選択した
 * breakpoint列を{@link Plan}(flush序数の集合)として返します
 * (2026-07-23新設、M3c増分3)。
 *
 * <p>
 * BreakNodeは<b>選択用の投影のみ</b>で、唯一の中間表現にはしない
 * (設計doc {@code docs/history/2026-07-23-m3c-design.md})。物理的な
 * 行生成(禁則・ハイフン実体化・インライン再生成・justification)は
 * すべて既存の{@link TextBuilder}が担当し、本クラスは「どのflushで
 * 改行するか」だけを供給する。禁則の写像は「下流へ実際に配達された
 * flush→通常penalty、SoftHyphen直後のflush→hyphen penalty、明示改行
 * (toLineFeed)のflush→forced penalty」。空白は仮想的なGlueとし、
 * 直後にflushがある場合のみ手前にコスト0のpenaltyを置いて候補にする
 * (TeXの慣用形。そこで破ると空白幅は行幅から除外される=末尾空白の
 * つぶしと一致する)。
 * </p>
 *
 * <p>
 * <b>伸縮モデル</b>: foliojet4のjustificationは伸長のみで縮小を実装して
 * いない({@code AbstractLineBox.align})ため、Glueのshrinkは常に0と
 * する——これによりK-Pは物理的に再現できない「詰め込んだ行」を決して
 * 選ばない。伸長は選択専用の控えめな近似で、空白Glueに幅の半分、各
 * breakpoint候補の直前に分割禁止ガード付きの0幅尾部Glue(約2em)を
 * 置く。<b>伸長は意図的に小さく保つ</b>——{@link TotalFit}はactiveの
 * 除去(TeXのdeactivation)を行わないため、伸長が大きく「ほぼ全ての
 * breakpointが実行可能」になるモデルでは候補集合がO(n²)に爆発して
 * 実用時間で解けない(実測でハング相当。増分4への申し送り)。この
 * 結果、行が大きく空く組み方はK-Pの実行可能解に入らず、事後の幅検証で
 * 検出されてlegacyへフォールバックする。
 * </p>
 *
 * <p>
 * 投影できない列(breakpoint候補なし、候補間の不可分な連続が行幅を
 * 超える、選択結果に溢れ行が残る等)はnullを返し、呼び出し側がlegacyへ
 * フォールバックする。純関数であり合成入力で単体テストできる。
 * </p>
 */
public final class TotalFitProjection {

	/** TeXの\hyphenpenalty相当のハイフン分割コストです。 */
	static final int HYPHEN_COST = 50;

	private TotalFitProjection() {
	}

	/**
	 * 計測済みイベントです。{@link TotalFitSession}が記録時に構築します。
	 */
	public sealed interface Piece {
		/** breakpoint候補の間の不可分な材料(グリフ等)の合計幅です。 */
		record Box(double width) implements Piece {
		}

		/** 空白({@code WhiteSpace})です。幅はつぶし前のadvanceです。 */
		record Space(double width) implements Piece {
		}

		/**
		 * ソフトハイフン({@code SoftHyphen})です。幅は分割時に実体化される
		 * ハイフングリフのadvanceです。
		 */
		record Hyphen(double width) implements Piece {
		}

		/** 明示改行({@code '\n'}、次のflushで強制改行)です。 */
		record LineFeed() implements Piece {
		}

		/**
		 * 下流へ配達されたflush(=breakpoint候補)です。
		 *
		 * @param ordinal セッション内のflush序数(0起点)
		 * @param stretch この候補位置の仮想的な伸長の基準量(現在フォント
		 *                サイズの半分)。尾部Glueのstretchはこの4倍(約2em)
		 */
		record Flush(int ordinal, double stretch) implements Piece {
		}
	}

	/**
	 * 選択されたbreakpoint列(flush序数の集合)です。再生時、
	 * {@link TotalFitSession}が各flushイベントの直前に
	 * {@link #arriveFlush}でカーソルを進め、{@link TextBuilder}が
	 * {@link #takeBreakAtCursor}で「このflushで改行するか」を一度だけ
	 * 消費します(legacyの{@code while(flush())}ループの再入で二重改行
	 * しないためのconsume-once)。
	 */
	public static final class Plan {
		private final BitSet chosen;

		private boolean pending = false;

		Plan(final BitSet chosen) {
			this.chosen = chosen;
		}

		/** 序数{@code ordinal}のflushイベントの到着を通知します。 */
		public void arriveFlush(final int ordinal) {
			this.pending = this.chosen.get(ordinal);
		}

		/** 現在のflushで改行すべきなら一度だけtrueを返します。 */
		public boolean takeBreakAtCursor() {
			if (!this.pending) {
				return false;
			}
			this.pending = false;
			return true;
		}

		/** 選択されたflush序数の集合を返します(テスト用)。 */
		public BitSet chosenOrdinals() {
			return (BitSet) this.chosen.clone();
		}
	}

	/**
	 * イベント列からbreakpoint列を選択します。
	 *
	 * @param pieces         計測済みイベント列
	 * @param firstLineWidth 先頭行の使用可能幅(text-indent適用済み)
	 * @param lineWidth      2行目以降の使用可能幅
	 * @param params         {@link TotalFit}のパラメータ
	 * @return 選択されたbreakpoint列。投影できない場合はnull(呼び出し側
	 *         はlegacyへフォールバックする)
	 */
	public static Plan plan(final List<Piece> pieces, final double firstLineWidth, final double lineWidth,
			final TotalFit.Parameters params) {
		if (!(firstLineWidth > 0) || !(lineWidth > 0) || Double.isInfinite(firstLineWidth)
				|| Double.isInfinite(lineWidth)) {
			return null;
		}
		final List<BreakNode> nodes = new ArrayList<>();
		final List<Integer> ordinals = new ArrayList<>();
		// breakpoint候補の間の不可分な連続幅。これが最小行幅を超える列は
		// K-Pの実行可能解が枯渇し全体が退化しうるため投影しない(legacyも
		// 同様に溢れるだけなので、フォールバックで出力はlegacyと一致する)
		final double maxUsable = Math.min(firstLineWidth, lineWidth);
		double unbreakable = 0;
		// 直前のbreakpoint候補以降に幅のある材料が出たか(材料なしのflushに
		// penaltyを置くと空行の候補を作ってしまうため抑制する)
		boolean material = false;
		boolean pendingForced = false;
		boolean anyCandidate = false;

		final int n = pieces.size();
		for (int i = 0; i < n; ++i) {
			switch (pieces.get(i)) {
			case Piece.Box box -> {
				nodes.add(new BreakNode.Box(box.width(), null, 0, 0));
				ordinals.add(-1);
				unbreakable += box.width();
				material = true;
				if (LayoutUtils.compare(unbreakable, maxUsable) > 0) {
					return null;
				}
			}

			case Piece.Space space -> {
				final Piece.Flush flush = !pendingForced && material && i + 1 < n
						&& pieces.get(i + 1) instanceof Piece.Flush f ? f : null;
				if (flush != null) {
					// 空白直後のflush: 空白Glueの手前にコスト0のpenaltyを
					// 置く(TeXの慣用形)。そこで破ると空白幅は行幅に
					// 入らず、次行頭では読み捨てられる——空白のつぶしと
					// 一致する。penalty方式なので尾部Glueのstretchが
					// 行内に算入される(Glue自体を破るとそのGlueの
					// stretchは行に入らない)
					addTailGlue(nodes, ordinals, flush);
					nodes.add(new BreakNode.Penalty(0, 0, false, null));
					ordinals.add(flush.ordinal());
					nodes.add(new BreakNode.Glue(space.width(), space.width() * 0.5, 0));
					ordinals.add(-1);
					++i;
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				} else {
					// flushを伴わない空白(nowrap等)・行頭の空白:
					// 分割禁止のGlue。materialは変えない(空白だけでは
					// 後続flushをbreakpoint候補にしない=空白のみの行を
					// 作らない)
					nodes.add(BreakNode.Penalty.forbidden());
					ordinals.add(-1);
					nodes.add(new BreakNode.Glue(space.width(), space.width() * 0.5, 0));
					ordinals.add(-1);
					unbreakable += space.width();
				}
			}

			case Piece.Hyphen hyphen -> {
				if (!pendingForced && material && i + 1 < n && pieces.get(i + 1) instanceof Piece.Flush f) {
					// SoftHyphen直後のflush: ハイフンpenalty。penalty幅は
					// 分割時にのみ行幅へ算入される=実体化されるハイフンの幅
					addTailGlue(nodes, ordinals, f);
					nodes.add(new BreakNode.Penalty(hyphen.width(), HYPHEN_COST, true, null));
					ordinals.add(f.ordinal());
					++i;
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				}
				// flushを伴わないソフトハイフンは幅0の無効果(候補にしない)
			}

			case Piece.LineFeed lf -> pendingForced = true;

			case Piece.Flush flush -> {
				if (pendingForced) {
					if (material) {
						addTailGlue(nodes, ordinals, flush);
					}
					nodes.add(BreakNode.Penalty.forced());
					ordinals.add(flush.ordinal());
					pendingForced = false;
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				} else if (material) {
					addTailGlue(nodes, ordinals, flush);
					nodes.add(new BreakNode.Penalty(0, 0, false, null));
					ordinals.add(flush.ordinal());
					unbreakable = 0;
					material = false;
					anyCandidate = true;
				}
				// 材料なしのflushは候補にしない
			}
			}
		}
		if (!anyCandidate || nodes.isEmpty()) {
			// breakpoint候補ゼロ(1行段落・nowrap等)は最適化する意味がない
			return null;
		}

		final LineMeasure measure = lineIndex -> lineIndex == 0 ? firstLineWidth : lineWidth;
		final List<TotalFit.BrokenLine> lines = TotalFit.totalFit(nodes, measure, params);

		// 幅の事後検証: 選択に溢れ行(実行可能解の枯渇によるfit-anyway
		// 退化)が残っていれば投影失敗として扱う
		final double[] sumWidth = new double[nodes.size() + 1];
		for (int i = 0; i < nodes.size(); ++i) {
			final BreakNode node = nodes.get(i);
			final double w = node instanceof BreakNode.Penalty ? 0 : node.width();
			sumWidth[i + 1] = sumWidth[i] + w;
		}
		final BitSet chosen = new BitSet();
		for (int lineIndex = 0; lineIndex < lines.size(); ++lineIndex) {
			final TotalFit.BrokenLine line = lines.get(lineIndex);
			final int breakIndex = line.breakIndex();
			// 行頭の読み捨てGlueを除いた自然幅
			int lineStart = line.begin();
			while (lineStart < breakIndex && nodes.get(lineStart) instanceof BreakNode.Glue) {
				++lineStart;
			}
			double natural = sumWidth[Math.min(breakIndex, nodes.size())] - sumWidth[lineStart];
			if (breakIndex < nodes.size() && nodes.get(breakIndex) instanceof BreakNode.Penalty penalty) {
				natural += penalty.width();
			}
			if (LayoutUtils.compare(natural, measure.width(lineIndex)) > 0) {
				return null;
			}
			if (line.kind() == TotalFit.BreakKind.PARAGRAPH_END) {
				continue;
			}
			if (breakIndex < 0 || breakIndex >= ordinals.size()) {
				return null;
			}
			final int ordinal = ordinals.get(breakIndex);
			if (ordinal < 0) {
				// 候補でないノードで破った——投影の不整合。安全側へ倒す
				return null;
			}
			chosen.set(ordinal);
		}
		return new Plan(chosen);
	}

	/**
	 * breakpoint候補の直前に、分割禁止ガード付きの0幅尾部Glue(この
	 * 候補で破った行が持ちうる仮想的な伸長。約2em)を置きます。ガードが
	 * ないとコスト・フラグなしの迂回breakpointができてしまう(強制改行の
	 * 手前の空行候補、hyphen demeritsの無効化)。
	 */
	private static void addTailGlue(final List<BreakNode> nodes, final List<Integer> ordinals,
			final Piece.Flush flush) {
		final double stretch = flush.stretch() * 4;
		if (stretch <= 0) {
			return;
		}
		nodes.add(BreakNode.Penalty.forbidden());
		ordinals.add(-1);
		nodes.add(new BreakNode.Glue(0, stretch, 0));
		ordinals.add(-1);
	}
}
