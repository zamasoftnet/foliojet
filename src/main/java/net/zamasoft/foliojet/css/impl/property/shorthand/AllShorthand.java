package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.PropertyInfo;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code all}(css-cascade-4 §3.1、2026-08-29)。
 *
 * <p>
 * 値は全体キーワード({@code inherit}/{@code initial}/{@code unset}、
 * {@code revert}系は宣言ごと無視)だけで、{@code direction}と
 * {@code unicode-bidi}を除く全longhandへ配る。実サイトはボタンの
 * リセット({@code all: unset})に使う。longhandの一覧は最初の呼び出し時に
 * 登録簿から集める(登録順序の都合で構築時には揃っていない)。
 * </p>
 */
public final class AllShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new AllShorthand();

	private volatile PrimitivePropertyInfo[] longhands;

	private AllShorthand() {
		super("all");
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		PrimitivePropertyInfo[] result = this.longhands;
		if (result == null) {
			final List<PrimitivePropertyInfo> list = new ArrayList<>();
			for (final PropertyInfo info : ElementPropertySet.getInstance().registeredInfos()) {
				if (info instanceof PrimitivePropertyInfo primitive && !list.contains(primitive)
						&& !"direction".equals(primitive.getName()) && !"unicode-bidi".equals(primitive.getName())
						&& ElementPropertySet.getCode(primitive) >= 0) {
					list.add(primitive);
				}
			}
			result = list.toArray(new PrimitivePropertyInfo[0]);
			this.longhands = result;
		}
		return result;
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		// 全体キーワード以外は不正(基底が全体キーワードを処理済み)
		throw new PropertyException();
	}
}
