package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.builder.impl.BuilderGlyphHandler;
import net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * 整形済みテキストイベントの不変スライスです(M3b Phase 1 / C3)。
 *
 * <p>
 * 残余行の restyle が GlyphHandler へ配達する列を record で捕捉し、
 * replay で同一の呼び出し列を再現する。Phase 1 ではボックス側
 * (TextBlockBox の残余行)を oracle として使い、運搬体だけを
 * イベント列に置き換える — 捕捉と再生は構成的に同一なので挙動不変。
 * </p>
 */
public final class TextReplaySlice {
	private final List<TextReplayEvent> events;
	private final BidiReplayPrefix bidiReplayPrefix;

	/** consume-once: 再生済みなら true(P0 の ranges と同じ規約)。 */
	private boolean consumed;

	private TextReplaySlice(final List<TextReplayEvent> events, final BidiReplayPrefix bidiReplayPrefix) {
		this.events = events;
		this.bidiReplayPrefix = bidiReplayPrefix;
	}

	/** イベント数を返します。 */
	public int size() {
		return this.events.size();
	}

	/**
	 * producer が GlyphHandler へ配達する列を捕捉します(close まで)。
	 */
	public static TextReplaySlice record(final Consumer<GlyphHandler> producer) {
		return record(producer, BidiReplayPrefix.EMPTY);
	}

	/** 段落途中の再生では、既に配置済みの先行行も UBA 文脈として保持する。 */
	public static TextReplaySlice record(final Consumer<GlyphHandler> producer,
			final List<AbstractLineBox> bidiReplayPrefix) {
		return record(producer, BidiReplayPrefix.EMPTY.append(bidiReplayPrefix));
	}

	public static TextReplaySlice record(final Consumer<GlyphHandler> producer,
			final BidiReplayPrefix bidiReplayPrefix) {
		final List<TextReplayEvent> events = new ArrayList<>();
		producer.accept(new GlyphHandler() {
			public void startTextRun(final int charOffset, final FontStyle fontStyle,
					final FontMetrics fontMetrics) {
				events.add(new TextReplayEvent.RunStart(charOffset, fontStyle, fontMetrics));
			}

			public void endTextRun() {
				events.add(new TextReplayEvent.RunEnd());
			}

			public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
				final char[] chars = new char[clen];
				System.arraycopy(ch, coff, chars, 0, clen);
				events.add(new TextReplayEvent.Glyph(charOffset, chars, gid));
			}

			public void control(final TextControl control) {
				events.add(new TextReplayEvent.ControlQuad(control));
			}

			public void flush() {
				events.add(new TextReplayEvent.Flush());
			}

			public void close() {
				// 終端は replay 側の close で再現する
			}
		});
		return new TextReplaySlice(Collections.unmodifiableList(events), bidiReplayPrefix);
	}

	/**
	 * 捕捉した列を GlyphHandler へ再生し、close で終端します。
	 * consume-once — 二重再生は二重供給(グリフの重複)なので禁止。
	 * 非 quad の Control(WhiteSpace/Tab/LineBreak/SoftHyphen)は
	 * final フィールドのみの不変値のため参照運搬=値運搬。可変参照が
	 * 残るのは InlineQuad(インライン継続)のみで、これは OpenChain
	 * recipe(Phase 3c)で値化する。
	 */
	public void replay(final GlyphHandler gh) {
		if (this.consumed) {
			throw new IllegalStateException("TextReplaySlice は consume-once");
		}
		this.consumed = true;
		if (gh instanceof BuilderGlyphHandler builderGlyphHandler) {
			builderGlyphHandler.seedBidiReplayPrefix(this.bidiReplayPrefix);
		}
		for (final TextReplayEvent event : this.events) {
			switch (event) {
			case TextReplayEvent.RunStart(final int charOffset, final FontStyle fontStyle,
					final FontMetrics fontMetrics) -> gh.startTextRun(charOffset, fontStyle, fontMetrics);
			case TextReplayEvent.RunEnd() -> gh.endTextRun();
			case TextReplayEvent.Glyph(final int charOffset, final char[] chars, final int gid) ->
				gh.glyph(charOffset, chars, 0, (byte) chars.length, gid);
			case TextReplayEvent.ControlQuad(final TextControl quad) -> gh.control(quad);
			case TextReplayEvent.Flush() -> gh.flush();
			}
		}
		gh.close();
	}
}
