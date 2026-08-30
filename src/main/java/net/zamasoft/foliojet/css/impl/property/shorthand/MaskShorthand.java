package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.box.MaskClip;
import net.zamasoft.foliojet.css.impl.property.box.MaskComposite;
import net.zamasoft.foliojet.css.impl.property.box.MaskImage;
import net.zamasoft.foliojet.css.impl.property.box.MaskMode;
import net.zamasoft.foliojet.css.impl.property.box.MaskOrigin;
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
import net.zamasoft.foliojet.css.impl.property.box.MaskClip.ClipValue;
import net.zamasoft.foliojet.css.impl.property.box.MaskComposite.CompositeValue;
import net.zamasoft.foliojet.css.impl.property.box.MaskMode.ModeValue;
import net.zamasoft.foliojet.css.impl.property.box.MaskOrigin.OriginValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code mask}ショートハンド(css-masking-1 §7.11、2026-08-29)。
 *
	 * <p>
	 * 実サイトは{@code -webkit-mask: url(icon.svg) no-repeat center / contain}の
	 * 形でアイコンを型抜きする。画像・repeat・position・{@code / size}に加え、
	 * mode・2つまでの幾何ボックス・clip・compositeを各レイヤのlonghandへ展開する。
	 * 描画側の近似範囲は各longhandのjavadocを参照。
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
				MaskPosition.INFO_Y, MaskSize.INFO_WIDTH, MaskSize.INFO_HEIGHT, MaskOrigin.INFO, MaskClip.INFO,
				MaskMode.INFO, MaskComposite.INFO };
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
		primitives.set(MaskOrigin.INFO, OriginValue.BORDER_BOX);
		primitives.set(MaskClip.INFO, ClipValue.BORDER_BOX);
		primitives.set(MaskMode.INFO, ModeValue.MATCH_SOURCE);
		primitives.set(MaskComposite.INFO, CompositeValue.ADD);

		final List<TokenStream> layers = tokens.splitComma();
		if (layers.isEmpty()) {
			throw new PropertyException();
		}
		final List<OriginValue> origins = new ArrayList<OriginValue>(layers.size());
		final List<ClipValue> clips = new ArrayList<ClipValue>(layers.size());
		final List<ModeValue> modes = new ArrayList<ModeValue>(layers.size());
		final List<CompositeValue> composites = new ArrayList<CompositeValue>(layers.size());
		for (final TokenStream valueLayer : layers) {
			OriginValue origin = OriginValue.BORDER_BOX;
			ClipValue clip = ClipValue.BORDER_BOX;
			ModeValue mode = ModeValue.MATCH_SOURCE;
			CompositeValue composite = CompositeValue.ADD;
			int boxes = 0;
			boolean modeSet = false, compositeSet = false, noClip = false;
			while (valueLayer.hasNext()) {
				final CssToken token = valueLayer.next();
				final OriginValue originValue = MaskOrigin.fromToken(token);
				if (originValue != null) {
					if (boxes >= 2 || noClip && boxes >= 1) {
						throw new PropertyException();
					}
					++boxes;
					if (boxes == 1) {
						origin = originValue;
					}
					if (!noClip) {
						clip = MaskClip.fromToken(token);
					}
					continue;
				}
				final ClipValue clipValue = MaskClip.fromToken(token);
				if (clipValue == ClipValue.NO_CLIP) {
					if (boxes > 1 || noClip) {
						throw new PropertyException();
					}
					clip = clipValue;
					noClip = true;
					continue;
				}
				final ModeValue modeValue = MaskMode.fromToken(token);
				if (modeValue != null) {
					if (modeSet) {
						throw new PropertyException();
					}
					mode = modeValue;
					modeSet = true;
					continue;
				}
				final CompositeValue compositeValue = MaskComposite.fromToken(token);
				if (compositeValue != null) {
					if (compositeSet) {
						throw new PropertyException();
					}
					composite = compositeValue;
					compositeSet = true;
					continue;
				}
			}
			origins.add(origin);
			clips.add(clip);
			modes.add(mode);
			composites.add(composite);
			valueLayer.rewind(0);
		}
		primitives.set(MaskOrigin.INFO, MaskOrigin.toValue(origins));
		primitives.set(MaskClip.INFO, MaskClip.toValue(clips));
		primitives.set(MaskMode.INFO, MaskMode.toValue(modes));
		primitives.set(MaskComposite.INFO, MaskComposite.toValue(composites));
		// 複数レイヤーは最初のレイヤーだけを使う(MaskImageと同じ近似)
		final TokenStream layer = layers.get(0);
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
