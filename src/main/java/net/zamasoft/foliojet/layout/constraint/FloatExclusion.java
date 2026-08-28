package net.zamasoft.foliojet.layout.constraint;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * 1個の配置済み浮動ボックスによる排除帯です(2026-07-23新設、
 * 排除域のConstraintSpace入力化——`docs/consultations/consult
 * -exclusion-zone-codex.txt`の設計に基づく)。
 *
 * <p>
 * 意図的に{@code IFloatBox}(live box)を保持しない——制約入力が
 * 再生・再構築後の旧箱を参照し続けることを避けるため。{@code order}は
 * 既存{@code BlockBuilder.FLOAT_COMP}の安定ソート契約
 * (同じ{@code pageEnd}の浮動体は追加順)を、この値型だけでも再現
 * できるように保持する挿入通し番号。
 * </p>
 *
 * <p>
 * {@code shape}は{@code shape-outside}で解決済みの排除形状
 * (2026-08-29)。nullなら従来どおりマージンボックス矩形。形状は
 * 行ボックスの配置({@link ExclusionSpace#scanLineBand})だけが
 * {@link #lineSpanAt}経由で見る——浮動体同士の配置とBFCを作るブロックの
 * 回避は仕様(css-shapes-1 §4.1)どおり矩形{@code lineSpan}のまま。
 * </p>
 */
public record FloatExclusion(long order, FloatSide side, AxisSpan pageSpan, AxisSpan lineSpan,
		ExclusionShape shape) {
	public FloatExclusion {
		if (side == null) {
			throw new IllegalArgumentException("side must not be null");
		}
		if (pageSpan == null || lineSpan == null) {
			throw new IllegalArgumentException("pageSpan/lineSpan must not be null");
		}
	}

	/** 形状なし(マージンボックス矩形)の排除帯。 */
	public FloatExclusion(final long order, final FloatSide side, final AxisSpan pageSpan, final AxisSpan lineSpan) {
		this(order, side, pageSpan, lineSpan, null);
	}

	/**
	 * ページ方向の帯[pageStart, pageEnd]でこの浮動体が占める行方向の
	 * 範囲です。形状なしなら常に{@link #lineSpan}。形状ありで帯と形状が
	 * 交わらなければnull(その帯では行を狭めない)。
	 */
	public AxisSpan lineSpanAt(final double pageStart, final double pageEnd) {
		if (this.shape == null) {
			return this.lineSpan;
		}
		return this.shape.lineSpanAt(pageStart, pageEnd);
	}
}
