package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * 論理的な辺(block-start/end・inline-start/end)です。writing-mode
 * ({@link BlockFlow})とdirection({@link Direction})の両方から物理的な辺
 * ({@link Side})への対応を解決します。
 * <p>
 * {@link Side#resolve}(物理プロパティの「回転」)との違いは2点:
 * (1) block軸だけでなくinline軸(方向性、RTLでの左右反転)も考慮する、
 * (2) {@code -cssj-direction-mode}(既定PHYSICAL=無回転)による無効化を
 * 受けない——CSS仕様上、論理プロパティは常にwriting-mode/directionに従って
 * 解決されるべきもので、foliojet4独自の「物理プロパティの回転」機能とは
 * 別の、常時有効な仕組みだからです(2026-07-19実装)。
 * </p>
 */
public enum LogicalSide {
	BLOCK_START, BLOCK_END, INLINE_START, INLINE_END;

	static final LogicalSide[] VALUES = values();

	/**
	 * 現在のwriting-mode/directionのもとで、この論理辺が対応する物理的な辺を
	 * 返します。
	 */
	public Side toPhysical(CSSStyle style) {
		WritingMode flow = BlockFlow.get(style);
		boolean rtl = Direction.get(style) == AbstractTextParams.DIRECTION_RTL;
		switch (flow) {
		case TB:
			// 横書き: block軸=上下、inline軸=左右
			switch (this) {
			case BLOCK_START:
				return Side.TOP;
			case BLOCK_END:
				return Side.BOTTOM;
			case INLINE_START:
				return rtl ? Side.RIGHT : Side.LEFT;
			case INLINE_END:
				return rtl ? Side.LEFT : Side.RIGHT;
			default:
				throw new IllegalStateException();
			}
		case RL:
			// 縦書き(右→左): block軸=左右、inline軸=上下
			switch (this) {
			case BLOCK_START:
				return Side.RIGHT;
			case BLOCK_END:
				return Side.LEFT;
			case INLINE_START:
				return rtl ? Side.BOTTOM : Side.TOP;
			case INLINE_END:
				return rtl ? Side.TOP : Side.BOTTOM;
			default:
				throw new IllegalStateException();
			}
		case LR:
			// 縦書き(左→右): block軸=左右、inline軸=上下(縦書きでは
			// RL/LRいずれもinline軸の向きは同じ、CSS Writing Modes仕様どおり)
			switch (this) {
			case BLOCK_START:
				return Side.LEFT;
			case BLOCK_END:
				return Side.RIGHT;
			case INLINE_START:
				return rtl ? Side.BOTTOM : Side.TOP;
			case INLINE_END:
				return rtl ? Side.TOP : Side.BOTTOM;
			default:
				throw new IllegalStateException();
			}
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 物理・論理いずれのプロパティが明示指定されているかに基づいて値を
	 * 解決します。物理側(例: margin-top)が明示指定されていればそちらを
	 * 優先し、無ければ対応する論理側(例: writing-mode/directionにより
	 * margin-topに解決されるmargin-block-start等)を見て、両方とも
	 * 未指定なら物理側の既定値を返します。
	 * <p>
	 * CSS仕様が要求する「カスケード出現順で後から宣言された方が勝つ」
	 * という物理⇔論理間の厳密な優先順位までは実装していません(この
	 * 実装の値保存モデルがプロパティ名ごとに独立したスロットを持つため、
	 * 異なるスロット間の出現順比較には対応していない)。「物理指定が
	 * あれば常に物理が勝つ」という単純化した規則を採用しています
	 * (docs/PLAN.md参照)。
	 * </p>
	 *
	 * @param style           対象スタイル
	 * @param requestedSide   呼び出し側が求めている物理的な辺(回転前)
	 * @param physicalBySide  {@link Side#ordinal()}で引く物理プロパティの配列
	 * @param logicalBySide   {@link LogicalSide#ordinal()}で引く論理プロパティの配列
	 */
	public static net.zamasoft.foliojet.css.value.Value resolve(CSSStyle style, Side requestedSide,
			PrimitivePropertyInfo[] physicalBySide, PrimitivePropertyInfo[] logicalBySide) {
		Side resolvedSide = requestedSide.resolve(style);
		PrimitivePropertyInfo physicalInfo = physicalBySide[resolvedSide.ordinal()];
		if (style.isDeclared(physicalInfo)) {
			return style.get(physicalInfo);
		}
		for (LogicalSide logical : VALUES) {
			if (logical.toPhysical(style) == resolvedSide) {
				PrimitivePropertyInfo logicalInfo = logicalBySide[logical.ordinal()];
				if (style.isDeclared(logicalInfo)) {
					return style.get(logicalInfo);
				}
				break;
			}
		}
		return style.get(physicalInfo);
	}
}
