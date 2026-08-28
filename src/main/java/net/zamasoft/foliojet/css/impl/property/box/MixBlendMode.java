package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;

/**
 * {@code mix-blend-mode}です(compositing-1 §4、2026-08-29新設)。
 *
 * <p>
 * 非継承・既定{@code normal}。16種のブレンドモードを受け、描画時に
 * pdfg2dの{@code GC.setBlendMode}(PDFではExtGStateの{@code /BM})へ
 * 流す。
 * </p>
 *
 * <p>
 * <b>近似</b>: 仕様では要素全体を1つのグループとして背景と合成するが、
 * 本実装はopacityと同じ流儀で、要素と子孫の各描画要素(背景・境界・
 * テキスト・画像)ごとにモードを適用する。子孫へ届けるためcomputed
 * valueは「自身がnormalなら親の値」とする(opacityが親の値を掛け込む
 * のと同型)。要素内で重なる描画同士も背景と同じモードで合成される
 * 点が仕様と異なる(不透明な単色背景+文字の典型例では差は出ない)。
 * {@code isolation}は受理するだけで効果はない。
 * </p>
 */
public class MixBlendMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MixBlendMode();

	/** ブレンドモードの値。 */
	public record BlendModeValue(BlendMode mode) implements Value {
		@Override
		public String toString() {
			return this.mode.cssName;
		}
	}

	private static final BlendModeValue NORMAL = new BlendModeValue(BlendMode.NORMAL);

	public static BlendMode get(final CSSStyle style) {
		return ((BlendModeValue) style.get(INFO)).mode();
	}

	protected MixBlendMode() {
		super("mix-blend-mode");
	}

	public Value getDefault(final CSSStyle style) {
		return NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		final CSSStyle parent = style.getParentStyle();
		if (parent == null || ((BlendModeValue) value).mode() != BlendMode.NORMAL) {
			return value;
		}
		// 子孫の描画要素へ親のモードを届ける(クラス冒頭の近似の説明参照)
		return parent.get(INFO);
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			final BlendMode mode = BlendMode.fromCssName(ident.lower());
			if (mode != null) {
				return mode == BlendMode.NORMAL ? NORMAL : new BlendModeValue(mode);
			}
		}
		throw new PropertyException();
	}
}
