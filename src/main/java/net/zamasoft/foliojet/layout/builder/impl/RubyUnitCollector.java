package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.value.RubyMergeValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.util.TextUtils;

/**
 * HTML/CSS Rubyの文字イベントを、親文字と複数注釈レベルへ正規化します。
 * ルビ要素1個だけを有界バッファに保持し、{@code rb}/{@code rt}の対応、
 * {@code rtc}による両側・複数段注釈、{@code ruby-merge}による熟語ルビを
 * 同じ単位表現へ落とします。
 */
final class RubyUnitCollector {

	/** 親文字または注釈の整形前断片です。 */
	record Segment(String text, InlineParams params, int charOffset, int charEnd) {
	}

	/** 1注釈と、それが属する注釈レベルです。 */
	record Annotation(Segment segment, int level) {
	}

	interface Sink {
		void emitRubyUnit(Segment base, List<Annotation> annotations);
	}

	private static final class Level {
		final InlineParams params;
		final List<Segment> annotations = new ArrayList<Segment>();
		boolean anonymousSpanning = false;

		Level(final InlineParams params) {
			this.params = params;
		}
	}

	private final InlineParams containerParams;
	private final Sink sink;
	private int depth = 1;
	private final List<InlineParams> paramsStack = new ArrayList<InlineParams>();

	private int baseDepth = 0;
	private int annotationDepth = 0;
	private int annotationContainerDepth = 0;
	private Level activeLevel = null;

	private final List<Segment> bases = new ArrayList<Segment>();
	private final List<Level> levels = new ArrayList<Level>();
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

	void startInline(final InlineParams params) {
		++this.depth;
		this.paramsStack.add(params);
		// rt/rb内部の装飾要素はスタイルスタックとしてだけ扱う。
		if (this.annotationDepth != 0 || this.baseDepth != 0) {
			return;
		}
		switch (params.rubyRole) {
		case AbstractTextParams.RUBY_TEXT_CONTAINER:
			this.flushBase();
			this.activeLevel = new Level(params);
			this.levels.add(this.activeLevel);
			this.annotationContainerDepth = this.depth;
			break;
		case AbstractTextParams.RUBY_TEXT:
			if (this.annotationContainerDepth == 0) {
				this.flushBase();
				if (this.activeLevel == null) {
					this.activeLevel = new Level(this.containerParams);
					this.levels.add(this.activeLevel);
				}
			} else {
				// rtc直下の裸テキストが先行していれば、匿名のspanning注釈。
				this.flushAnnotation(true);
			}
			this.annotationDepth = this.depth;
			break;
		case AbstractTextParams.RUBY_BASE:
			if (!this.levels.isEmpty()) {
				this.flushBase();
				this.emitSegment();
			}
			this.flushBase();
			this.baseDepth = this.depth;
			break;
		default:
			break;
		}
	}

	/** コンテナ自身が閉じたときだけtrue。 */
	boolean endInline() {
		if (this.annotationDepth != 0 && this.depth == this.annotationDepth) {
			this.flushAnnotation(false);
			this.annotationDepth = 0;
		} else if (this.baseDepth != 0 && this.depth == this.baseDepth) {
			this.flushBase();
			this.baseDepth = 0;
		} else if (this.annotationContainerDepth != 0 && this.depth == this.annotationContainerDepth) {
			this.flushAnnotation(true);
			this.annotationContainerDepth = 0;
			this.activeLevel = null;
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

	void characters(final int charOffset, final char[] ch, final int off, final int len) {
		final boolean annotation = this.annotationDepth != 0 || this.annotationContainerDepth != 0;
		if (!annotation && !this.levels.isEmpty() && containsNonWhiteSpace(ch, off, len)) {
			// HTML5では1つのruby要素に複数segmentを置ける。注釈後に
			// 親文字が再開した地点をsegment境界とする。
			this.flushBase();
			this.emitSegment();
		}
		for (int i = 0; i < len; ++i) {
			final char c = ch[off + i];
			if (charOffset >= 0) {
				if (this.buffStart < 0) {
					this.buffStart = charOffset + i;
				}
				this.buffEnd = charOffset + i + 1;
			}
			if (c == ' ' || TextUtils.isControl(c) || TextUtils.isWhiteSpace(c)) {
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

	/** malformedなブロック混入時は、現在までを安全な単位として確定する。 */
	void drain() {
		if (this.annotationDepth != 0 || this.annotationContainerDepth != 0) {
			this.flushAnnotation(this.annotationDepth == 0);
		} else {
			this.flushBase();
		}
		this.emitSegment();
	}

	private void finish() {
		if (this.annotationDepth != 0 || this.annotationContainerDepth != 0) {
			this.flushAnnotation(this.annotationDepth == 0);
		} else {
			this.flushBase();
		}
		this.emitSegment();
	}

	private void emitSegment() {
		if (this.bases.isEmpty() && this.levels.isEmpty()) {
			this.resetSegment();
			return;
		}
		boolean merge = this.containerParams.rubyMerge == RubyMergeValue.MERGE;
		for (final Level level : this.levels) {
			merge |= level.anonymousSpanning && this.bases.size() > 1;
			merge |= level.params.rubyMerge == RubyMergeValue.MERGE;
		}
		if (!merge && this.containerParams.rubyMerge == RubyMergeValue.AUTO) {
			merge = this.needsAutomaticMerge();
		}
		for (final Level level : this.levels) {
			if (!merge && level.params.rubyMerge == RubyMergeValue.AUTO) {
				merge = this.needsAutomaticMerge();
			}
		}

		if (merge) {
			final Segment base = combine(this.bases, this.containerParams);
			final List<Annotation> annotations = new ArrayList<Annotation>();
			for (int level = 0; level < this.levels.size(); ++level) {
				final Segment annotation = combine(this.levels.get(level).annotations,
						this.levels.get(level).params);
				if (annotation != null) {
					annotations.add(new Annotation(annotation, level));
				}
			}
			this.sink.emitRubyUnit(base, annotations);
		} else {
			int count = this.bases.size();
			for (final Level level : this.levels) {
				count = Math.max(count, level.annotations.size());
			}
			for (int i = 0; i < count; ++i) {
				final Segment base = i < this.bases.size() ? this.bases.get(i) : null;
				final List<Annotation> annotations = new ArrayList<Annotation>();
				for (int level = 0; level < this.levels.size(); ++level) {
					final List<Segment> candidates = this.levels.get(level).annotations;
					if (i < candidates.size()) {
						annotations.add(new Annotation(candidates.get(i), level));
					}
				}
				this.sink.emitRubyUnit(base, annotations);
			}
		}
		this.resetSegment();
	}

	private boolean needsAutomaticMerge() {
		for (final Level level : this.levels) {
			final int count = Math.min(this.bases.size(), level.annotations.size());
			for (int i = 0; i < count; ++i) {
				final String base = this.bases.get(i).text();
				final String ruby = level.annotations.get(i).text();
				if (ruby.codePointCount(0, ruby.length()) > base.codePointCount(0, base.length()) * 2) {
					return true;
				}
			}
		}
		return false;
	}

	private void resetSegment() {
		this.bases.clear();
		this.levels.clear();
		this.activeLevel = null;
	}

	private void flushBase() {
		final Segment segment = this.takeBuffer();
		if (segment != null) {
			this.bases.add(segment);
		}
	}

	private void flushAnnotation(final boolean anonymous) {
		final Segment segment = this.takeBuffer();
		if (segment == null) {
			return;
		}
		if (this.activeLevel == null) {
			this.activeLevel = new Level(this.containerParams);
			this.levels.add(this.activeLevel);
		}
		this.activeLevel.annotations.add(segment);
		this.activeLevel.anonymousSpanning |= anonymous;
	}

	private Segment takeBuffer() {
		final int start = this.buffStart, end = this.buffEnd;
		final InlineParams params = this.buffParams == null ? this.currentParams() : this.buffParams;
		final String text = transform(this.buff.toString(), params);
		this.buff.setLength(0);
		this.buffParams = null;
		this.pendingSpace = false;
		this.buffStart = this.buffEnd = -1;
		return text.isEmpty() ? null : new Segment(text, params, start, end);
	}

	private InlineParams currentParams() {
		return this.paramsStack.isEmpty() ? this.containerParams : this.paramsStack.get(this.paramsStack.size() - 1);
	}

	private static Segment combine(final List<Segment> segments, final InlineParams fallback) {
		if (segments.isEmpty()) {
			return null;
		}
		final StringBuilder text = new StringBuilder();
		int start = -1, end = -1;
		for (final Segment segment : segments) {
			text.append(segment.text());
			if (segment.charOffset() >= 0) {
				start = start < 0 ? segment.charOffset() : Math.min(start, segment.charOffset());
			}
			end = Math.max(end, segment.charEnd());
		}
		final InlineParams params = segments.get(0).params() == null ? fallback : segments.get(0).params();
		return new Segment(text.toString(), params, start, end);
	}

	private static boolean containsNonWhiteSpace(final char[] ch, final int off, final int len) {
		for (int i = 0; i < len; ++i) {
			final char c = ch[off + i];
			if (c != ' ' && !TextUtils.isControl(c) && !TextUtils.isWhiteSpace(c)) {
				return true;
			}
		}
		return false;
	}

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
