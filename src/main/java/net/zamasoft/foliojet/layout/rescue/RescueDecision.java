package net.zamasoft.foliojet.layout.rescue;

/**
 * 救済分割(visual rescue split)の1ステップの判定結果です
 * (2026-07-25新設、増分1。{@code docs/consultations/consult-rescue-split-codex.md}
 * §1・§2。まだ本番経路へは配線されていません)。
 *
 * <p>
 * 救済分割は「ページ先頭でもなお収まらない分割不能ボックス」を
 * <b>幾何学的に</b>切って次のフラグメントへ送る仕組みで、通常の改ページ
 * 分割とは別物です。この型は、そのステップで
 * </p>
 *
 * <ul>
 * <li>{@link None} — 救済しない(=従来どおりの挙動へ委ねる)</li>
 * <li>{@link Slice} — {@code offset}から{@code sliceExtent}だけ切り出す</li>
 * </ul>
 *
 * <p>
 * のどちらかを表します。判定は{@link VisualRescuePlanner}の純関数だけが
 * 作ります(副作用なし・ボックスに触れない)。
 * </p>
 */
public sealed interface RescueDecision {

	/**
	 * 救済しなかった理由です。分岐の意図をテストで固定できるように、
	 * 単なる{@code false}ではなく理由を持たせています。
	 */
	public enum Reason {
		/** フラグメントの先頭ではない(=まだ次のフラグメントへ送る余地がある)。 */
		NOT_FIRST,
		/** 絶対配置(合意仕様により対象外——意図的なはみ出しを壊さない)。 */
		ABSOLUTE,
		/** NaN・Infinity・{@code LayoutUtils.NONE}(未確定)が混じっている。 */
		UNDEFINED_GEOMETRY,
		/** 負のoffset、非正の元寸法など、幾何として成立しない。 */
		INVALID_GEOMETRY,
		/** 消費済みoffsetが元寸法に達しており、残余がない。 */
		EXHAUSTED,
		/** 先頭断片で容量に収まる——救済不要(通常経路)。 */
		FITS,
		/** 容量が{@link VisualRescuePlanner#MIN_RESCUE_ADVANCE}未満(前進保証を満たせない)。 */
		INSUFFICIENT_CAPACITY,
		/** 加算の丸めで{@code offset}が厳密増加しない(極大doubleなど)。 */
		NO_PROGRESS;
	}

	/**
	 * 救済しません。呼び出し側は従来どおりの終端(ページ先頭なら
	 * はみ出したまま描画、そうでなければ次フラグメントへ委譲)へ落ちます。
	 *
	 * @param reason 救済しなかった理由
	 */
	public record None(Reason reason) implements RescueDecision {
		public None {
			if (reason == null) {
				throw new IllegalArgumentException("reason");
			}
		}
	}

	/**
	 * 区間{@code [offset, nextOffset)}を1断片として切り出します
	 * (「区間分割値型」)。レイアウト寸法は一切変わらず、ページ上の
	 * 占有量だけが{@code sliceExtent}になります。
	 *
	 * <p>
	 * 不変条件(コンストラクタで検査):
	 * </p>
	 * <ul>
	 * <li>{@code offset >= 0}</li>
	 * <li>{@code sliceExtent > 0}</li>
	 * <li>{@code nextOffset > offset}(前進保証。等しければ無限ループ)</li>
	 * <li>{@code firstFragment == (offset == 0)}</li>
	 * </ul>
	 *
	 * @param offset        この断片が始まるページ方向位置(元ボックス座標)
	 * @param sliceExtent   この断片がフラグメント上で占有するページ方向寸法
	 * @param nextOffset    次の断片が始まる位置({@code offset + sliceExtent})
	 * @param firstFragment 先頭断片(上マージン・上枠線を持つ)か
	 * @param lastFragment  最終断片(下枠線・下マージンを持ち、tailを作らない)か
	 */
	public record Slice(
			double offset,
			double sliceExtent,
			double nextOffset,
			boolean firstFragment,
			boolean lastFragment) implements RescueDecision {

		public Slice {
			if (!(offset >= 0)) {
				throw new IllegalArgumentException("offset=" + offset);
			}
			if (!(sliceExtent > 0)) {
				throw new IllegalArgumentException("sliceExtent=" + sliceExtent);
			}
			if (!(nextOffset > offset)) {
				// 前進保証: ここを破ると改ページループが停止しない
				throw new IllegalArgumentException("前進しない: offset=" + offset + " nextOffset=" + nextOffset);
			}
			if (firstFragment != (offset == 0)) {
				throw new IllegalArgumentException("firstFragment=" + firstFragment + " offset=" + offset);
			}
		}

		/** 続きの断片(=PDFのartifactとして出す側)であればtrueを返します。 */
		public boolean isContinuation() {
			return !this.firstFragment;
		}

		/** この断片のあとに残余断片(tail)を作る必要があればtrueを返します。 */
		public boolean hasTail() {
			return !this.lastFragment;
		}
	}
}
