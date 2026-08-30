package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageOutset;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageRepeat;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageSlice;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageSource;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageWidth;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderImageValueUtils;
import net.zamasoft.foliojet.css.value.BorderImageOutsetValue;
import net.zamasoft.foliojet.css.value.BorderImageRepeatValue;
import net.zamasoft.foliojet.css.value.BorderImageRepeatValue.Mode;
import net.zamasoft.foliojet.css.value.BorderImageSliceValue;
import net.zamasoft.foliojet.css.value.BorderImageWidthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** {@code border-image} ショートハンドです。 */
public final class BorderImageShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderImageShorthand();

	private BorderImageShorthand() {
		super("border-image");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { BorderImageSource.INFO, BorderImageSlice.INFO, BorderImageWidth.INFO,
				BorderImageOutset.INFO, BorderImageRepeat.INFO };
	}

	@Override
	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final List<List<CssToken>> groups = splitSlash(tokens);
		if (groups.size() > 3) {
			throw new PropertyException("/が3度以上指定されています");
		}

		Value source = null;
		final List<Mode> repeats = new ArrayList<Mode>(2);
		for (int groupIndex = 0; groupIndex < groups.size(); ++groupIndex) {
			final List<CssToken> input = groups.get(groupIndex);
			final List<CssToken> numeric = new ArrayList<CssToken>(input.size());
			boolean trailingComponent = false;
			for (final CssToken token : input) {
				final Mode repeat = BorderImageValueUtils.repeat(token);
				if (repeat != null) {
					if (groupIndex > 0 && (groupIndex != groups.size() - 1 || numeric.isEmpty())) {
						throw new PropertyException("repeatがslash成分の途中にあります");
					}
					if (repeats.size() == 2) {
						throw new PropertyException("repeatが3度指定されています");
					}
					repeats.add(repeat);
					trailingComponent = groupIndex > 0;
					continue;
				}
				final Value image = BackgroundImage.parseLayer(ua, uri, token);
				if (image != null) {
					if (groupIndex > 0 && (groupIndex != groups.size() - 1 || numeric.isEmpty())) {
						throw new PropertyException("sourceがslash成分の途中にあります");
					}
					if (source != null) {
						throw new PropertyException("sourceが2度指定されています");
					}
					source = image;
					trailingComponent = groupIndex > 0;
					continue;
				}
				if (trailingComponent) {
					throw new PropertyException("slash成分の後に値があります");
				}
				numeric.add(token);
			}
			groups.set(groupIndex, numeric);
		}
		if (source == null && repeats.isEmpty() && groups.size() == 1 && groups.get(0).isEmpty()) {
			throw new PropertyException();
		}

		// slashを使う構文はsliceに従属するため、slash前のslice値が必須。
		if (groups.size() > 1 && groups.get(0).isEmpty()) {
			throw new PropertyException("/の前にsliceがありません");
		}

		BorderImageSliceValue slice = BorderImageSliceValue.DEFAULT;
		if (!groups.get(0).isEmpty()) {
			slice = BorderImageValueUtils.parseSlice(new TokenStream(groups.get(0)), ua);
		}

		BorderImageWidthValue width = BorderImageWidthValue.DEFAULT;
		if (groups.size() == 2) {
			if (groups.get(1).isEmpty()) {
				throw new PropertyException("widthがありません");
			}
			width = BorderImageValueUtils.parseWidth(new TokenStream(groups.get(1)), ua);
		} else if (groups.size() == 3 && !groups.get(1).isEmpty()) {
			width = BorderImageValueUtils.parseWidth(new TokenStream(groups.get(1)), ua);
		}

		BorderImageOutsetValue outset = BorderImageOutsetValue.DEFAULT;
		if (groups.size() == 3) {
			if (groups.get(2).isEmpty()) {
				throw new PropertyException("outsetがありません");
			}
			outset = BorderImageValueUtils.parseOutset(new TokenStream(groups.get(2)), ua);
		}

		final BorderImageRepeatValue repeat;
		if (repeats.isEmpty()) {
			repeat = BorderImageRepeatValue.STRETCH;
		} else {
			final Mode horizontal = repeats.get(0);
			final Mode vertical = repeats.size() == 1 ? horizontal : repeats.get(1);
			repeat = horizontal == Mode.STRETCH && vertical == Mode.STRETCH ? BorderImageRepeatValue.STRETCH
					: new BorderImageRepeatValue(horizontal, vertical);
		}

		primitives.set(BorderImageSource.INFO, source == null ? KeywordValue.NONE : source);
		primitives.set(BorderImageSlice.INFO, slice);
		primitives.set(BorderImageWidth.INFO, width);
		primitives.set(BorderImageOutset.INFO, outset);
		primitives.set(BorderImageRepeat.INFO, repeat);
	}

	private static List<List<CssToken>> splitSlash(TokenStream tokens) {
		final List<List<CssToken>> groups = new ArrayList<List<CssToken>>(3);
		groups.add(new ArrayList<CssToken>());
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token == CssToken.Op.SLASH) {
				groups.add(new ArrayList<CssToken>());
			} else {
				groups.get(groups.size() - 1).add(token);
			}
		}
		return groups;
	}
}
