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
 */
public record FloatExclusion(long order, FloatSide side, AxisSpan pageSpan, AxisSpan lineSpan) {
	public FloatExclusion {
		if (side == null) {
			throw new IllegalArgumentException("side must not be null");
		}
		if (pageSpan == null || lineSpan == null) {
			throw new IllegalArgumentException("pageSpan/lineSpan must not be null");
		}
	}
}
