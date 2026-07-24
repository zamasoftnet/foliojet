package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.util.TextUtils;

/**
 * 注釈付きテキスト方式のルビ単位バッファです(2026-07-25新設、F-1試作。
 * {@code text.ruby=annotation}のときだけ{@link StyledTextUnitizer}が
 * 生成します)。
 *
 * <p>
 * ルビコンテナ(ruby要素相当のINLINE)の開始から終了までの文字イベントを
 * ため、「親文字列+ふりがな文字列」のペア(単位)へ切り分けます。
 * バッファはルビ要素1個分で有界です。live構築とソース再生は同じ
 * {@code StyledTextUnitizer}経路を通るため、この組み立ても両経路で
 * 同一に働きます(仕様裁定
 * docs/history/2026-07-25-ruby-annotation-spec-decision.md の核心)。
 * </p>
 *
 * <p>
 * 単位の切り分け規則:
 * </p>
 * <ul>
 * <li>ふりがな範囲(rt相当、{@code RUBY_TEXT})の終了で1単位を確定する
 * (親文字=直前までにたまった文字、ふりがな=rt内の文字)。</li>
 * <li>コンテナ終了時に残った親文字は「ふりがな無しの単位」として確定
 * する(malformedマークアップ——rtの無いruby等——の安全側)。</li>
 * <li>単位内のマークアップ(ネストした要素)は無視して文字だけを拾う。
 * ネスト深さだけを数え、対応する終了でコンテナの閉じを検出する。</li>
 * <li>空白・制御コードは通常の空白つぶし相当に単一スペースへ畳む
 * (行分割は単位内で起こらないため改行コードも空白扱い)。</li>
 * </ul>
 */
final class RubyUnitCollector {

	/** 確定した1単位(親文字列+ふりがな文字列のペア)です。 */
	record PendingUnit(String baseText, int baseOffset, String rubyText, int rubyOffset) {
	}

	private final InlineParams containerParams;

	/** コンテナ自身を1とするインラインのネスト深さです。 */
	private int depth = 1;

	/** ふりがな範囲(rt相当)の中かどうかです。 */
	private boolean inAnnotation = false;

	/** ふりがな範囲を開始したインラインの深さです。 */
	private int annotationDepth = 0;

	private final StringBuilder base = new StringBuilder();

	private final StringBuilder ruby = new StringBuilder();

	private boolean basePendingSpace = false, rubyPendingSpace = false;

	private int baseOffset = -1, rubyOffset = -1;

	private final List<PendingUnit> units = new ArrayList<>();

	RubyUnitCollector(final InlineParams containerParams) {
		this.containerParams = containerParams;
	}

	InlineParams containerParams() {
		return this.containerParams;
	}

	/**
	 * 確定済みの単位列を返します。未確定の残り(コンテナが正常に閉じず
	 * 配達される防御経路)もここで確定します(正常経路では
	 * {@link #endInline()}が確定済みのため冪等)。
	 */
	List<PendingUnit> units() {
		this.completeUnit();
		return this.units;
	}

	/** コンテナ内でインラインが開始された(役割はそのインラインのrubyRole)。 */
	void startInline(final byte rubyRole) {
		++this.depth;
		if (!this.inAnnotation && rubyRole == AbstractTextParams.RUBY_TEXT) {
			this.inAnnotation = true;
			this.annotationDepth = this.depth;
		}
	}

	/**
	 * コンテナ内でインラインが終了しました。コンテナ自身が閉じたら
	 * trueを返します(呼び出し側は単位列を取り出して配達する)。
	 */
	boolean endInline() {
		if (this.inAnnotation && this.depth == this.annotationDepth) {
			// ふりがな範囲の終了=単位の確定
			this.inAnnotation = false;
			this.completeUnit();
		}
		--this.depth;
		if (this.depth == 0) {
			this.completeUnit();
			return true;
		}
		return false;
	}

	/** コンテナ内の文字です。親文字またはふりがなのバッファへためます。 */
	void characters(final int charOffset, final char[] ch, final int off, final int len) {
		for (int i = 0; i < len; ++i) {
			final char c = ch[off + i];
			if (c == ' ' || TextUtils.isControl(c) || TextUtils.isWhiteSpace(c)) {
				// 空白・制御コードは単一スペースへ畳む(先頭は捨てる)
				if (this.inAnnotation) {
					this.rubyPendingSpace = this.ruby.length() > 0;
				} else {
					this.basePendingSpace = this.base.length() > 0;
				}
				continue;
			}
			if (this.inAnnotation) {
				if (this.rubyPendingSpace) {
					this.ruby.append(' ');
					this.rubyPendingSpace = false;
				}
				if (this.ruby.length() == 0) {
					this.rubyOffset = charOffset + i;
				}
				this.ruby.append(c);
			} else {
				if (this.basePendingSpace) {
					this.base.append(' ');
					this.basePendingSpace = false;
				}
				if (this.base.length() == 0) {
					this.baseOffset = charOffset + i;
				}
				this.base.append(c);
			}
		}
	}

	private void completeUnit() {
		if (this.base.length() == 0 && this.ruby.length() == 0) {
			return;
		}
		this.units.add(new PendingUnit(this.base.toString(), this.baseOffset, this.ruby.toString(), this.rubyOffset));
		this.base.setLength(0);
		this.ruby.setLength(0);
		this.basePendingSpace = this.rubyPendingSpace = false;
		this.baseOffset = this.rubyOffset = -1;
	}
}
