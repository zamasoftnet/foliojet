package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.property.grid.GridTemplateAreas;
import net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridTemplateAreasValue;
import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid-template}ショートハンドです(css-grid-1 §7.4、2026-08-29)。
 *
 * <pre>
 * none
 * | &lt;'grid-template-rows'&gt; / &lt;'grid-template-columns'&gt;
 * | [ &lt;line-names&gt;? &lt;string&gt; &lt;track-size&gt;? &lt;line-names&gt;? ]+ [ / &lt;explicit-track-list&gt; ]?
 * </pre>
 *
 * <p>
 * 3形式目(areas+rows)は各行の文字列を{@code grid-template-areas}へ、
 * 文字列の前後の線名と行寸法を{@code grid-template-rows}へ振り分ける
 * (寸法省略は{@code auto}、隣接する線名は同じ線に集まる)。省略した
 * longhandは初期値({@code none})へ戻す。{@code grid}ショートハンドが
 * {@link #parseTemplate}を共用する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class GridTemplateShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new GridTemplateShorthand();

	protected GridTemplateShorthand() {
		super("grid-template");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(GridTemplateTracks.ROWS, global);
			primitives.set(GridTemplateTracks.COLUMNS, global);
			primitives.set(GridTemplateAreas.INFO, global);
			return;
		}
		parseTemplate(tokens, ua, uri, primitives);
	}

	/**
	 * {@code grid-template}の値を解析してrows/columns/areasを設定します
	 * ({@code grid}ショートハンドと共用)。
	 */
	static void parseTemplate(final TokenStream tokens, final UserAgent ua, final URI uri,
			final Primitives primitives) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("none")) {
			primitives.set(GridTemplateTracks.ROWS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridTemplateTracks.COLUMNS, GridTrackListValue.NONE_VALUE);
			primitives.set(GridTemplateAreas.INFO, GridTemplateAreasValue.NONE_VALUE);
			return;
		}
		final List<List<CssToken>> sides = splitSlash(tokens);
		final List<CssToken> before = sides.get(0);
		final List<CssToken> after = sides.size() > 1 ? sides.get(1) : null;
		if (before.isEmpty() || (after != null && after.isEmpty())) {
			throw new PropertyException();
		}
		boolean hasString = false;
		for (final CssToken token : before) {
			hasString |= token instanceof CssToken.Str;
		}
		if (!hasString) {
			// <rows> / <columns>
			if (after == null) {
				throw new PropertyException();
			}
			primitives.set(GridTemplateTracks.ROWS,
					((GridTemplateTracks) GridTemplateTracks.ROWS).parseValue(new TokenStream(before), ua, uri));
			primitives.set(GridTemplateTracks.COLUMNS,
					((GridTemplateTracks) GridTemplateTracks.COLUMNS).parseValue(new TokenStream(after), ua, uri));
			primitives.set(GridTemplateAreas.INFO, GridTemplateAreasValue.NONE_VALUE);
			return;
		}
		// [ <line-names>? <string> <track-size>? <line-names>? ]+ [ / <explicit-track-list> ]?
		final List<CssToken> rowTokens = new ArrayList<>();
		final List<CssToken> areaTokens = new ArrayList<>();
		boolean pendingSize = false; // 直前の文字列にまだ行寸法が付いていない
		for (final CssToken token : before) {
			if (token instanceof CssToken.LineNames) {
				if (pendingSize) {
					// 寸法省略の行の後ろの線名: autoを補ってから線へ
					rowTokens.add(new CssToken.Ident("auto"));
					pendingSize = false;
				}
				rowTokens.add(token);
			} else if (token instanceof CssToken.Str) {
				if (pendingSize) {
					rowTokens.add(new CssToken.Ident("auto"));
				}
				areaTokens.add(token);
				pendingSize = true;
			} else {
				if (!pendingSize || token instanceof CssToken.Func func && func.is("repeat")) {
					throw new PropertyException();
				}
				rowTokens.add(token);
				pendingSize = false;
			}
		}
		if (pendingSize) {
			rowTokens.add(new CssToken.Ident("auto"));
		}
		primitives.set(GridTemplateAreas.INFO,
				((GridTemplateAreas) GridTemplateAreas.INFO).parseValue(new TokenStream(areaTokens), ua, uri));
		primitives.set(GridTemplateTracks.ROWS,
				((GridTemplateTracks) GridTemplateTracks.ROWS).parseValue(new TokenStream(rowTokens), ua, uri));
		if (after == null) {
			primitives.set(GridTemplateTracks.COLUMNS, GridTrackListValue.NONE_VALUE);
		} else {
			for (final CssToken token : after) {
				if (token instanceof CssToken.Str) {
					throw new PropertyException();
				}
			}
			primitives.set(GridTemplateTracks.COLUMNS,
					((GridTemplateTracks) GridTemplateTracks.COLUMNS).parseValue(new TokenStream(after), ua, uri));
		}
	}

	/** トップレベルの{@code /}で2つに分けます(2つ以上あれば無効)。 */
	static List<List<CssToken>> splitSlash(final TokenStream tokens) throws PropertyException {
		final List<List<CssToken>> sides = new ArrayList<>(2);
		List<CssToken> current = new ArrayList<>();
		sides.add(current);
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token == CssToken.Op.SLASH) {
				if (sides.size() == 2) {
					throw new PropertyException();
				}
				current = new ArrayList<>();
				sides.add(current);
			} else {
				current.add(token);
			}
		}
		return sides;
	}
}
