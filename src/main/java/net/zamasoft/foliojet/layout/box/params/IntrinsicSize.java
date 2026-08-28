package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.layout.sizing.Sizing;

/**
 * 固有寸法キーワード(css-sizing-3 §2.2 {@code max-content}/
 * {@code min-content}/{@code fit-content}/{@code fit-content(L)})の
 * レイアウト側表現です(2026-08-29)。
 *
 * <p>
 * <b>{@link LengthType}へ値を足さず別枠で運ぶ理由</b>。{@link Dimension}は
 * 型を2ビットに詰めており、また{@code LengthType}のswitchは main 全体に
 * 176箇所ある。キーワードは長さではなく「内容から決める」という
 * <b>寸法決定の方式</b>なので、{@code Dimension}上はAUTOのまま(=行方向を
 * 内容で決める既存経路を通る)にして、方式だけを{@link BlockParams}の
 * {@code intrinsicLine}/{@code intrinsicMinLine}/{@code intrinsicMaxLine}
 * が運ぶ。解決は{@code AbstractStaticBlockBox.shrinkToFit}(浮動体・
 * inline-block・固有寸法付き通常フロー)と
 * {@code AbsoluteBlockBox.shrinkToFit}で行う。ブロック軸(ページ方向)の
 * キーワードは仕様どおり内容高さ(=auto)なので、マッパーの段階で捨てる。
 * </p>
 *
 * @param kind     方式
 * @param argument {@code fit-content(L)}の上限L。無ければnull(利用可能寸法を
 *                 上限にする)
 */
public record IntrinsicSize(Kind kind, Length argument) {
	public enum Kind {
		MAX_CONTENT, MIN_CONTENT, FIT_CONTENT;
	}

	public static final IntrinsicSize MAX_CONTENT = new IntrinsicSize(Kind.MAX_CONTENT, null);
	public static final IntrinsicSize MIN_CONTENT = new IntrinsicSize(Kind.MIN_CONTENT, null);
	public static final IntrinsicSize FIT_CONTENT = new IntrinsicSize(Kind.FIT_CONTENT, null);

	/**
	 * {@code fit-content(L)}を生成します。Lがautoなら引数無しと同じ。
	 */
	public static IntrinsicSize fitContent(final Length bound) {
		if (bound == null || bound.getType() == LengthType.AUTO) {
			return FIT_CONTENT;
		}
		return new IntrinsicSize(Kind.FIT_CONTENT, bound);
	}

	/** {@code fit-content(L)}の上限Lを持つかどうか。 */
	public boolean hasArgument() {
		return this.argument != null;
	}

	/**
	 * 行方向の使用寸法(content-box)を返します。
	 *
	 * @param minContent 最小内容寸法
	 * @param maxContent 最大内容寸法
	 * @param bound      fit-contentの上限(利用可能寸法、または解決済みの引数L)
	 * @return 使用寸法
	 */
	public double resolve(final double minContent, final double maxContent, final double bound) {
		return switch (this.kind) {
		case MAX_CONTENT -> maxContent;
		case MIN_CONTENT -> minContent;
		case FIT_CONTENT -> Sizing.fitContent(minContent, maxContent, bound);
		};
	}

	public String toString() {
		return this.argument == null ? this.kind.name() : this.kind.name() + "(" + this.argument + ")";
	}
}
