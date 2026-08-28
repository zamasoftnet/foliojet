package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * inset 特性です(css-logical §4.4。top/right/bottom/leftの一括指定で、
 * marginと同じ1〜4値のTRBL展開。論理軸ではなく物理辺へ展開する点も
 * 仕様どおり)。
 *
 * <p>
 * 未対応だと`inset:0; margin:auto`の絶対配置センタリング(実地で頻出の
 * 中央寄せイディオム)が丸ごと落ち、要素が静的位置(左上)へ張り付く
 * (asahi.comの動画再生アイコンが左上へ寄った、2026-08-27)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class InsetShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new InsetShorthand();

	protected InsetShorthand() {
		super("inset");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final Value inset1 = BoxValueUtils.toTRLB(ua, tokens.next());
		if (inset1 == null) {
			throw new PropertyException();
		}
		if (inset1 == KeywordValue.INHERIT) {
			primitives.set(Inset.TOP, KeywordValue.INHERIT);
			primitives.set(Inset.RIGHT, KeywordValue.INHERIT);
			primitives.set(Inset.BOTTOM, KeywordValue.INHERIT);
			primitives.set(Inset.LEFT, KeywordValue.INHERIT);
			return;
		}
		if (!tokens.hasNext()) {
			primitives.set(Inset.TOP, inset1);
			primitives.set(Inset.RIGHT, inset1);
			primitives.set(Inset.BOTTOM, inset1);
			primitives.set(Inset.LEFT, inset1);
			return;
		}
		final Value inset2 = BoxValueUtils.toTRLB(ua, tokens.next());
		if (inset2 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(Inset.TOP, inset1);
			primitives.set(Inset.RIGHT, inset2);
			primitives.set(Inset.BOTTOM, inset1);
			primitives.set(Inset.LEFT, inset2);
			return;
		}
		final Value inset3 = BoxValueUtils.toTRLB(ua, tokens.next());
		if (inset3 == null) {
			throw new PropertyException();
		}
		if (!tokens.hasNext()) {
			primitives.set(Inset.TOP, inset1);
			primitives.set(Inset.RIGHT, inset2);
			primitives.set(Inset.BOTTOM, inset3);
			primitives.set(Inset.LEFT, inset2);
			return;
		}
		final Value inset4 = BoxValueUtils.toTRLB(ua, tokens.next());
		if (inset4 == null) {
			throw new PropertyException();
		}
		primitives.set(Inset.TOP, inset1);
		primitives.set(Inset.RIGHT, inset2);
		primitives.set(Inset.BOTTOM, inset3);
		primitives.set(Inset.LEFT, inset4);
	}
}
