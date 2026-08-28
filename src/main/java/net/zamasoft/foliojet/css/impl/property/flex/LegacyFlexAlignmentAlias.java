package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 2011年版Flexbox({@code -ms-flex-pack}等)の整列プロパティを標準名へ
 * 写す別名です(2026-08-29新設、実サイト50件中18箇所)。
 *
 * <p>
 * 値の語彙が違う({@code start}/{@code end}/{@code justify}/
 * {@code distribute})ので単純な{@code alias()}では受けられない。
 * {@code start}→{@code flex-start}、{@code end}→{@code flex-end}、
 * {@code justify}→{@code space-between}、{@code distribute}→
 * {@code space-around}に読み替えてから標準プロパティの解析へ委ねる
 * (longhand1つのショートハンドとして実装。全体キーワードも通る)。
 * {@code center}/{@code stretch}/{@code baseline}/{@code auto}は
 * そのまま通る。
 * </p>
 *
 * <p>
 * 2009年版の{@code -webkit-box-pack}/{@code -webkit-box-align}/
 * {@code -webkit-box-orient}は対象外——{@code display: -webkit-box}を
 * blockへ写している(flex容器にならない)ので、整列だけ写しても意味が
 * なく、無視リスト({@code PropertySet.IGNORED_PROPERTIES}の
 * {@code box-pack}等)に残す。
 * </p>
 */
public final class LegacyFlexAlignmentAlias extends AbstractShorthandPropertyInfo {
	public static final LegacyFlexAlignmentAlias FLEX_PACK = new LegacyFlexAlignmentAlias("-ms-flex-pack",
			GridAlignmentProperty.JUSTIFY_CONTENT);

	public static final LegacyFlexAlignmentAlias FLEX_ALIGN = new LegacyFlexAlignmentAlias("-ms-flex-align",
			GridAlignmentProperty.ALIGN_ITEMS);

	public static final LegacyFlexAlignmentAlias FLEX_ITEM_ALIGN = new LegacyFlexAlignmentAlias(
			"-ms-flex-item-align", GridAlignmentProperty.ALIGN_SELF);

	public static final LegacyFlexAlignmentAlias FLEX_LINE_PACK = new LegacyFlexAlignmentAlias(
			"-ms-flex-line-pack", GridAlignmentProperty.ALIGN_CONTENT);

	public static List<LegacyFlexAlignmentAlias> all() {
		return List.of(FLEX_PACK, FLEX_ALIGN, FLEX_ITEM_ALIGN, FLEX_LINE_PACK);
	}

	private final GridAlignmentProperty target;

	private LegacyFlexAlignmentAlias(final String name, final GridAlignmentProperty target) {
		super(name);
		this.target = target;
	}

	public GridAlignmentProperty getTarget() {
		return this.target;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { this.target };
	}

	/** 2011年版の語彙を標準の語彙へ。該当しなければそのまま。 */
	static String translate(final String keyword) {
		switch (keyword) {
		case "start":
			return "flex-start";
		case "end":
			return "flex-end";
		case "justify":
			return "space-between";
		case "distribute":
			return "space-around";
		default:
			return keyword;
		}
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri,
			final Primitives primitives) throws PropertyException {
		final CssToken token = tokens.next();
		if (!(token instanceof CssToken.Ident ident) || tokens.hasNext()) {
			throw new PropertyException();
		}
		final TokenStream translated = new TokenStream(List.of(new CssToken.Ident(translate(ident.lower()))));
		final BoxAlignmentValue value = this.target.eatValue(translated);
		if (value == null || translated.hasNext()) {
			throw new PropertyException();
		}
		primitives.set(this.target, value);
	}
}
