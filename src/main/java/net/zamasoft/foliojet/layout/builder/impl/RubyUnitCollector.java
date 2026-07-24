package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.util.TextUtils;

/**
 * ルビ単位バッファです(注釈付きテキスト方式、2026-07-25新設。
 * {@link StyledTextUnitizer}がルビコンテナの開始で生成します)。
 *
 * <p>
 * ルビコンテナ(ruby要素)の開始から終了までの文字イベントをため、
 * 「親文字列+ふりがな文字列」のペア(単位)へ切り分けて、対応が
 * ついた時点で{@link Sink}へ<b>逐次</b>配達します(確定済み単位を
 * ルビ終了までため込まない——ストリーミング処理の原則)。live構築と
 * ソース再生は同じ{@code StyledTextUnitizer}経路を通るため、この
 * 組み立ても両経路で同一に働きます(仕様裁定
 * docs/history/2026-07-25-ruby-annotation-spec-decision.md の核心)。
 * </p>
 *
 * <p>
 * 単位の切り分け規則:
 * </p>
 * <ul>
 * <li>親文字の境界は、明示的な{@code rb}(={@code RUBY_BASE})の終了か、
 * ふりがな({@code rt}={@code RUBY_TEXT})の開始である(HTML5に
 * {@code rb}は無く、ruby直下の裸テキストが親文字になる)。</li>
 * <li>親文字とふりがなはそれぞれ<b>キュー</b>へ積み、両方がそろった
 * 時点で1単位ずつ配達する。これにより
 * {@code <ruby><rb>京</rb><rb>都</rb><rt>きょう</rt><rt>と</rt></ruby>}
 * のような複数ペアが正しく対応づく。</li>
 * <li>コンテナ終了時に残った片方だけの断片は、その断片だけの単位として
 * 配達する(malformedマークアップ——rtの無いruby等——の安全側)。</li>
 * <li>単位内のマークアップ(ネストした要素)は箱を作らず、文字と
 * <b>スタイル</b>だけを拾う(断片の書式は、その断片の最初の文字の
 * 時点で有効なインラインのスタイル)。</li>
 * <li>空白・制御コードは通常の空白つぶし相当に単一スペースへ畳む
 * (行分割は単位内で起こらないため改行コードも空白扱い)。</li>
 * </ul>
 *
 * <p>
 * 作らないもの(仕様): {@code rtc}・複数注釈レベル・単位内改行・
 * オーバーハング組版・ルビ内の置換要素/インラインブロック/絶対配置。
 * </p>
 */
final class RubyUnitCollector {

	/**
	 * 単位の片側(親文字またはふりがな)です。
	 *
	 * @param text       整形済みの文字列(空白つぶし・text-transform適用後)
	 * @param params     この断片の書式(最初の文字の時点で有効なインライン)
	 * @param charOffset 断片が消費したソース文字の先頭オフセット(無ければ-1)
	 * @param charEnd    断片が消費したソース文字の終端(exclusive。無ければ-1)
	 */
	record Segment(String text, InlineParams params, int charOffset, int charEnd) {
	}

	/** 対応がついた単位の配達先です。 */
	interface Sink {
		/**
		 * 単位を1つ配達します。{@code base}・{@code ruby}のどちらかは
		 * nullのことがあります(malformedマークアップ)。
		 */
		void emitRubyUnit(Segment base, Segment ruby);
	}

	private final InlineParams containerParams;

	private final Sink sink;

	/** コンテナ自身を1とするインラインのネスト深さです。 */
	private int depth = 1;

	/** コンテナ内で開いているインラインのスタイルです(先頭=コンテナ)。 */
	private final List<InlineParams> paramsStack = new ArrayList<InlineParams>();

	/** ふりがな範囲(rt相当)の中かどうかです。 */
	private boolean inAnnotation = false;

	/** ふりがな範囲を開始したインラインの深さです。 */
	private int annotationDepth = 0;

	/** 明示的な親文字範囲(rb相当)の深さです(0=開いていない)。 */
	private int baseDepth = 0;

	/** 対応待ちの親文字・ふりがなです(有界——ルビ要素1個分)。 */
	private final Deque<Segment> bases = new ArrayDeque<Segment>();

	private final Deque<Segment> rubies = new ArrayDeque<Segment>();

	private final StringBuilder buff = new StringBuilder();

	private InlineParams buffParams = null;

	private boolean pendingSpace = false;

	private int buffStart = -1, buffEnd = -1;

	RubyUnitCollector(final InlineParams containerParams, final Sink sink) {
		this.containerParams = containerParams;
		this.sink = sink;
		this.paramsStack.add(containerParams);
	}

	InlineParams containerParams() {
		return this.containerParams;
	}

	/** コンテナ内でインラインが開始されました。 */
	void startInline(final InlineParams params) {
		++this.depth;
		this.paramsStack.add(params);
		if (this.inAnnotation) {
			// ふりがなの中のマークアップは深さだけ数える
			return;
		}
		switch (params.rubyRole) {
		case AbstractTextParams.RUBY_TEXT:
			// ふりがなの開始 = ここまでの親文字の確定(暗黙のrb境界)
			this.flush(this.bases);
			this.inAnnotation = true;
			this.annotationDepth = this.depth;
			break;
		case AbstractTextParams.RUBY_BASE:
			if (this.baseDepth == 0) {
				// 明示的な親文字の開始 = 直前の裸テキストの確定
				this.flush(this.bases);
				this.baseDepth = this.depth;
			}
			break;
		default:
			break;
		}
	}

	/**
	 * コンテナ内でインラインが終了しました。コンテナ自身が閉じたら
	 * trueを返します(呼び出し側は通常のインライン終了処理へ進む)。
	 */
	boolean endInline() {
		if (this.inAnnotation && this.depth == this.annotationDepth) {
			// ふりがな範囲の終了 = 断片の確定
			this.inAnnotation = false;
			this.flush(this.rubies);
			this.pair();
		} else if (!this.inAnnotation && this.baseDepth != 0 && this.depth == this.baseDepth) {
			// 明示的な親文字範囲の終了 = 断片の確定
			this.baseDepth = 0;
			this.flush(this.bases);
			this.pair();
		}
		if (!this.paramsStack.isEmpty()) {
			this.paramsStack.remove(this.paramsStack.size() - 1);
		}
		--this.depth;
		if (this.depth <= 0) {
			this.finish();
			return true;
		}
		return false;
	}

	/** コンテナ内の文字です。親文字またはふりがなのバッファへためます。 */
	void characters(final int charOffset, final char[] ch, final int off, final int len) {
		for (int i = 0; i < len; ++i) {
			final char c = ch[off + i];
			if (charOffset >= 0) {
				// ソース範囲は「捨てた空白」も含めて数える(単位が消費した
				// ソースの境界——改ページ部分再生の再開位置に使う)
				if (this.buffStart < 0) {
					this.buffStart = charOffset + i;
				}
				this.buffEnd = charOffset + i + 1;
			}
			if (c == ' ' || TextUtils.isControl(c) || TextUtils.isWhiteSpace(c)) {
				// 空白・制御コードは単一スペースへ畳む(先頭は捨てる)
				this.pendingSpace = this.buff.length() > 0;
				continue;
			}
			if (this.buffParams == null) {
				this.buffParams = this.currentParams();
			}
			if (this.pendingSpace) {
				this.buff.append(' ');
				this.pendingSpace = false;
			}
			this.buff.append(c);
		}
	}

	/**
	 * コンテナが正常に閉じないまま配達を強制されました(malformed——
	 * ルビの中にブロックが現れた等)。たまっている分をその場で配達し
	 * ますが、深さの追跡は継続します(通常のインラインスタックを
	 * 誤popしないため)。
	 */
	void drain() {
		this.flush(this.inAnnotation ? this.rubies : this.bases);
		this.pair();
	}

	private void finish() {
		this.flush(this.inAnnotation ? this.rubies : this.bases);
		this.pair();
		// 片方だけ残った断片(malformed)はその断片だけの単位にする
		while (!this.bases.isEmpty()) {
			this.sink.emitRubyUnit(this.bases.poll(), null);
		}
		while (!this.rubies.isEmpty()) {
			this.sink.emitRubyUnit(null, this.rubies.poll());
		}
	}

	/** 親文字とふりがながそろっている分を配達します。 */
	private void pair() {
		while (!this.bases.isEmpty() && !this.rubies.isEmpty()) {
			this.sink.emitRubyUnit(this.bases.poll(), this.rubies.poll());
		}
	}

	private InlineParams currentParams() {
		return this.paramsStack.isEmpty() ? this.containerParams : this.paramsStack.get(this.paramsStack.size() - 1);
	}

	/** バッファの内容を断片として確定し、指定のキューへ積みます。 */
	private void flush(final Deque<Segment> queue) {
		final int start = this.buffStart, end = this.buffEnd;
		final InlineParams params = this.buffParams == null ? this.currentParams() : this.buffParams;
		final String text = transform(this.buff.toString(), params);
		this.buff.setLength(0);
		this.buffParams = null;
		this.pendingSpace = false;
		this.buffStart = this.buffEnd = -1;
		if (text.isEmpty()) {
			return;
		}
		queue.add(new Segment(text, params, start, end));
	}

	/**
	 * text-transformを適用します(ルビ内のマークアップは箱にしないため、
	 * 断片ごとの書式としてここで解決する)。
	 */
	private static String transform(final String text, final AbstractTextParams params) {
		switch (params.textTransform) {
		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE:
			return text.toLowerCase(java.util.Locale.ROOT);
		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE:
			return text.toUpperCase(java.util.Locale.ROOT);
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE: {
			final char[] ch = text.toCharArray();
			boolean spaceBefore = true;
			for (int i = 0; i < ch.length; ++i) {
				if (Character.isLetter(ch[i])) {
					if (spaceBefore) {
						ch[i] = Character.toUpperCase(ch[i]);
					}
					spaceBefore = false;
				} else {
					spaceBefore = true;
				}
			}
			return new String(ch);
		}
		default:
			return text;
		}
	}
}
