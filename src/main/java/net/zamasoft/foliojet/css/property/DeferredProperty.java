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
			this.applyInvalidAtComputedValueTime(style);
			return;
		}
		Property resolved;
		try {
			resolved = this.propertyInfo.parse(new TokenStream(substituted), this.ua, this.uri, this.important);
		} catch (PropertyException e) {
			this.applyInvalidAtComputedValueTime(style);
			return;
		}
		if (resolved != null) {
			resolved.applyProperty(style);
		}
	}

	/**
	 * 「使用値計算時に無効」を適用します(2026-08-03)。
	 *
	 * <p>
	 * <b>何もしないのでは足りない。</b> 従来はここで {@code return} していた
	 * ため、同じ要素の<b>下位の宣言が生き残って</b>いた——
	 * {@code p { color: blue; color: var(--未定義) }} で青のままになる。
	 * カスケードで勝ったのは {@code var()} の側なので、青は既に負けており、
	 * 復活してはならない。
	 * </p>
	 *
	 * <p>
	 * 仕様(CSS Variables 1「invalid at computed-value time」)では、この宣言は
	 * {@code unset} を指定したのと同じ扱いになる——継承特性なら継承値、
	 * 非継承特性なら初期値。{@link CSSStyle}は{@code unset}をそのとおりに
	 * 解決するので、明示的に置く。Chrome・Firefoxとも同じ挙動
	 * (2026-08-03に確認)。
	 * </p>
	 */
	private void applyInvalidAtComputedValueTime(final CSSStyle style) {
		// 一括指定(shorthand)もあるので、値を直に置かず**同じ解析器へ
		// `unset` を通す**。そうすれば一括指定は自分の個別指定すべてへ
		// 展開してくれる(CSS全体キーワードはどのプロパティも受け付ける)
		final Property unset;
		try {
			unset = this.propertyInfo.parse(new TokenStream(java.util.List.of(CssToken.Keyword.UNSET)), this.ua,
					this.uri, this.important);
		} catch (PropertyException e) {
			// 全体キーワードを拒むプロパティは無い想定。仮に来ても、
			// 従来どおり何もしないより悪くはならない
			return;
		}
		if (unset != null) {
			unset.applyProperty(style);
		}
	}

	public String toString() {
		return this.name + ": " + this.tokens + (this.important ? " !important" : "") + " [var() deferred]";
	}
}
