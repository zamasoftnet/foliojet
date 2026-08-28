package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.impl.property.grid.GridAutoFlow;
import net.zamasoft.foliojet.css.impl.property.grid.GridTemplateAreas;
import net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridAutoFlowValue;
import net.zamasoft.foliojet.css.value.GridTemplateAreasValue;
import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid}ショートハンドです(css-grid-1 §7.8、2026-08-29)。
 *
 * <pre>
 * &lt;'grid-template'&gt;
 * | &lt;'grid-template-rows'&gt; / [ auto-flow &amp;&amp; dense? ] &lt;'grid-auto-columns'&gt;?
 * | [ auto-flow &amp;&amp; dense? ] &lt;'grid-auto-rows'&gt;? / &lt;'grid-template-columns'&gt;
 * </pre>
 *
 * <p>
 * 明示トラック(rows/columns/areas)と暗黙トラック(auto-rows/auto-columns/
 * auto-flow)の6 longhandを全て設定する——指定しなかった側は初期値へ戻す
 * (仕様の「gridはgrid-template-*とgrid-auto-*の両方をリセットする」)。
 * {@code auto-flow}がどちら側にあるかで形式を判別する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class GridShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new GridShorthand();

	protected GridShorthand() {
		super("grid");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(GridTemplateTracks.ROWS, global);
			primitives.set(GridTemplateTracks.COLUMNS, global);
			primitives.set(GridTemplateAreas.INFO, global);
			primitives.set(GridTemplateTracks.AUTO_ROWS, global);
			primitives.set(GridTemplateTracks.AUTO_COLUMNS, global);
			primitives.set(GridAutoFlow.INFO, global);
			return;
		}
		final int start = tokens.position();
		final List<List<CssToken>> sides = GridTemplateShorthand.splitSlash(tokens);
		final List<CssToken> before = sides.get(0);
		final List<CssToken> after = sides.size() > 1 ? sides.get(1) : null;
		final boolean flowBefore = hasAutoFlow(before);
		final boolean flowAfter = after != null && hasAutoFlow(after);
		if (!flowBefore && !flowAfter) {
			// <'grid-template'>: 暗黙トラック側は初期値へ
			tokens.rewind(start);
			GridTemplateShorthand.parseTemplate(tokens, ua, uri, primitives);
			primitives.set(GridTemplateTracks.AUTO_ROWS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridTemplateTracks.AUTO_COLUMNS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridAutoFlow.INFO, GridAutoFlowValue.ROW);
			return;
		}
		if (flowBefore == flowAfter || after == null || before.isEmpty() || after.isEmpty()) {
			throw new PropertyException();
		}
		final List<CssToken> flowSide = flowBefore ? before : after;
		final List<CssToken> templateSide = flowBefore ? after : before;
		// [ auto-flow && dense? ] <auto tracks>?
		boolean dense = false, autoFlow = false;
		int consumed = 0;
		while (consumed < flowSide.size() && flowSide.get(consumed) instanceof CssToken.Ident ident
				&& (ident.is("auto-flow") || ident.is("dense"))) {
			if (ident.is("auto-flow") ? autoFlow : dense) {
				throw new PropertyException(); // 重複
			}
			autoFlow |= ident.is("auto-flow");
			dense |= ident.is("dense");
			++consumed;
		}
		if (!autoFlow) {
			throw new PropertyException(); // auto-flowはトラックより前
		}
		final List<CssToken> autoTracks = flowSide.subList(consumed, flowSide.size());
		// (解析結果はcomputed前の中間形なのでValueのまま運ぶ)
		final net.zamasoft.foliojet.css.value.Value implicit = autoTracks.isEmpty()
				? GridTrackListValue.NONE_VALUE
				: ((GridTemplateTracks) GridTemplateTracks.AUTO_ROWS).parseValue(new TokenStream(autoTracks), ua, uri);
		if (flowBefore) {
			// [auto-flow && dense?] <auto-rows>? / <columns>
			primitives.set(GridTemplateTracks.AUTO_ROWS, implicit);
			primitives.set(GridTemplateTracks.AUTO_COLUMNS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridAutoFlow.INFO, GridAutoFlowValue.of(false, dense));
			primitives.set(GridTemplateTracks.ROWS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridTemplateTracks.COLUMNS,
					((GridTemplateTracks) GridTemplateTracks.COLUMNS).parseValue(new TokenStream(templateSide), ua, uri));
		} else {
			// <rows> / [auto-flow && dense?] <auto-columns>?
			primitives.set(GridTemplateTracks.AUTO_COLUMNS, implicit);
			primitives.set(GridTemplateTracks.AUTO_ROWS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridAutoFlow.INFO, GridAutoFlowValue.of(true, dense));
			primitives.set(GridTemplateTracks.COLUMNS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridTemplateTracks.ROWS,
					((GridTemplateTracks) GridTemplateTracks.ROWS).parseValue(new TokenStream(templateSide), ua, uri));
		}
		primitives.set(GridTemplateAreas.INFO, GridTemplateAreasValue.NONE_VALUE);
	}

	private static boolean hasAutoFlow(final List<CssToken> tokens) {
		for (final CssToken token : tokens) {
			if (token instanceof CssToken.Ident ident && ident.is("auto-flow")) {
				return true;
			}
		}
		return false;
	}
}
