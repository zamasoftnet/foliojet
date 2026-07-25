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
	 * 次の断片を決めます。
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
		if (!(remaining > 0)) {
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
