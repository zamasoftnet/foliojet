package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.VarSubstitution;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * var()を含む宣言値です。個別プロパティの型は宣言解析時点(スタイルシート
 * 解析時、文書全体で1回・共有)では確定できません——var()の実際の値は
 * カスケード適用時(要素ごと)に異なりうるためです。生トークン列を保持し、
 * 実際の解釈({@link PropertyInfo#parse})を{@link #applyProperty}
 * (要素ごとに呼ばれるカスケード適用の時点)まで遅延します。
 *
 * @author MIYABE Tatsuhiko
 */
public final class DeferredProperty implements Property {
	private final String name;
	private final PropertyInfo propertyInfo;
	private final List<CssToken> tokens;
	private final UserAgent ua;
	private final URI uri;
	private final boolean important;

	public DeferredProperty(String name, PropertyInfo propertyInfo, List<CssToken> tokens, UserAgent ua, URI uri,
			boolean important) {
		this.name = name;
		this.propertyInfo = propertyInfo;
		this.tokens = tokens;
		this.ua = ua;
		this.uri = uri;
		this.important = important;
	}

	public String getName() {
		return this.name;
	}

	public URI getURI() {
		return this.uri;
	}

	public boolean isImportant() {
		return this.important;
	}

	/**
	 * var()を要素ごとに解決してから、通常のプロパティ解析を再実行します。
	 * 参照先のカスタムプロパティが見つからない(フォールバックも無い)場合、
	 * 循環参照、または解決後のトークン列がこのプロパティとして解釈できない
	 * 場合は、CSS仕様の「使用値計算時に無効」に従い何もsetしません
	 * ({@link CSSStyle#get}の継承/デフォルト解決がそのまま働く)。
	 * 要素ごとに発生しうる失敗のため、通常の宣言解析失敗と異なり警告は
	 * 出しません(同じ規則が数千要素にマッチする場合に警告が氾濫するのを
	 * 避けるため)。
	 */
	public void applyProperty(CSSStyle style) {
		List<CssToken> substituted = VarSubstitution.substitute(this.tokens, style);
		if (substituted == null) {
			return;
		}
		Property resolved;
		try {
			resolved = this.propertyInfo.parse(new TokenStream(substituted), this.ua, this.uri, this.important);
		} catch (PropertyException e) {
			return;
		}
		if (resolved != null) {
			resolved.applyProperty(style);
		}
	}

	public String toString() {
		return this.name + ": " + this.tokens + (this.important ? " !important" : "") + " [var() deferred]";
	}
}
