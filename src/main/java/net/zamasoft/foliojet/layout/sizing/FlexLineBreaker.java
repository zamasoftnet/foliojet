package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.List;

/**
 * Flexの行分割(css-flexbox-1 §9.3 step 5)の純粋計算です(Flex F2a、
 * 2026-08-02——consult-codex-2026-08-02-flexbox.txt)。itemをouter
 * hypothetical main sizeとmain gapで行へ集め、次のitemを足すと
 * コンテナ主軸内寸を超える位置で切る。各行は最低1 item(単体で
 * 超過するitemも自分の行を持つ)。
 *
 * <p>
 * 境界は「超えたら切る」(&gt;)——ちょうど収まる(==)は同じ行に残す。
 * 浮動小数の等値ぎわは{@code EPSILON}で「ちょうど」側へ倒す
 * (答申の検証条件: exact fitのFP誤差で行が割れない)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexLineBreaker {

	/** exact fit判定の許容誤差(pt)。 */
	private static final double EPSILON = 1e-6;

	private FlexLineBreaker() {
	}

	/** 1行の範囲です(from含む・to含まない、ソース順)。 */
	public record Line(int from, int to) {
		public int count() {
			return this.to - this.from;
		}
	}

	/**
	 * @param items ソース順のitem計測値
	 * @param innerMainSize コンテナ主軸内寸
	 * @param mainGap item間のgap
	 * @return 行のリスト(空入力は空リスト)
	 */
	public static List<Line> breakLines(final List<FlexItemMetrics> items, final double innerMainSize,
			final double mainGap) {
		final List<Line> lines = new ArrayList<>();
		int from = 0;
		double used = 0;
		for (int i = 0; i < items.size(); ++i) {
			final double outer = items.get(i).outerHypotheticalMain();
			final double candidate = (i > from ? used + mainGap : 0) + outer;
			if (i > from && candidate > innerMainSize + EPSILON) {
				lines.add(new Line(from, i));
				from = i;
				used = outer;
			} else {
				used = candidate;
			}
		}
		if (from < items.size()) {
			lines.add(new Line(from, items.size()));
		}
		return lines;
	}
}
