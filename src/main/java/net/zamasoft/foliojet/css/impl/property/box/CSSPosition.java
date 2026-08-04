package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSPosition extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSPosition();

	public static byte get(CSSStyle style) {
		PositionValue value = (PositionValue) style.get(INFO);
		return value.getPosition();
	}

	private CSSPosition() {
		super("position");
	}

	public Value getDefault(CSSStyle style) {
		return PositionValue.STATIC_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("static")) {
				return PositionValue.STATIC_VALUE;
			} else if (ident.equals("relative")) {
				return PositionValue.RELATIVE_VALUE;
			} else if (ident.equals("absolute")) {
				return PositionValue.ABSOLUTE_VALUE;
			} else if (ident.equals("fixed")) {
				return PositionValue.FIXED_VALUE;
			} else if (ident.equals("sticky")) {
				// **sticky は relative として扱う**(2026-08-05)。
				//
				// css-position-3 の定義は「relative と同じように配置するが、
				// ずらす量は<b>最も近い祖先のスクロール領域</b>を基準に決める」。
				// 紙にはスクロール領域が無いので、ずれ幅は常に0になり、
				// **relative と同じ**に落ちる。ブラウザの印刷も同じ結果になる。
				//
				// それまでは不正な値として宣言ごと捨てていた。結果は static で
				// 実害は小さかったが、
				//   ・「Invalid value "sticky" on 'position'」が全ページ分出て
				//     本物の警告を埋めていた(実地コーパスで多数)
				//   ・relative は包含ブロックを作るので、子の絶対配置の基準が
				//     static のときとずれる——ここは実際に版面が変わる
				// 実地コーパス第13波(vercel/IBM Carbon等、サイドバー付きの
				// 文書サイトはほぼ全部この作り)で見つけた。
				return PositionValue.RELATIVE_VALUE;
			}
		}
		throw new PropertyException();
	}

}