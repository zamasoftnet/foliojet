package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code zoom}です(css-viewport-1 §4、2026-08-29新設)。
 *
 * <p>
 * {@code <number [0,∞]> | <percentage [0,∞]> | normal | reset}。非継承、
 * 既定1。0は仕様どおり1と同じ(旧IEは非表示だったが、現行仕様は
 * {@code 0}=1)。
 * </p>
 *
 * <p>
 * <b>近似</b>: 仕様の{@code zoom}はレイアウトに効く(要素と子孫の
 * 計算値の長さが全て倍率で掛かり、周囲の配置も押し広げる)が、本実装は
 * 描画時の拡大——要素の<b>境界箱の左上</b>を原点に、要素とその子孫の
 * 描画を倍率で拡大する({@code AbstractBox.transform}。作者の
 * {@code transform}の外側、{@code transform-origin}は関与しない)。
 * 周囲のレイアウトは変わらないので、拡大した分は隣接内容に重なる。
 * 実サイトでは{@code zoom:1}(IEのhasLayoutトリガ)がほとんどで、これは
 * 恒等なので無害。
 * </p>
 */
public class Zoom extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Zoom();

	public static double get(final CSSStyle style) {
		return ((RealValue) style.get(INFO)).getReal();
	}

	protected Zoom() {
		super("zoom");
	}

	public Value getDefault(final CSSStyle style) {
		return RealValue.ONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		double zoom;
		if (token instanceof CssToken.Num num) {
			zoom = num.value();
		} else if (token instanceof CssToken.Percent percent) {
			zoom = percent.value() / 100.0;
		} else if (token instanceof CssToken.Ident ident && (ident.is("normal") || ident.is("reset"))) {
			// reset(旧WebKit: 祖先のzoomを打ち消す)は祖先を辿らないので1
			return RealValue.ONE;
		} else {
			throw new PropertyException();
		}
		if (zoom < 0) {
			throw new PropertyException();
		}
		if (zoom == 0) {
			zoom = 1;
		}
		return RealValue.create(zoom);
	}
}
