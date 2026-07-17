package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 絶対配置ボックスの行方向の寸法・インセット・マージン解決です。
 * SPEC CSS2.1 10.3.7(制約式 start + margin + size + margin + end = available)
 * をボックスに触れない純関数として実装します。auto は {@link LayoutUtils#NONE}
 * で表します。max/min クランプで寸法が変わった場合は解決をやり直します
 * (旧実装の state ループと同じ)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class AbsoluteSizing {
	/**
	 * 入力です。auto は NONE で表します。
	 *
	 * @param available       包含ブロックの行方向寸法(パディング込み)
	 * @param size            指定寸法(box-sizing 調整済み。auto=NONE)
	 * @param maxSize         最大寸法(なし=NONE)
	 * @param minSize         最小寸法(なし=0相当の解決済み値)
	 * @param insetStart      行方向始端インセット(auto=NONE)
	 * @param insetEnd        行方向終端インセット(auto=NONE)
	 * @param marginStart     解決済み始端マージン
	 * @param marginEnd       解決済み終端マージン
	 * @param marginStartAuto 始端マージンが auto 指定
	 * @param marginEndAuto   終端マージンが auto 指定
	 * @param frameExtent     行方向フレーム(マージン+ボーダー+パディング)合計
	 * @param minContent      最小内容寸法
	 * @param maxContent      最大内容寸法
	 */
	public record Input(double available, double size, double maxSize, double minSize, double insetStart,
			double insetEnd, double marginStart, double marginEnd, boolean marginStartAuto, boolean marginEndAuto,
			double frameExtent, double minContent, double maxContent) {
	}

	/**
	 * 解決結果です。
	 *
	 * @param size        行方向寸法
	 * @param insetStart  始端インセット(確定値)
	 * @param insetEnd    終端インセット(確定値)
	 * @param marginStart 始端マージン(auto 未解決なら NONE)
	 * @param marginEnd   終端マージン(auto 未解決なら NONE)
	 */
	public record Result(double size, double insetStart, double insetEnd, double marginStart, double marginEnd) {
	}

	private AbsoluteSizing() {
		// utility
	}

	/**
	 * ページ方向の入力です(CSS2.1 10.6.4 相当)。auto は NONE で表します。
	 * 行方向({@link Input})と違い shrink-to-fit(min/max-content)は関与せず、
	 * 内容の実寸(contentSize)を直接使います。
	 *
	 * @param available   包含ブロックのページ方向寸法(パディング込み)
	 * @param size        指定寸法(auto=NONE。box-sizing 調整は呼び出し側で後段)
	 * @param maxSize     最大寸法(なし=NONE)
	 * @param minSize     最小寸法(解決済み値)
	 * @param insetStart  ページ方向始端インセット(横書き=top / 縦書き=left。auto=NONE)
	 * @param insetEnd    ページ方向終端インセット(横書き=bottom / 縦書き=right。auto=NONE)
	 * @param marginStart 始端マージン(auto=NONE)
	 * @param marginEnd   終端マージン(auto=NONE)
	 * @param contentSize 内容のページ方向実寸
	 * @param frameExtent ボーダー+パディングのページ方向合計
	 */
	public record PageInput(double available, double size, double maxSize, double minSize, double insetStart,
			double insetEnd, double marginStart, double marginEnd, double contentSize, double frameExtent) {
	}

	/**
	 * ページ方向の解決結果です。
	 *
	 * @param size        ページ方向寸法
	 * @param insetStart  始端インセット(確定値)
	 * @param marginStart 始端マージン(確定値)
	 * @param marginEnd   終端マージン(確定値)
	 */
	public record PageResult(double size, double insetStart, double marginStart, double marginEnd) {
	}

	/**
	 * ページ方向の寸法・インセット・マージンを解決します
	 * (旧 AbsoluteBlockBox.finishLayout の縦横鏡像 約100行×2 の統合。
	 * 忠実移植 — dangling-else の「制限しすぎ」挙動も維持)。
	 *
	 * @param in 入力
	 * @return 解決結果
	 */
	public static PageResult resolvePage(final PageInput in) {
		double size = in.size();
		double start = 0;
		double marginStart = 0, marginEnd = 0;
		for (int state = 0; state < 2; ++state) {
			marginStart = in.marginStart();
			marginEnd = in.marginEnd();
			start = in.insetStart();
			double end = in.insetEnd();
			if (!LayoutUtils.isNone(start) && !LayoutUtils.isNone(end) && !LayoutUtils.isNone(size)) {
				// 過剰指定: マージン(auto)で吸収する
				if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginStart = marginEnd = (in.available() - start - end - size - in.frameExtent()) / 2.0;
				}
				if (LayoutUtils.isNone(marginStart) && !LayoutUtils.isNone(marginEnd)) {
					marginStart = in.available() - start - end - size - marginEnd - in.frameExtent();
				}
				if (!LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginEnd = in.available() - start - end - size - marginStart - in.frameExtent();
				} else {
					// 制限しすぎ(旧実装の dangling-else をそのまま維持)
					end = 0;
				}
			} else {
				if (LayoutUtils.isNone(marginStart)) {
					marginStart = 0;
				}
				if (LayoutUtils.isNone(marginEnd)) {
					marginEnd = 0;
				}
				if (LayoutUtils.isNone(size)) {
					if (LayoutUtils.isNone(start) && LayoutUtils.isNone(end)) {
						start = 0;
						size = in.contentSize();
					} else if (LayoutUtils.isNone(start)) {
						size = in.contentSize();
						start = in.available() - end - size - marginStart - marginEnd - in.frameExtent();
					} else if (LayoutUtils.isNone(end)) {
						size = in.contentSize();
						end = in.available() - start - size - marginStart - marginEnd - in.frameExtent();
					} else {
						size = in.available() - start - end - marginStart - marginEnd - in.frameExtent();
					}
				} else {
					if (LayoutUtils.isNone(end)) {
						if (LayoutUtils.isNone(start)) {
							start = 0;
						}
						end = in.available() - start - size - marginStart - marginEnd - in.frameExtent();
					} else {
						start = in.available() - end - size - marginStart - marginEnd - in.frameExtent();
					}
				}
			}
			switch (state) {
			case 0:
				if (!LayoutUtils.isNone(in.maxSize()) && size > in.maxSize()) {
					size = in.maxSize();
					continue;
				}
				state = 1;
			case 1:
				if (size < in.minSize()) {
					size = in.minSize();
					continue;
				}
				state = 2;
				break;
			}
		}
		return new PageResult(size, start, marginStart, marginEnd);
	}

	/**
	 * 行方向の寸法・インセット・マージンを解決します。
	 *
	 * @param in 入力
	 * @return 解決結果
	 */
	public static Result resolve(final Input in) {
		double size = in.size();
		double start = 0, end = 0;
		double marginStart = 0, marginEnd = 0;
		for (int state = 0; state < 2; ++state) {
			start = in.insetStart();
			end = in.insetEnd();
			if (!LayoutUtils.isNone(start) && !LayoutUtils.isNone(end) && !LayoutUtils.isNone(size)) {
				// 過剰指定: マージン(auto)で吸収する
				marginStart = in.marginStartAuto() ? LayoutUtils.NONE : in.marginStart();
				marginEnd = in.marginEndAuto() ? LayoutUtils.NONE : in.marginEnd();
				if (LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginStart = marginEnd = (in.available() - start - end - size - in.frameExtent()) / 2.0;
				}
				if (LayoutUtils.isNone(marginStart) && !LayoutUtils.isNone(marginEnd)) {
					marginStart = in.available() - start - end - size - in.frameExtent();
				}
				if (!LayoutUtils.isNone(marginStart) && LayoutUtils.isNone(marginEnd)) {
					marginEnd = in.available() - start - end - size - in.frameExtent();
				} else {
					// 制限しすぎ(旧実装の dangling-else をそのまま維持)
					end = 0;
				}
			} else {
				marginStart = in.marginStart();
				marginEnd = in.marginEnd();
				if (LayoutUtils.isNone(size)) {
					if (!LayoutUtils.isNone(start) && !LayoutUtils.isNone(end)) {
						size = in.available() - start - end - in.frameExtent();
					} else {
						size = in.maxContent();
						final double limit = in.available() - in.frameExtent();
						if (LayoutUtils.isNone(start) && LayoutUtils.isNone(end)) {
							size = Sizing.fitContent(in.minContent(), size, limit);
							start = end = 0;
						} else if (LayoutUtils.isNone(start)) {
							// 台帳#2 解消(2026-07-17): 旧縦書き変種
							// fitContent(minContent - inset, …, limit) を廃し、
							// 10.3.7 の形 fitContent(minContent, …, limit - inset) に統一
							size = Sizing.fitContent(in.minContent(), size, limit - end);
							start = in.available() - end - size - in.frameExtent();
						} else {
							size = Sizing.fitContent(in.minContent(), size, limit - start);
							end = in.available() - start - size - in.frameExtent();
						}
					}
				} else {
					if (LayoutUtils.isNone(end)) {
						if (LayoutUtils.isNone(start)) {
							start = 0;
						}
						end = in.available() - start - size - in.frameExtent();
					} else {
						start = in.available() - end - size - in.frameExtent();
					}
				}
			}
			switch (state) {
			case 0:
				if (!LayoutUtils.isNone(in.maxSize()) && size > in.maxSize()) {
					size = in.maxSize();
					continue;
				}
				state = 1;
			case 1:
				if (size < in.minSize()) {
					size = in.minSize();
					continue;
				}
				state = 2;
				break;
			}
		}
		return new Result(size, start, end, marginStart, marginEnd);
	}
}
