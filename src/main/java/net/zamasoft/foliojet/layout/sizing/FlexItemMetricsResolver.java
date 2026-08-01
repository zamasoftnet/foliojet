package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;

/**
 * authoredスタイルと内在サイズからFlex itemの主軸計測値
 * ({@link FlexItemMetrics})を導く純粋計算です(Flex F1b、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q3「TwoPass intrinsicsからのbasis」)。
 * 横書きrow初期サブセット:
 *
 * <ol>
 * <li>flex base size(§9.2.3): definite basis→その値(min/max未適用)。
 * percentage basisはコンテナ主軸がdefiniteなら解決、indefiniteならauto扱い。
 * basis:auto→widthプロパティ、それもautoまたはbasis:content→max-content</li>
 * <li>min-width:auto(§4.5): scrollableなら0、非scrollableは
 * min(min-content, definite preferred width)(preferredがautoなら
 * min-content)、さらにdefinite maxでclamp</li>
 * <li>hypothetical main size: baseをused min/maxでclamp</li>
 * </ol>
 *
 * <p>
 * 全入力はpt。preferred/min/maxと%basisの解決結果はbox-sizing:border-box
 * のとき枠(border+padding)を引いてcontent-box内寸へ正規化する
 * (負になれば0)。内在サイズ(minContent/maxContent)は元々内寸。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexItemMetricsResolver {

	private FlexItemMetricsResolver() {
	}

	/**
	 * 1 itemぶんの入力です。寸法はcontent-box外寸(著者指定そのまま)で
	 * 渡し、正規化はresolverが行う。
	 *
	 * @param preferredMain 主軸のwidth(auto=NaN)
	 * @param minMain min-width(auto=NaN——{@code FlexItemSpec.minWidthAuto})
	 * @param maxMain max-width(なし=+∞)
	 * @param mainFrame 主軸のborder+padding合計
	 * @param outerMargin 主軸のmargin合計
	 * @param borderBox box-sizing:border-boxか
	 * @param scrollable overflowがvisible以外か(§4.5の自動最小除外)
	 * @param minContent 内在最小(内寸)
	 * @param maxContent 内在最大(内寸)
	 * @param containerInnerMain コンテナ主軸内寸(indefinite=NaN)
	 */
	public record Input(int sourceIndex, double grow, double shrink, FlexBasisValue basis, double preferredMain,
			double minMain, double maxMain, double mainFrame, double outerMargin, boolean borderBox,
			boolean scrollable, double minContent, double maxContent, double containerInnerMain) {
	}

	public static FlexItemMetrics resolve(final Input in) {
		final double preferred = inner(in.preferredMain(), in);
		final double max = inner(in.maxMain(), in);
		// flex base size(§9.2.3の初期サブセット)
		final double base;
		final Double basisSize = basisSize(in);
		if (basisSize != null) {
			base = Math.max(0, basisSize);
		} else if (!in.basis().isContent() && !Double.isNaN(preferred)) {
			base = preferred;
		} else {
			base = in.maxContent();
		}
		// 自動最小サイズ(§4.5)
		double min = inner(in.minMain(), in);
		if (Double.isNaN(min)) {
			if (in.scrollable()) {
				min = 0;
			} else {
				min = Double.isNaN(preferred) ? in.minContent() : Math.min(in.minContent(), preferred);
				min = Math.min(min, max);
			}
		}
		final double hypothetical = Math.min(Math.max(base, min), max);
		return new FlexItemMetrics(in.sourceIndex(), base, hypothetical, min, max,
				in.outerMargin() + in.mainFrame(), in.grow(), in.shrink());
	}

	/**
	 * basisの寸法解決(絶対長さ・解決可能な%)。auto/content/未解決%は
	 * null(auto経路へ)。calc等の非絶対量も初期サブセット外として
	 * auto扱い(黙って0にしない)。
	 */
	private static Double basisSize(final Input in) {
		if (in.basis().isAuto() || in.basis().isContent()) {
			return null;
		}
		final QuantityValue size = in.basis().getSize();
		if (size instanceof PercentageValue percent) {
			if (Double.isNaN(in.containerInnerMain())) {
				return null;
			}
			return inner(in.containerInnerMain() * percent.getRatio(), in);
		}
		if (size instanceof AbsoluteLengthValue length) {
			return inner(length.getLength(), in);
		}
		return null;
	}

	/** border-box指定の寸法をcontent-box内寸へ(auto=NaNはそのまま)。 */
	private static double inner(final double size, final Input in) {
		if (Double.isNaN(size) || !in.borderBox()) {
			return size;
		}
		return Math.max(0, size - in.mainFrame());
	}
}
