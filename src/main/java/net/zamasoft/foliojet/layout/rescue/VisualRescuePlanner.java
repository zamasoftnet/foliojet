package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 救済分割(visual rescue split)の判定を一手に引き受ける純関数です
 * (2026-07-25新設、増分1。{@code docs/consultations/consult-rescue-split-codex.md}
 * §1・§4。<b>まだ本番経路へは配線されていません</b>)。
 *
 * <p>
 * 呼び出し口は将来「通常フロー」と「float」の二か所だけになりますが、
 * 判定そのものは散らばらせずここへ集約します(答申§4)。入力は
 * </p>
 *
 * <ul>
 * <li>フラグメント先頭か({@code atFragmentStart})</li>
 * <li>利用可能なページ方向の量({@code available})</li>
 * <li>元ボックスのページ方向の占有量({@code sourcePageExtent})</li>
 * <li>すでに消費した量({@code offset})</li>
 * </ul>
 *
 * <p>
 * の4つだけで、ボックスにもコンテナにも触れません。
 * </p>
 *
 * <h2>前進保証</h2>
 *
 * <p>
 * 無限ループの不在はカウンタではなく次の構造で保証します(答申§1)。
 * </p>
 *
 * <ul>
 * <li>tailを作る断片は必ず{@link #MIN_RESCUE_ADVANCE}以上を消費する。</li>
 * <li>{@code nextOffset > offset}が厳密に成り立つときしか断片を作らない
 * (NaN・Infinity・極大doubleの丸めで停滞する場合は救済しない)。</li>
 * <li>残余が容量に収まったら{@code lastFragment}となり、tailを作らない。</li>
 * </ul>
 *
 * <p>
 * したがって救済が改ページを起こしたフラグメントは必ず正の量を消費し、
 * 残余は真に減少します。判定が{@link RescueDecision.None}を返した場合は
 * 従来どおりの終端(ページ先頭ならはみ出したまま描画)へ落ち、再試行は
 * しません。
 * </p>
 */
public final class VisualRescuePlanner {

	private VisualRescuePlanner() {
		// unused
	}

	/**
	 * 救済分割が1ステップで消費しなければならない最小量です
	 * ({@code 2 * LayoutUtils.THRESHOLD} = 1pt相当)。
	 *
	 * <p>
	 * {@link LayoutUtils#compare(double, double)}は{@code THRESHOLD}未満の
	 * 差を「同一」とみなすため、これを下回る前進は「進んでいない」と
	 * 区別できません。したがってtailを作る断片の下限をこの値に取ります。
	 * </p>
	 */
	public static final double MIN_RESCUE_ADVANCE = 2 * LayoutUtils.THRESHOLD;

	/**
	 * 救済分割が1ステップで消費しなければならない<b>実用上の</b>最小量です
	 * (2026-07-25、増分4で追加。「意図しない白紙(実質白紙)ページを作らない」
	 * という絶対要件のうち、<b>極小断片ページ</b>を防ぐ側)。
	 *
	 * <p>
	 * {@link #MIN_RESCUE_ADVANCE}(1pt)は「無限ループしないか」の下限
	 * であって、「1ptずつ切って何十ページも作ってよい」という意味では
	 * ありません。値20ptは
	 * {@code BreakableBuilder.MIN_PAGE_LIMIT}と同じで、エンジン自身が
	 * 「これより小さいページ方向容量は縮退として無視する」と決めている
	 * 唯一既存の閾値です。新しい魔法数を増やさず、既存の判断基準に
	 * そろえます(定数の重複定義を避けて参照しないのは、
	 * {@code layout.rescue}が{@code layout.builder.impl}に依存しない
	 * ためです)。
	 * </p>
	 */
	public static final double MIN_RESCUE_SLICE = 20;

	/**
	 * 救済分割が1ステップで消費しなければならない、フラグメンテナ容量に
	 * 対する最小の割合です(2026-07-25、増分4)。
	 *
	 * <p>
	 * 絶対値の下限({@link #MIN_RESCUE_SLICE})だけでは、大きなページで
	 * フロートの排除域などにより利用可能量が極端に小さくなった場合に、
	 * 数十ptの断片ページが延々と続く危険が残ります。「フラグメンテナの
	 * 1/4も使えないなら救済しない(=従来どおりの終端へ落ちる)」という
	 * 割合の下限を併せて課します。
	 * </p>
	 */
	public static final double MIN_RESCUE_FRACTION = 0.25;

	/**
	 * 与えられたフラグメンテナ容量に対して、救済を始めてよい利用可能量の
	 * 下限です。
	 *
	 * @param capacity フラグメンテナ(ページ・段・セル)のページ方向内寸
	 * @return 下限
	 */
	public static double minUsefulSlice(final double capacity) {
		if (!isDefined(capacity) || !(capacity > 0)) {
			return MIN_RESCUE_SLICE;
		}
		return Math.max(MIN_RESCUE_SLICE, capacity * MIN_RESCUE_FRACTION);
	}

	/**
	 * 配置方法が救済分割の対象になり得るかを返します。
	 *
	 * <p>
	 * 絶対配置は対象外です(合意仕様)。透かし・装飾・裁ち落としのように
	 * <b>意図的に</b>はみ出させる用途が多く、勝手に切ると明らかな誤りに
	 * なるためです。切りたい場合は通常ブロックで包めば対象になります。
	 * なお{@code BreakableBuilder.addBound()}も{@code PosType.ABSOLUTE}を
	 * 即座に通常配置へ送っており、除外は二重になります(答申§4)。
	 * </p>
	 *
	 * @param posType 配置方法({@code null}可——不明なら対象とみなす)
	 * @return 救済分割の対象になり得ればtrue
	 */
	public static boolean isRescuablePos(final PosType posType) {
		return posType != PosType.ABSOLUTE;
	}

	/**
	 * 配置方法の除外を含めて次の断片を決めます。
	 *
	 * @param posType          対象ボックスの配置方法
	 * @param atFragmentStart  フラグメント(ページ・段・セル)の先頭か
	 * @param available        利用可能なページ方向の量
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           すでに消費したページ方向の量
	 * @return 判定結果
	 */
	public static RescueDecision plan(final PosType posType, final boolean atFragmentStart, final double available,
			final double sourcePageExtent, final double offset) {
		if (!isRescuablePos(posType)) {
			return new RescueDecision.None(RescueDecision.Reason.ABSOLUTE);
		}
		return plan(atFragmentStart, available, sourcePageExtent, offset);
	}

	/**
	 * フラグメンテナ容量を考慮して次の断片を決めます(<b>配線はこれを
	 * 使います</b>)。
	 *
	 * <p>
	 * {@link #plan(boolean, double, double, double)}の「前進保証」に加えて、
	 * <b>極小断片ページを作らない</b>という要件を課します
	 * ({@link #minUsefulSlice(double)})。これは「無限ループしないか」とは
	 * 別の判断で、「意図しない実質白紙ページを作らない」という絶対要件の
	 * 側です。
	 * </p>
	 *
	 * <p>
	 * 下限は<b>両側</b>に課します(2026-07-25、増分6/7で追加した
	 * 末尾側)。
	 * </p>
	 *
	 * <ul>
	 * <li><b>先頭側</b>: 利用可能量が{@link #minUsefulSlice(double)}未満なら
	 * 救済しない——数ptずつの断片ページが連続するのを防ぐ。</li>
	 * <li><b>末尾側</b>: <b>はみ出し量</b>が{@link #MIN_RESCUE_SLICE}未満なら
	 * 救済しない——数ptのはみ出しを救うために丸ごと1ページ増やすと、
	 * そのページは実質白紙になる。ここに割合の下限を課さないのは、
	 * 「A4に貼られた少しだけ背の高い画像」のような<b>本来の用途</b>を
	 * 拒否してしまうためで、エンジン自身の縮退閾値(20pt)だけを使う。</li>
	 * </ul>
	 *
	 * <p>
	 * どちらの下限も<b>救済を始めるかどうか</b>({@code offset == 0})の
	 * 判定にだけ効きます。すでに切り始めている({@code offset > 0})断片で
	 * 「小さすぎるからやめる」を選ぶと、残りの内容が失われる(=従来どおり
	 * はみ出して切り捨てられる)ため、開始後は前進保証だけを守って必ず
	 * 切り進めます。
	 * </p>
	 *
	 * @param posType          対象ボックスの配置方法
	 * @param atFragmentStart  フラグメント(ページ・段・セル)の先頭か
	 * @param capacity         フラグメンテナのページ方向内寸(容量)
	 * @param available        利用可能なページ方向の量
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           すでに消費したページ方向の量
	 * @return 判定結果
	 */
	public static RescueDecision planInFragmentainer(final PosType posType, final boolean atFragmentStart,
			final double capacity, final double available, final double sourcePageExtent, final double offset) {
		final RescueDecision decision = plan(posType, atFragmentStart, available, sourcePageExtent, offset);
		if (!(decision instanceof RescueDecision.Slice slice)) {
			return decision;
		}
		if (!slice.firstFragment()) {
			// 開始後は前進保証だけを守る(やめると内容が失われる)
			return decision;
		}
		if (available < minUsefulSlice(capacity)) {
			return new RescueDecision.None(RescueDecision.Reason.SLIVER_CAPACITY);
		}
		if (sourcePageExtent - slice.nextOffset() < MIN_RESCUE_SLICE) {
			// 末尾側の守り: はみ出し量が実用上小さすぎる。数ptのために
			// 1ページ増やすと、そのページは実質白紙になる
			return new RescueDecision.None(RescueDecision.Reason.SLIVER_REMAINDER);
		}
		return decision;
	}

	/**
	 * 次の断片を決めます(前進保証だけを見る中核判定)。
	 *
	 * <p>
	 * {@code atFragmentStart}が偽のときは救済しません。まだ「次の
	 * フラグメントへ送る」という通常の手段が残っており、救済は
	 * <b>その手段を使い切った地点</b>(=現在はみ出したまま描画している
	 * 地点)だけを置き換えるものだからです。
	 * </p>
	 *
	 * @param atFragmentStart  フラグメント(ページ・段・セル)の先頭か
	 * @param available        利用可能なページ方向の量
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           すでに消費したページ方向の量
	 * @return 判定結果
	 */
	public static RescueDecision plan(final boolean atFragmentStart, final double available,
			final double sourcePageExtent, final double offset) {
		if (!atFragmentStart) {
			return new RescueDecision.None(RescueDecision.Reason.NOT_FIRST);
		}
		if (!isDefined(available) || !isDefined(sourcePageExtent) || !isDefined(offset)) {
			return new RescueDecision.None(RescueDecision.Reason.UNDEFINED_GEOMETRY);
		}
		if (offset < 0 || !(sourcePageExtent > 0)) {
			return new RescueDecision.None(RescueDecision.Reason.INVALID_GEOMETRY);
		}
		final double remaining = sourcePageExtent - offset;
		// 残余の判定もLayoutUtils.compare基準にする(2026-07-25、増分4)。
		// 素の`remaining > 0`では、丸めで0.1pt等の残余が出たときに
		// 「実質白紙の断片ページ」を1枚作ってしまう。エンジンが
		// 「同一」とみなす差(THRESHOLD)以下の残余は消費済みとする
		if (LayoutUtils.compare(remaining, 0) <= 0) {
			return new RescueDecision.None(RescueDecision.Reason.EXHAUSTED);
		}

		// 残余が容量に収まるか(THRESHOLD許容つき)。収まるなら最終断片で、
		// tailを作らないため前進量の下限は要らない。収まらないなら容量
		// いっぱいを切り、必ずtailが続く。
		final boolean fits = LayoutUtils.compare(remaining, available) <= 0;
		if (fits) {
			if (offset == 0) {
				// 先頭でそもそも収まっている——救済不要(通常経路)
				return new RescueDecision.None(RescueDecision.Reason.FITS);
			}
			final double nextOffset = offset + remaining;
			if (!(nextOffset > offset)) {
				// 極大doubleの丸めなど。進めないなら救済しない
				return new RescueDecision.None(RescueDecision.Reason.NO_PROGRESS);
			}
			return new RescueDecision.Slice(offset, remaining, nextOffset, false, true);
		}

		if (!(available >= MIN_RESCUE_ADVANCE)) {
			// 容量0・容量1pt未満・負の容量。外側のfragmentainerへ委譲する
			return new RescueDecision.None(RescueDecision.Reason.INSUFFICIENT_CAPACITY);
		}
		final double nextOffset = offset + available;
		if (!(nextOffset > offset)) {
			return new RescueDecision.None(RescueDecision.Reason.NO_PROGRESS);
		}
		return new RescueDecision.Slice(offset, available, nextOffset, offset == 0, false);
	}

	/**
	 * 有限かつ「未確定」でない実数であればtrueを返します。
	 * {@code LayoutUtils.NONE}はAUTO等の未確定を表すマジック値なので、
	 * 数値としては有限でも幾何としては扱えません。
	 */
	private static boolean isDefined(final double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v) && !LayoutUtils.isNone(v);
	}
}
