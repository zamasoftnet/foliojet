package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.util.TextUtils;

/** 割注コンテナ内の文字と先頭書式を収集する有界バッファ。 */
final class WarichuCollector {
	record Segment(String text, InlineParams params, int sourceStart, int sourceEnd) {
	}

	interface Sink {
		void emitWarichu(Segment segment);
	}

	private final InlineParams containerParams;
	private final Sink sink;
	private final List<InlineParams> paramsStack = new ArrayList<InlineParams>();
	private final StringBuilder buff = new StringBuilder();
	private int depth = 1;
	private InlineParams firstParams = null;
	private boolean pendingSpace = false;
	private int sourceStart = -1, sourceEnd = -1;
	private boolean emitted = false;

	WarichuCollector(final InlineParams containerParams, final Sink sink) {
		this.containerParams = containerParams;
		this.sink = sink;
		this.paramsStack.add(containerParams);
	}

	void startInline(final InlineParams params) {
		++this.depth;
		this.paramsStack.add(params);
	}

	boolean endInline() {
		if (!this.paramsStack.isEmpty()) {
			this.paramsStack.remove(this.paramsStack.size() - 1);
		}
		--this.depth;
		if (this.depth <= 0) {
			this.emit();
			return true;
		}
		return false;
	}

	void characters(final int charOffset, final char[] ch, final int off, final int len) {
		final InlineParams params = this.currentParams();
		for (int i = 0; i < len; ++i) {
			char c = ch[off + i];
			if (charOffset >= 0) {
				if (this.sourceStart < 0) {
					this.sourceStart = charOffset + i;
				}
				this.sourceEnd = charOffset + i + 1;
			}
			if (c == ' ' || TextUtils.isControl(c) || TextUtils.isWhiteSpace(c)) {
				this.pendingSpace = this.buff.length() > 0;
				continue;
			}
			if (this.firstParams == null) {
				this.firstParams = params;
			}
			if (this.pendingSpace) {
				this.buff.append(' ');
				this.pendingSpace = false;
			}
			c = transform(c, params.textTransform, this.buff.length() == 0
						|| Character.isWhitespace(this.buff.charAt(this.buff.length() - 1)));
			this.buff.append(c);
		}
	}

	void drain() {
		this.emit();
	}

	private void emit() {
		if (this.emitted) {
			return;
		}
		this.emitted = true;
		if (this.buff.length() == 0) {
			return;
		}
		this.sink.emitWarichu(new Segment(this.buff.toString(),
				this.firstParams == null ? this.containerParams : this.firstParams, this.sourceStart, this.sourceEnd));
	}

	private InlineParams currentParams() {
		return this.paramsStack.isEmpty() ? this.containerParams : this.paramsStack.get(this.paramsStack.size() - 1);
	}

	private static char transform(final char c, final byte mode, final boolean wordStart) {
		return switch (mode) {
		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE -> Character.toLowerCase(c);
		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE -> Character.toUpperCase(c);
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE -> wordStart ? Character.toUpperCase(c) : c;
		default -> c;
		};
	}
}
