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
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code mask-clip}(css-masking-1 §7.5)。カンマ区切りの各マスクレイヤの
 * 切り抜き基準を保持する。
 *
 * <p>{@code padding-box}/{@code content-box}はURLマスクの描画へ反映する。
 * 初期値の{@code border-box}は従来の出力経路を保つ。SVG固有の3値と
 * {@code no-clip}は値を受理・保持するが、現在のHTMLボックス用マスク描画には
 * 反映しない。</p>
 */
public final class MaskClip extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskClip();

	/** mask-clipの各キーワード。 */
	public enum ClipValue implements Value {
		BORDER_BOX("border-box", Background.BORDER_BOX),
		PADDING_BOX("padding-box", Background.PADDING_BOX),
		CONTENT_BOX("content-box", Background.CONTENT_BOX),
		FILL_BOX("fill-box", -1),
		STROKE_BOX("stroke-box", -1),
		VIEW_BOX("view-box", -1),
		NO_CLIP("no-clip", -1);

		private final String cssText;
		private final byte backgroundClip;

		ClipValue(String cssText, int backgroundClip) {
			this.cssText = cssText;
			this.backgroundClip = (byte) backgroundClip;
		}

		/** Backgroundの切り抜き基準へ変換可能ならtrue。 */
		public boolean isPaintSupported() {
			return this.backgroundClip >= 0;
		}

		/** Backgroundの切り抜き基準。{@link #isPaintSupported()}がfalseなら使えない。 */
		public byte getBackgroundClip() {
			return this.backgroundClip;
		}

		@Override
		public String toString() {
			return this.cssText;
		}
	}

	/** 多層マスクの値（先頭が最前面）。 */
	public record LayersValue(ClipValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	private MaskClip() {
		super("mask-clip");
	}

	/** 全レイヤの値を返す（先頭が最前面）。 */
	public static ClipValue[] getLayers(CSSStyle style) {
		final Value value = style.get(INFO);
		return value instanceof LayersValue layers ? layers.layers() : new ClipValue[] { (ClipValue) value };
	}

	/** 現在描画対象にしている先頭レイヤの値を返す。 */
	public static ClipValue get(CSSStyle style) {
		return getLayers(style)[0];
	}

	/** 単層ならキーワード値、多層ならレイヤ値にまとめる。 */
	public static Value toValue(List<ClipValue> values) {
		return values.size() == 1 ? values.get(0)
				: new LayersValue(values.toArray(new ClipValue[values.size()]));
	}

	/** キーワードトークンを値へ変換する。 */
	public static ClipValue fromToken(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "border-box" -> ClipValue.BORDER_BOX;
		case "padding-box" -> ClipValue.PADDING_BOX;
		case "content-box" -> ClipValue.CONTENT_BOX;
		case "fill-box" -> ClipValue.FILL_BOX;
		case "stroke-box" -> ClipValue.STROKE_BOX;
		case "view-box" -> ClipValue.VIEW_BOX;
		case "no-clip" -> ClipValue.NO_CLIP;
		default -> null;
		};
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return ClipValue.BORDER_BOX;
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
		final List<ClipValue> values = new ArrayList<ClipValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final ClipValue value = fromToken(layer.next());
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
