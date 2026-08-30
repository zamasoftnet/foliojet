package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue;
import net.zamasoft.foliojet.ua.UserAgent;

/** background-origin。 */
public class BackgroundOrigin extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundOrigin();

	/** 多層背景の各レイヤの origin（先頭が最前面）。 */
	public record LayersValue(BackgroundOriginValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	/**
	 * 各レイヤの origin を返します。指定数が画像数より少ない場合は描画側で
	 * CSS のリスト規則に従って循環させます。
	 */
	public static byte[] get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof LayersValue layers) {
			final BackgroundOriginValue[] values = layers.layers();
			final byte[] origins = new byte[values.length];
			for (int i = 0; i < values.length; ++i) {
				origins[i] = values[i].getBackgroundOrigin();
			}
			return origins;
		}
		return new byte[] { ((BackgroundOriginValue) value).getBackgroundOrigin() };
	}

	/** 単層ならその値、多層ならレイヤ値にまとめます。 */
	public static Value toValue(List<BackgroundOriginValue> values) {
		if (values.size() == 1) {
			return values.get(0);
		}
		return new LayersValue(values.toArray(new BackgroundOriginValue[values.size()]));
	}

	protected BackgroundOrigin() {
		super("background-origin");
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return BackgroundOriginValue.PADDING_BOX_VALUE;
	}

	@Override
	public boolean isInherited() {
		return false;
	}

	@Override
	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final List<BackgroundOriginValue> values = new ArrayList<BackgroundOriginValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final CssToken token = layer.next();
			if (token == null || layer.hasNext()) {
				throw new PropertyException();
			}
			final BackgroundOriginValue value = ColorValueUtils.toBackgroundOrigin(token);
			if (value == null) {
				throw new PropertyException();
			}
			values.add(value);
		}
		if (values.isEmpty()) {
			throw new PropertyException();
		}
		return toValue(values);
	}
}
