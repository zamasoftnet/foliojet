package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.CalcValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/colors.html#propdef-background-position">
 * backgropund-position 特性 </a>です。
 * <p>
 * 2026-07-20: {@code -cssj-direction-mode}廃止に伴い、縦書き時のx/y軸
 * 入れ替え(実世界のCSS/ブラウザには存在しない挙動)を削除した。
 * background-positionは常に物理座標のまま扱う。
 * </p>
 * <p>
 * 2026-08-27: &lt;position&gt;文法を全面書き直し。object-positionとの共有
 * (派生は{@code getPrimitives()}で対象を差し替える)、calc()等の
 * &lt;length-percentage&gt;、3〜4値の端キーワード+オフセット構文
 * (例: {@code right 10px bottom 20px}——端からのオフセットはMIXED値
 * {@code 100% - 10px}へ畳む)、単一値の2つ目=center(css-values-4)に対応した。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class BackgroundPosition extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_X = new BackgroundPosition();

	public static final PrimitivePropertyInfo INFO_Y = new BackgroundPosition();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_X, INFO_Y };

	public static Offset get(CSSStyle style) {
		Value xValue = style.get(INFO_X);
		Value yValue = style.get(INFO_Y);
		return BoxValueUtils.toOffset(xValue, yValue);
	}

	protected BackgroundPosition() {
		this("background-position");
	}

	/** object-position等、同じ&lt;position&gt;文法を使う特性のための派生用です。 */
	protected BackgroundPosition(String name) {
		super(name);
	}

	public Value getDefault(CSSStyle style) {
		return PercentageValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	/**
	 * 計算値はPercentageValueまたはAbsoluteLengthです。
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	/**
	 * ショートハンド({@code background}・{@code mask})から&lt;position&gt;の
	 * トークン列を渡すための公開入口(2026-08-29)。4値構文
	 * ({@code right 10px bottom 20px})もここで解ける。
	 */
	public Entry[] parsePositionValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		return this.parseValues(tokens, ua, uri);
	}

	protected Entry[] parseValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		// 派生特性(object-position)からも使うため、対象primitiveは
		// getPrimitives()経由で決める
		final PrimitivePropertyInfo infoX = this.getPrimitives()[0];
		final PrimitivePropertyInfo infoY = this.getPrimitives()[1];
		if (tokens.isInherit()) {
			return new Entry[] { new Entry(infoX, KeywordValue.INHERIT), new Entry(infoY, KeywordValue.INHERIT) };
		}

		// 成分(キーワードまたは<length-percentage>)を最大4つ読む
		final String[] kw = new String[4];
		final Value[] val = new Value[4];
		int n = 0;
		for (CssToken token = tokens.next(); token != null; token = tokens.next()) {
			if (n >= 4) {
				throw new PropertyException();
			}
			if (token instanceof CssToken.Ident ident) {
				final String k = ident.lower();
				if (!(k.equals("left") || k.equals("right") || k.equals("top") || k.equals("bottom")
						|| k.equals("center"))) {
					throw new PropertyException();
				}
				kw[n++] = k;
			} else {
				final Value v = toOffsetValue(ua, token);
				if (v == null) {
					throw new PropertyException();
				}
				val[n++] = v;
			}
		}
		if (n == 0) {
			throw new PropertyException();
		}

		final Value x, y;
		if (n == 1) {
			// SPEC css-values <position>: 値が1つだけの場合の2つ目はcenter
			if (kw[0] != null) {
				switch (kw[0]) {
				case "left":
					x = PercentageValue.ZERO;
					y = PercentageValue.HALF;
					break;
				case "right":
					x = PercentageValue.FULL;
					y = PercentageValue.HALF;
					break;
				case "top":
					x = PercentageValue.HALF;
					y = PercentageValue.ZERO;
					break;
				case "bottom":
					x = PercentageValue.HALF;
					y = PercentageValue.FULL;
					break;
				default:
					x = y = PercentageValue.HALF;
					break;
				}
			} else {
				x = val[0];
				y = PercentageValue.HALF;
			}
		} else if (n == 2 && (kw[0] == null || kw[1] == null)) {
			// 値を含む2値構文: 1つ目=x、2つ目=y。キーワードは軸が固定される
			if (kw[0] != null) {
				x = keywordAxis(kw[0], true);
			} else {
				x = val[0];
			}
			if (kw[1] != null) {
				y = keywordAxis(kw[1], false);
			} else {
				y = val[1];
			}
		} else if (n == 2) {
			// キーワード2つ: 順不同で各軸へ割り当てる
			String kx = null, ky = null;
			int centers = 0;
			for (int i = 0; i < 2; ++i) {
				final String k = kw[i];
				if (k.equals("left") || k.equals("right")) {
					if (kx != null) {
						throw new PropertyException();
					}
					kx = k;
				} else if (k.equals("top") || k.equals("bottom")) {
					if (ky != null) {
						throw new PropertyException();
					}
					ky = k;
				} else {
					++centers;
				}
			}
			if (kx == null && centers > 0) {
				kx = "center";
				--centers;
			}
			if (ky == null && centers > 0) {
				ky = "center";
				--centers;
			}
			if (kx == null || ky == null) {
				throw new PropertyException();
			}
			x = keywordAxis(kx, true);
			y = keywordAxis(ky, false);
		} else {
			// 3〜4値: [端キーワード オフセット?] の組。centerはオフセット不可
			Value xv = null, yv = null;
			boolean centerPending = false;
			int i = 0;
			while (i < n) {
				final String k = kw[i];
				if (k == null) {
					throw new PropertyException();
				}
				Value off = null;
				if (i + 1 < n && val[i + 1] != null) {
					if (k.equals("center")) {
						throw new PropertyException();
					}
					off = val[i + 1];
					i += 2;
				} else {
					i += 1;
				}
				switch (k) {
				case "left":
					if (xv != null) {
						throw new PropertyException();
					}
					xv = off == null ? PercentageValue.ZERO : off;
					break;
				case "right":
					if (xv != null) {
						throw new PropertyException();
					}
					xv = off == null ? PercentageValue.FULL : flipFromFull(ua, off);
					break;
				case "top":
					if (yv != null) {
						throw new PropertyException();
					}
					yv = off == null ? PercentageValue.ZERO : off;
					break;
				case "bottom":
					if (yv != null) {
						throw new PropertyException();
					}
					yv = off == null ? PercentageValue.FULL : flipFromFull(ua, off);
					break;
				default:
					if (centerPending) {
						throw new PropertyException();
					}
					centerPending = true;
					break;
				}
			}
			if (centerPending) {
				if (xv == null) {
					xv = PercentageValue.HALF;
				} else if (yv == null) {
					yv = PercentageValue.HALF;
				} else {
					throw new PropertyException();
				}
			}
			if (xv == null || yv == null) {
				throw new PropertyException();
			}
			x = xv;
			y = yv;
		}
		return new Entry[] { new Entry(infoX, x), new Entry(infoY, y) };
	}

	/** 2値構文でのキーワードの軸解決です。horizontal軸にtop/bottomは書けません。 */
	private static Value keywordAxis(String keyword, boolean horizontal) throws PropertyException {
		switch (keyword) {
		case "left":
		case "top":
			if (horizontal != keyword.equals("left")) {
				throw new PropertyException();
			}
			return PercentageValue.ZERO;
		case "right":
		case "bottom":
			if (horizontal != keyword.equals("right")) {
				throw new PropertyException();
			}
			return PercentageValue.FULL;
		default:
			return PercentageValue.HALF;
		}
	}

	/** &lt;length-percentage&gt;(calc含む)を読みます。解釈できなければnull。 */
	private static Value toOffsetValue(UserAgent ua, CssToken token) {
		Value v = ValueUtils.toPercentage(token);
		if (v == null) {
			v = ValueUtils.toLength(ua, token);
		}
		if (v == null) {
			final Value calc = CalcValueUtils.toCalc(ua, token);
			if (calc instanceof PercentageValue || calc instanceof AbsoluteLengthValue
					|| calc instanceof CalcLengthValue || calc instanceof CalcFontRelativeValue) {
				v = calc;
			}
		}
		return v;
	}

	/**
	 * 端キーワード(right/bottom)からのオフセットを、開始端基準の
	 * {@code 100% - オフセット}へ畳みます。
	 */
	private static Value flipFromFull(UserAgent ua, Value off) throws PropertyException {
		if (off instanceof PercentageValue p) {
			return PercentageValue.create(100 - p.getRatio() * 100);
		}
		if (off instanceof AbsoluteLengthValue l) {
			return CalcLengthValue.create(ua, -l.getLength(), 1);
		}
		if (off instanceof CalcLengthValue c) {
			return CalcLengthValue.create(ua, -c.getAbsolute(), 1 - c.getRatio());
		}
		if (off instanceof RelativeLengthValue r) {
			return CalcFontRelativeValue.fullMinus(r.getUnit(), r.getValue());
		}
		if (off instanceof CalcFontRelativeValue f) {
			return f.subtractedFromFull();
		}
		throw new PropertyException();
	}
}
