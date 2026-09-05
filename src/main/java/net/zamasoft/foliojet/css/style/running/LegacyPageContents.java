package net.zamasoft.foliojet.css.style.running;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.PageRule;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.ua.PageAssignmentState;
import net.zamasoft.foliojet.ua.PageAssignmentState.Assignment;
import net.zamasoft.foliojet.ua.UserAgent;

/** 頁内の最後の代入を選び、fixedと同じ版面基準の座標へ再生します。 */
public final class LegacyPageContents {
	private LegacyPageContents() {
	}

	/** 複数の面指定は和です。singleは片面impositionの実際の頁種別を使います。 */
	public static boolean matches(final byte mask, final CSSElement page) {
		return mask == 0
				|| (mask & PageRule.PSEUDO_FIRST) != 0 && page.isPseudoClass(CSSElement.PC_FIRST)
				|| (mask & PageRule.PSEUDO_LEFT) != 0 && page.isPseudoClass(CSSElement.PC_LEFT)
				|| (mask & PageRule.PSEUDO_RIGHT) != 0 && page.isPseudoClass(CSSElement.PC_RIGHT)
				|| (mask & PageRule.PSEUDO_SINGLE) != 0
						&& (page == CSSElement.PAGE_SINGLE || page == CSSElement.PAGE_SINGLE_FIRST);
	}

	/** 白紙判定でも使えるよう、未commitの配置を値だけで重ねます。共有状態は変更しません。 */
	public static List<RunningTemplate> active(final PageAssignmentState<RunningTemplate> state,
			final CSSElement page, final List<RunningRegistry.Placement> placements) {
		final var winners = new HashMap<String, Assignment<RunningTemplate>>();
		for (final String name : state.names()) {
			final var snapshot = state.snapshot(name);
			winners.put(name, snapshot.last() == null ? snapshot.entry() : snapshot.last());
		}
		for (final var placement : placements) {
			for (final String name : placement.clears()) {
				put(winners, name, new Assignment<RunningTemplate>(placement.order(), null, false, true));
			}
			if (placement.template() != null) {
				put(winners, placement.template().name(), new Assignment<RunningTemplate>(
						placement.order(), placement.template(), placement.beginsPage(), false));
			}
		}
		final var selected = new ArrayList<Assignment<RunningTemplate>>();
		for (final var value : winners.values()) {
			if (value != null && !value.tombstone() && value.value().legacy() && matches(value.value().pages(), page)) {
				selected.add(value);
			}
		}
		// 異名同士は最終markerの文書順で重ねる(同じz-indexでは後ほど手前)。3.2のHashMap列挙順は再現しない。
		selected.sort(Comparator.comparingLong(Assignment::order));
		return selected.stream().map(Assignment::value).toList();
	}

	private static void put(final HashMap<String, Assignment<RunningTemplate>> winners, final String name,
			final Assignment<RunningTemplate> value) {
		final var previous = winners.get(name);
		if (previous == null || previous.order() <= value.order()) {
			winners.put(name, value);
		}
	}

	/** 新品の頁を包含ブロックとするため、fixed登録もこの表示頁の寿命で終わります。 */
	public static void layout(final UserAgent ua, final CSSElement page, final PageBox target,
			final RunningRenderer renderer) {
		layout(ua, target, renderer, active(ua.getPassContext().getRunningState(), page, List.of()))
				.forEach(target::addPageContent);
	}

	/** fixed登録の有無ではなく、隔離した表示リストに描画内容があるかを調べます。 */
	public static boolean paintsAnything(final UserAgent ua, final CSSElement page, final String pageName,
			final PageBox target, final List<RunningRegistry.Placement> placements) {
		final var templates = active(ua.getPassContext().getRunningState(), page, placements);
		if (templates.isEmpty()) {
			return false;
		}
		final var renderer = new RunningRenderer(ua, new PageValueSnapshot(ua, page, pageName));
		final Drawer drawer = new Drawer(0);
		for (final PageBox mini : layout(ua, target, renderer, templates)) {
			RunningRenderer.draw(mini, drawer, 0, 0);
		}
		return drawer.hasPaintCommands();
	}

	private static List<PageBox> layout(final UserAgent ua, final PageBox target, final RunningRenderer renderer,
			final List<RunningTemplate> templates) {
		final var contents = new ArrayList<PageBox>();
		final CSSStyle container = CSSStyle.getCSSStyle(ua, null, CSSElement.ANON);
		final BlockParams source = target.getBlockParams();
		final BlockParams params = new BlockParams();
		params.fontStyle = source.fontStyle;
		params.fontManager = source.fontManager;
		params.lineBreakRules = source.lineBreakRules;
		params.flow = source.flow;
		params.writingModeVariant = source.writingModeVariant;
		params.direction = source.direction;
		// AbsoluteBlockBox.finishLayoutSelfと同じpadding辺を割合寸法の基準にする。
		final double width = target.getInnerWidth() + target.getFrame().padding.getFrameWidth();
		final double height = target.getInnerHeight() + target.getFrame().padding.getFrameHeight();
		params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		for (final RunningTemplate template : templates) {
			final RunningRenderer.Content content = renderer.prepare(template, container, true);
			if (content != null) {
				final PageBox mini = content.layout(params, width, height);
				if (mini != null) {
					contents.add(mini);
				}
			}
		}
		return contents;
	}
}
