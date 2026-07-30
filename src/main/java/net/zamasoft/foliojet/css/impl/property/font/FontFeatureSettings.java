package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontFeatureSettingsValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * {@code font-feature-settings}(css-fonts-3)です。
 * {@code normal | <feature-tag-value>#}——各エントリは引用符つきの4文字タグと
 * 任意の値({@code on}=1、{@code off}=0、非負整数。省略は1)。
 * 重複タグは後勝ちで、{@link FontFeatureSet}の正規形へ畳みます。
 *
 * @author MIYABE Tatsuhiko
 */
public class FontFeatureSettings extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontFeatureSettings();

	public static FontFeatureSet get(CSSStyle style) {
		return ((FontFeatureSettingsValue) style.get(INFO)).getFeatures();
	}

	protected FontFeatureSettings() {
		super("font-feature-settings");
	}

	public Value getDefault(CSSStyle style) {
		return FontFeatureSettingsValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("normal")) {
			return FontFeatureSettingsValue.NORMAL_VALUE;
		}
		final List<TokenStream> groups = tokens.splitComma();
		if (groups.isEmpty()) {
			throw new PropertyException();
		}
		final int[] tags = new int[groups.size()];
		final int[] values = new int[groups.size()];
		for (int i = 0; i < groups.size(); ++i) {
			final TokenStream group = groups.get(i);
			final String tag = group.string();
			if (tag == null) {
				throw new PropertyException();
			}
			try {
				tags[i] = FontFeatureSet.packTag(tag);
			} catch (IllegalArgumentException e) {
				throw new PropertyException();
			}
			int value = 1;
			if (group.hasNext()) {
				if (group.eat("on")) {
					value = 1;
				} else if (group.eat("off")) {
					value = 0;
				} else {
					final CssToken.Num num = group.number();
					if (num == null || !num.integer() || num.value() < 0) {
						throw new PropertyException();
					}
					value = num.intValue();
				}
			}
			if (group.hasNext()) {
				throw new PropertyException();
			}
			values[i] = value;
		}
		return FontFeatureSettingsValue.create(FontFeatureSet.of(tags, values));
	}
}
