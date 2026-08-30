package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code mask-composite}(css-masking-1 §7.8)。カンマ区切りの各値を受理・保持する。
 *
 * <p>現在のマスク描画は単一マスクを既存のアルファ/add相当で扱う近似であり、
 * 複数マスク間の{@code subtract}/{@code intersect}/{@code exclude}合成は行わない。
 * 未実装値を別の演算へ近似して見た目を変えないためである。</p>
 */
public final class MaskComposite extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskComposite();

	/** mask-compositeの各キーワード。 */
	public enum CompositeValue implements Value {
		ADD("add"), SUBTRACT("subtract"), INTERSECT("intersect"), EXCLUDE("exclude");

		private final String cssText;

		CompositeValue(String cssText) {
			this.cssText = cssText;
		}

		@Override
		public String toString() {
			return this.cssText;
		}
	}

	/** 多層マスクの値（先頭が最前面）。 */
	public record LayersValue(CompositeValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	private MaskComposite() {
		super("mask-composite");
	}

	/** 全レイヤの値を返す（先頭が最前面）。描画側ではまだ参照しない。 */
	public static CompositeValue[] getLayers(CSSStyle style) {
		final Value value = style.get(INFO);
		return value instanceof LayersValue layers ? layers.layers() : new CompositeValue[] { (CompositeValue) value };
	}

	/** 単層ならキーワード値、多層ならレイヤ値にまとめる。 */
	public static Value toValue(List<CompositeValue> values) {
		return values.size() == 1 ? values.get(0)
				: new LayersValue(values.toArray(new CompositeValue[values.size()]));
	}

	/** キーワードトークンを値へ変換する。 */
	public static CompositeValue fromToken(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "add" -> CompositeValue.ADD;
		case "subtract" -> CompositeValue.SUBTRACT;
		case "intersect" -> CompositeValue.INTERSECT;
		case "exclude" -> CompositeValue.EXCLUDE;
		default -> null;
		};
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return CompositeValue.ADD;
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
		final List<CompositeValue> values = new ArrayList<CompositeValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final CompositeValue value = fromToken(layer.next());
			if (value == null || layer.hasNext()) {
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
