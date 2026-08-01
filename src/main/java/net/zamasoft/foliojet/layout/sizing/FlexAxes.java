package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Flexの論理軸(main/cross)と物理軸(line/page)の対応です(Flex F4a、
 * 2026-08-02——consult-codex-2026-08-02-flexbox.txt)。§2 Flex Layout
 * Box Modelの写像を一点に集約する純粋値——F4b以降のcolumn実装と
 * F5bのreverse、F6の縦書きがこの表だけを参照する。
 *
 * <p>
 * rowの主軸はinline(行)方向=物理line軸、columnはblock方向=物理page軸。
 * 書字方向はWritingModeが吸収する(縦書きでは物理line軸が上下になる——
 * 消費側は{@code getLineExtent(flow)}系の論理アクセサを使うため、
 * ここではmainがline軸かどうかとreverseの有無だけを持てばよい)。
 * RTL(direction: rtl)はサブセット外。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record FlexAxes(boolean mainIsLine, boolean mainReversed, boolean crossReversed) {

	public static FlexAxes of(final WritingMode flow, final FlexDirection direction, final FlexWrap wrap) {
		// flowはline/page軸の物理向きを決めるが、論理写像はdirection/wrapだけで
		// 決まる(消費側が論理アクセサを使う前提)。flowは将来のRTL・
		// 直交item対応の引数席として維持
		return new FlexAxes(direction.isRow(), direction.isReverse(), wrap == FlexWrap.WRAP_REVERSE);
	}

	/** 主軸がpage軸(=column系)か。 */
	public boolean mainIsPage() {
		return !this.mainIsLine;
	}
}
