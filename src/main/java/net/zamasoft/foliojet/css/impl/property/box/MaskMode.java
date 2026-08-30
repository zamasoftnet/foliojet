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
 * {@code mask-mode}(css-masking-1 §7.10)。カンマ区切りの各値を受理・保持する。
 *
 * <p>現在のマスク描画は既存のアルファ相当の近似経路だけを持つため、
 * {@code alpha}/{@code luminance}/{@code match-source}による描画の切替は
 * 行わない。未実装値を別のモードへ近似して見た目を変えないためである。</p>
 */
public final class MaskMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskMode();

	/** mask-modeの各キーワード。 */
	public enum ModeValue implements Value {
		ALPHA("alpha"), LUMINANCE("luminance"), MATCH_SOURCE("match-source");

		private final String cssText;

		ModeValue(String cssText) {
			this.cssText = cssText;
		}

		@Override
		public String toString() {
			return this.cssText;
		}
	}

	/** 多層マスクの値（先頭が最前面）。 */
	public record LayersValue(ModeValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	private MaskMode() {
		super("mask-mode");
	}

	/** 全レイヤの値を返す（先頭が最前面）。描画側ではまだ参照しない。 */
	public static ModeValue[] getLayers(CSSStyle style) {
		final Value value = style.get(INFO);
		return value instanceof LayersValue layers ? layers.layers() : new ModeValue[] { (ModeValue) value };
	}

	/** 単層ならキーワード値、多層ならレイヤ値にまとめる。 */
	public static Value toValue(List<ModeValue> values) {
		return values.size() == 1 ? values.get(0)
				: new LayersValue(values.toArray(new ModeValue[values.size()]));
	}

	/** キーワードトークンを値へ変換する。 */
	public static ModeValue fromToken(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "alpha" -> ModeValue.ALPHA;
		case "luminance" -> ModeValue.LUMINANCE;
		case "match-source" -> ModeValue.MATCH_SOURCE;
		default -> null;
		};
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return ModeValue.MATCH_SOURCE;
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
		final List<ModeValue> values = new ArrayList<ModeValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final ModeValue value = fromToken(layer.next());
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
