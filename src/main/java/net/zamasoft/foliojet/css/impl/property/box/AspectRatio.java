package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AspectRatioValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code aspect-ratio}です(css-sizing-4 §5、2026-08-29)。
 * {@code auto | <ratio> | auto && <ratio>}、{@code <ratio>}は
 * {@code <number> [ / <number> ]?}(非負)。非継承、既定{@code auto}。
 * 50サイトの変換掃過で424回(23サイト)出現——16:9サムネイル
 * (yomiuri/cnn/cookpad)の高さがこれだけで決まっている。
 *
 * <p>
 * 印刷向けの意味づけ: 置換要素は{@code auto}併記なら固有比率を優先
 * ({@code AbstractReplacedBox})、非置換ボックスは行方向が確定して
 * いてページ方向が{@code auto}なら比率で高さを決める
 * ({@code FlowBlockBox}/{@code AbstractStaticBlockBox})。内容が比率高
 * より高いときは{@code overflow:visible}なら内容に合わせて伸びる
 * (仕様の{@code min-height:auto}=内容寸法の近似)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class AspectRatio extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new AspectRatio();

	public static AspectRatioValue get(CSSStyle style) {
		return (AspectRatioValue) style.get(INFO);
	}

	protected AspectRatio() {
		super("aspect-ratio");
	}

	public Value getDefault(CSSStyle style) {
		return AspectRatioValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		boolean auto = false;
		double ratio = 0;
		boolean hasRatio = false;
		while (tokens.hasNext()) {
			if (!auto && tokens.eat("auto")) {
				auto = true;
				continue;
			}
			if (hasRatio) {
				throw new PropertyException();
			}
			final CssToken.Num width = tokens.number();
			if (width == null || width.value() < 0) {
				throw new PropertyException();
			}
			double height = 1;
			if (tokens.eatSlash()) {
				final CssToken.Num h = tokens.number();
				if (h == null || h.value() < 0) {
					throw new PropertyException();
				}
				height = h.value();
			}
			ratio = height == 0 ? Double.POSITIVE_INFINITY : width.value() / height;
			hasRatio = true;
		}
		if (!auto && !hasRatio) {
			throw new PropertyException();
		}
		return AspectRatioValue.create(auto, ratio);
	}
}
