package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.box.MaskImage;
import net.zamasoft.foliojet.css.impl.property.box.MaskPosition;
import net.zamasoft.foliojet.css.impl.property.box.MaskRepeat;
import net.zamasoft.foliojet.css.impl.property.box.MaskSize;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code mask}ショートハンド(css-masking-1 §7.11、2026-08-29)。
 *
 * <p>
 * 実サイトは{@code -webkit-mask: url(icon.svg) no-repeat center / contain}の
 * 形でアイコンを型抜きする。対応するのは画像・repeat・position・
 * {@code / size}で、{@code mask-mode}({@code alpha}等)・幾何ボックス・
 * {@code mask-composite}のキーワードは読み飛ばす(それらを落とす害より、
 * 宣言全体を捨てて背景色の四角だけが残る害のほうが大きい)。
 * </p>
 */
public class MaskShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new MaskShorthand();

	private static final Set<String> IGNORED_KEYWORDS = Set.of("alpha", "luminance", "match-source", "border-box",
			"padding-box", "content-box", "fill-box", "stroke-box", "view-box", "no-clip", "add", "subtract",
			"intersect", "exclude");

	protected MaskShorthand() {
		super("mask");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { MaskImage.INFO, MaskRepeat.INFO, MaskPosition.INFO_X,
				MaskPosition.INFO_Y, MaskSize.INFO_WIDTH, MaskSize.INFO_HEIGHT };
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		primitives.set(MaskImage.INFO, KeywordValue.NONE);
		primitives.set(MaskRepeat.INFO, BackgroundRepeatValue.REPEAT_VALUE);
		primitives.set(MaskPosition.INFO_X, PercentageValue.ZERO);
		primitives.set(MaskPosition.INFO_Y, PercentageValue.ZERO);
		primitives.set(MaskSize.INFO_WIDTH, KeywordValue.AUTO);
		primitives.set(MaskSize.INFO_HEIGHT, KeywordValue.AUTO);
		// 複数レイヤーは最初のレイヤーだけを使う(MaskImageと同じ近似)
		final TokenStream layer = tokens.splitComma().get(0);
		boolean image = false, position = false;
		while (layer.hasNext()) {
			final CssToken lu = layer.next();
			if (lu instanceof CssToken.Ident ident && IGNORED_KEYWORDS.contains(ident.lower())) {
				continue;
			}
			if (!image && (ValueUtils.isNone(lu) || lu instanceof CssToken.Uri || lu instanceof CssToken.Func)) {
				final List<CssToken> one = new ArrayList<>();
				one.add(lu);
				final Value value = ((MaskImage) MaskImage.INFO).parseValue(new TokenStream(one), ua, uri);
				primitives.set(MaskImage.INFO, value);
				image = true;
				continue;
			}
			final Value repeat = ColorValueUtils.toBackgroundRepeat(lu);
			if (repeat != null) {
				primitives.set(MaskRepeat.INFO, repeat);
				continue;
			}
			if (lu == CssToken.Op.SLASH) {
				// サイズはlonghandのパーサに任せる(cover/contain/2値)
				final List<CssToken> rest = new ArrayList<>();
				while (layer.hasNext() && BackgroundShorthand.isPositionToken(ua, layer.peek())
						|| layer.hasNext() && layer.peek() instanceof CssToken.Ident sizeKw
								&& ("cover".equals(sizeKw.lower()) || "contain".equals(sizeKw.lower()))) {
					rest.add(layer.next());
				}
				if (rest.isEmpty()) {
					throw new PropertyException();
				}
				for (final Entry entry : ((MaskSize) MaskSize.INFO_WIDTH).parseSizeValues(new TokenStream(rest), ua,
						uri)) {
					primitives.set(entry.getPrimitivePropertyInfo(), entry.getValue());
				}
				continue;
			}
			if (!position && BackgroundShorthand.isPositionToken(ua, lu)) {
				final List<CssToken> pos = new ArrayList<>();
				pos.add(lu);
				while (pos.size() < 4 && layer.hasNext() && BackgroundShorthand.isPositionToken(ua, layer.peek())) {
					pos.add(layer.next());
				}
				for (final Entry entry : ((BackgroundPosition) MaskPosition.INFO_X)
						.parsePositionValues(new TokenStream(pos), ua, uri)) {
					primitives.set(entry.getPrimitivePropertyInfo(), entry.getValue());
				}
				position = true;
				continue;
			}
			throw new PropertyException();
		}
	}
}
