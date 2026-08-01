package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.VarSubstitution;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * ある文脈(要素・@page・@font-face)で解釈可能なプロパティの集合です。
 *
 * @author MIYABE Tatsuhiko
 */
public abstract class PropertySet {
	private static final Logger LOG = Logger.getLogger(PropertySet.class.getName());

	private final Map<String, PropertyInfo> nameToInfo = new HashMap<String, PropertyInfo>();

	/**
	 * プロパティを登録します。
	 */
	protected final void put(PropertyInfo... infos) {
		for (PropertyInfo info : infos) {
			this.nameToInfo.put(info.getName(), info);
		}
	}

	/**
	 * 別名(ベンダープレフィックス等)でプロパティを登録します。
	 */
	protected final void alias(String name, PropertyInfo info) {
		this.nameToInfo.put(name, info);
	}

	protected PropertyInfo getPropertyParser(String name) {
		return this.nameToInfo.get(name);
	}

	/**
	 * 登録済みプロパティの列挙です(登録整合性テスト用——
	 * {@code PropertyCodeRegistryTest}が「全ての解釈可能プロパティは
	 * カスケード用コードを持つ」を静的に検査する。@page sizeで実際に
	 * 踏んだ「名前登録だけしてコード未割当→set()が黙って落ちる」罠の
	 * 再発防止、2026-08-01)。
	 */
	final java.util.Collection<PropertyInfo> registeredInfos() {
		return java.util.Collections.unmodifiableCollection(this.nameToInfo.values());
	}

	public final Property parseDeclaration(String name, List<CssToken> value, UserAgent ua, URI uri,
			boolean important) {
		if (isCustomPropertyName(name)) {
			// カスタムプロパティ(--name)は型検証を行わず生トークン列のまま
			// 保持する(名前は大文字小文字を区別するため小文字化しない)。
			return new CustomProperty(name, value, uri, important);
		}
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph != null) {
			if (VarSubstitution.containsVarReference(value)) {
				// var()の実際の値はカスケード適用時(要素ごと)に異なりうるため、
				// ここ(スタイルシート解析時、文書全体で1回)では解析を確定
				// できない。要素ごとの適用時まで遅延する(DeferredProperty参照)。
				return new DeferredProperty(name, ph, value, ua, uri, important);
			}
			TokenStream tokens = new TokenStream(value);
			try {
				return ph.parse(tokens, ua, uri, important);
			} catch (PropertyException e) {
				String m = name + ":" + tokens + ":" + e.getMessage();
				LOG.log(Level.FINE, m, e);
				ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, tokens.toString(), e.getMessage());
				return null;
			}
		}
		ua.message(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, name);
		return null;
	}

	private static boolean isCustomPropertyName(String name) {
		return name.length() > 2 && name.charAt(0) == '-' && name.charAt(1) == '-';
	}

	/**
	 * @supports (name: value) の判定用。プロパティ名が登録されており、かつ
	 * 与えられた値をそのプロパティとして解析できるかを試すだけで、実際の値は
	 * 破棄します(通常の{@link #parseDeclaration}と違い、失敗しても警告を
	 * 出しません——@supports は「対応していない」ことを調べるための構文であり、
	 * 未対応であること自体が正常な結果のため)。
	 */
	public final boolean supports(String name, List<CssToken> value, UserAgent ua, URI uri) {
		if (isCustomPropertyName(name)) {
			// カスタムプロパティの宣言文法は常に妥当(CSS仕様: -- で始まる
			// プロパティは任意のトークン列を受理する)
			return true;
		}
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph == null) {
			return false;
		}
		if (VarSubstitution.containsVarReference(value)) {
			// var()の実際の値は要素ごとに異なりうるため、ここでは評価せず
			// 常にtrueとする(ブラウザの挙動と同じ: var()を含む宣言は
			// @supportsの判定では無条件にサポートありとみなす)
			return true;
		}
		try {
			return ph.parse(new TokenStream(value), ua, uri, false) != null;
		} catch (PropertyException e) {
			return false;
		}
	}
}
