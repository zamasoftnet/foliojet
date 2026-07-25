package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextWrapStyleValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * CSS Text 4 の {@code text-wrap-style} です(2026-07-25新設。旧
 * {@code text.line-breaker}変換プロパティを置き換える)。
 *
 * <p>
 * 行分割戦略のオプトインで、継承します。既定は{@code auto}=貪欲法
 * (性能)、{@code pretty}でKnuth-Plass全体最適(品質)を選びます——
 * ブラウザ(Chrome/Firefox/Safari)と同じ切り分けです。
 * </p>
 *
 * <p>
 * <b>{@code balance}と{@code stable}は構文としては受理しますが
 * {@code auto}として扱います}</b>(未対応。この制限は
 * copperpdf4の{@code docs/CSS-SUPPORT.md}に明記されます)。
 * {@code balance}は行数の均等化、{@code stable}は再組版時の先頭行安定を
 * 求めるもので、いずれも本エンジンの2系統(貪欲法/K-P)には
 * 対応物がありません。
 * </p>
 *
 * <p>
 * K-Pの適用単位は段落なので、実効するのは段落を確立するブロックに
 * 算出された値だけです。インライン要素や{@code ::first-line}に指定しても
 * 段落の組み方は変わりません({@code ::first-line}がある段落はそもそも
 * K-Pの適格条件から外れます)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class TextWrapStyle extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextWrapStyle();

	public static byte get(CSSStyle style) {
		return ((TextWrapStyleValue) style.get(INFO)).getTextWrapStyle();
	}

	protected TextWrapStyle() {
		super("text-wrap-style");
	}

	public Value getDefault(CSSStyle style) {
		return TextWrapStyleValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			final Value value = toValue(((CssToken.Ident) lu).lower());
			if (value != null) {
				return value;
			}
		}
		throw new PropertyException();
	}

	/**
	 * 識別子を値へ変換します(短縮形{@code text-wrap}と共有)。
	 * 未知の識別子ではnullを返します。
	 */
	public static Value toValue(final String ident) {
		switch (ident) {
		case "auto":
			return TextWrapStyleValue.AUTO_VALUE;

		case "pretty":
			return TextWrapStyleValue.PRETTY_VALUE;

		case "balance":
		case "stable":
			// 構文としては受理するがautoとして扱う(未対応)
			return TextWrapStyleValue.AUTO_VALUE;

		default:
			return null;
		}
	}
}
