package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineFragmentView;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.text.LeaderQuad;
import net.zamasoft.pdfg2d.gc.text.GlyphAdvances;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.pipeline.Itemizer;
import net.zamasoft.pdfg2d.font.Font;

/**
 * 行分割後の論理 tree を段落で一度解決し、各行の描画専用 tree を作る。
 */
public final class BidiParagraphLayout {
	private static final AtomicLong NEXT_PARAGRAPH_ID = new AtomicLong();
	private static final int SIZE_CEILING = 1_000_000;

	private BidiParagraphLayout() {
	}

	/**
	 * container builder が所有する行・barrier の順序付き段落 queue。
	 * v1 の保持量は O(paragraph) とし、soft wrap・改ページでは閉じない。
	 */
	public static final class Session {
		private sealed interface OrderedEvent permits LineEvent, BarrierEvent {
		}

		private record LineEvent(TextBlockBox block, AbstractLineBox line) implements OrderedEvent {
		}

		private record BarrierEvent(Object payload) implements OrderedEvent {
		}

		private final List<OrderedEvent> events = new ArrayList<>();
		private BidiReplayPrefix replayPrefix = BidiReplayPrefix.EMPTY;
		private int estimatedChars;
		private boolean overCeiling;

		public void line(final TextBlockBox block, final AbstractLineBox line) {
			this.events.add(new LineEvent(block, line));
			this.estimatedChars += estimateChars(line.getLogicalContents());
			if (!this.overCeiling && this.estimatedChars > SIZE_CEILING) {
				this.overCeiling = true;
				// TODO WARN_BIDI_PARAGRAPH_TOO_LARGE を追加したらここで一度だけ通知する。
				// UBA を行単位に劣化させないため、現時点では段落全体の保持を続ける。
			}
		}

		public void barrier(final Object payload) {
			if (!this.events.isEmpty()) {
				final OrderedEvent last = this.events.get(this.events.size() - 1);
				if (last instanceof BarrierEvent barrier && barrier.payload() == payload) {
					return;
				}
			}
			this.events.add(new BarrierEvent(payload));
		}

		public boolean isEmpty() {
			for (final OrderedEvent event : this.events) {
				if (event instanceof LineEvent) {
					return false;
				}
			}
			return true;
		}

		/** 段落途中の replay より前にある、既に配置済みの論理行。 */
		public void replayPrefix(final BidiReplayPrefix prefix) {
			this.replayPrefix = prefix;
		}

		public void resolve(final BlockParams params) {
			this.resolveLines(params, params.bidiSemanticAlias);
			this.events.clear();
			this.replayPrefix = BidiReplayPrefix.EMPTY;
			this.estimatedChars = 0;
			this.overCeiling = false;
		}

		/**
		 * 改ページ直前の確定ページを描けるよう、queue を閉じずに現時点の行へ
		 * visual tree を付ける。後続断片が加わった段落終端では全行を解決し直す。
		 */
		public void preview(final BlockParams params) {
			this.resolveLines(params, params.bidiSemanticAlias);
		}

		private void resolveLines(final BlockParams params, final boolean semanticAlias) {
			final List<LayoutLine> lines = new ArrayList<>();
			final Map<AbstractLineBox, Boolean> seen = new IdentityHashMap<>();
			for (final AbstractLineBox line : this.replayPrefix.lines()) {
				if (seen.put(line, Boolean.TRUE) == null) {
					lines.add(new LayoutLine(line, false, 0));
				}
			}
			final Map<TextBlockBox, Map<AbstractLineBox, Boolean>> liveLines = new IdentityHashMap<>();
			for (final OrderedEvent event : this.events) {
				if (event instanceof LineEvent line) {
					final AbstractLineBox box = line.line();
					final Map<AbstractLineBox, Boolean> live = liveLines.computeIfAbsent(line.block(), block -> {
						final Map<AbstractLineBox, Boolean> members = new IdentityHashMap<>();
						block.forEachLine(value -> members.put(value, Boolean.TRUE));
						return members;
					});
					if (live.containsKey(box) && seen.put(box, Boolean.TRUE) == null) {
						lines.add(new LayoutLine(box, true, line.block().getLineSize()));
					}
				}
				// BarrierEvent は配置済みの外側イベントを越えないための順序標識。
				// 行分割は論理順で即時実行しているので、ここで外側副作用を再実行しない。
			}
			BidiParagraphLayout.resolve(lines, params, this.replayPrefix, semanticAlias);
		}
	}

	/** {@code lineSize} は fragment の margin/padding を未切断の frame から再計算するための行方向寸法。 */
	private record LayoutLine(AbstractLineBox line, boolean attach, double lineSize) {
	}

	private record PathEntry(InlineBox box, double verticalAlign) {
		Object key() {
			return this.box.getParams().element == null ? this.box.getInlineParams() : this.box.getParams().element;
		}
	}

	private enum AtomKind {
		TEXT, CONTROL, ATOMIC, BARRIER, EMPTY
	}

	private static final class Atom {
		final AtomKind kind;
		final Object visual;
		final List<PathEntry> path;
		final int logicalOrdinal;
		BidiParagraphBuffer.Event event;
		byte level;

		Atom(final AtomKind kind, final Object visual, final List<PathEntry> path, final int logicalOrdinal) {
			this.kind = kind;
			this.visual = visual;
			this.path = List.copyOf(path);
			this.logicalOrdinal = logicalOrdinal;
		}
	}

	private record LineAtoms(AbstractLineBox line, List<Atom> atoms, boolean attach, double lineSize) {
	}

	private record ParagraphBuffer(BidiParagraphBuffer buffer, int[] lineStarts, int[] lineLimits) {
	}

	private static final class FragmentContext {
		final InlineFragmentView view;
		final Object key;
		final FragmentContext parent;
		int firstOrdinal = Integer.MAX_VALUE;
		int lastOrdinal = Integer.MIN_VALUE;

		FragmentContext(final InlineFragmentView view, final Object key, final FragmentContext parent) {
			this.view = view;
			this.key = key;
			this.parent = parent;
		}
	}

	private static void resolve(final List<LayoutLine> lines, final BlockParams params,
			final BidiReplayPrefix replayPrefix, final boolean semanticAlias) {
		if (lines.isEmpty() || params.isVerticalTypesetting()) {
			// 通常の vertical-* は従来どおり bidi 対象外。sideways は水平組版なので通す。
			return;
		}
		final long replayParagraphId = replayPrefix.paragraphId();
		final long paragraphId = replayParagraphId == 0 ? NEXT_PARAGRAPH_ID.incrementAndGet() : replayParagraphId;
		List<LineAtoms> paragraphLines = flattenLines(lines, false);
		ParagraphBuffer paragraph = buildBuffer(paragraphLines, params);
		attachParagraphMetadata(paragraphLines, replayPrefix, paragraphId);
		if (paragraph.buffer().isEmpty() || !paragraph.buffer().requiresVisualReordering()) {
			// 純 LTR は論理 tree をそのまま描く。cluster 分割・L2・fragment 構築を行わない。
			return;
		}

		paragraphLines = flattenLines(lines, true);
		paragraph = buildBuffer(paragraphLines, params);
		final BidiParagraphBuffer buffer = paragraph.buffer();
		final int[] lineStarts = paragraph.lineStarts();
		final int[] lineLimits = paragraph.lineLimits();

		final byte paragraphLevel = (byte) buffer.paragraphLevel();
		final Map<Object, Integer> first = new IdentityHashMap<>();
		final Map<Object, Integer> last = new IdentityHashMap<>();
		for (final LineAtoms line : paragraphLines) {
			for (final Atom atom : line.atoms()) {
				for (final PathEntry entry : atom.path) {
					first.putIfAbsent(entry.key(), atom.logicalOrdinal);
					last.put(entry.key(), atom.logicalOrdinal);
				}
			}
		}
		final Map<Object, String> semanticTexts = collectSemanticTexts(paragraphLines);

		for (int li = 0; li < paragraphLines.size(); ++li) {
			final LineAtoms line = paragraphLines.get(li);
			if (line.atoms().isEmpty()) {
				continue;
			}
			final Bidi lineBidi = lineStarts[li] == lineLimits[li] ? null
					: buffer.lineBidi(lineStarts[li], lineLimits[li]);
			final byte[] levels = new byte[line.atoms().size()];
			for (int i = 0; i < levels.length; ++i) {
				final Atom atom = line.atoms().get(i);
				if (atom.event.length() == 0 || lineBidi == null) {
					atom.level = paragraphLevel;
				} else {
					final int index = Math.max(0, Math.min(lineBidi.getLength() - 1,
							atom.event.start() - lineStarts[li]));
					atom.level = (byte) lineBidi.getLevelAt(index);
				}
				levels[i] = atom.level;
				if ((atom.level & 1) != 0 && atom.visual instanceof TextImpl text) {
					mirrorGlyph(text, semanticAlias);
				}
			}
			final int[] order = Itemizer.reorderVisual(levels);
			final Map<Object, BidiSlice> slices = new IdentityHashMap<>();
			for (final Atom atom : line.atoms()) {
				final List<InlineBox> ancestry = new ArrayList<>(atom.path.size());
				for (final PathEntry entry : atom.path) {
					ancestry.add(entry.box());
				}
				if (atom.visual != null) {
					slices.put(atom.visual, new BidiSlice(paragraphId, atom.event.start(), atom.event.limit(),
							paragraphLevel, atom.level, ancestry));
				}
			}
			if (line.attach()) {
				final LogicalLineEmission logicalLine = line.line().prepareLogicalLineEmission();
				line.line().setVisualContents(buildVisual(line.atoms(), order, first, last, semanticTexts,
						line.lineSize(), logicalLine, line.line()::getLogicalLineVisualText, slices),
						slices);
			}
		}
	}

	private static List<LineAtoms> flattenLines(final List<LayoutLine> lines, final boolean splitClusters) {
		final List<LineAtoms> paragraphLines = new ArrayList<>();
		int ordinal = 0;
		for (final LayoutLine input : lines) {
			final List<Atom> atoms = new ArrayList<>();
			ordinal = flatten(input.line().getLogicalContents(), new ArrayList<>(), atoms, ordinal, splitClusters);
			paragraphLines.add(new LineAtoms(input.line(), atoms, input.attach(), input.lineSize()));
		}
		return paragraphLines;
	}

	private static ParagraphBuffer buildBuffer(final List<LineAtoms> paragraphLines, final BlockParams params) {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(params.direction, params.unicodeBidi);
		List<PathEntry> previousPath = List.of();
		final int[] lineStarts = new int[paragraphLines.size()];
		final int[] lineLimits = new int[paragraphLines.size()];
		for (int li = 0; li < paragraphLines.size(); ++li) {
			final LineAtoms line = paragraphLines.get(li);
			lineStarts[li] = buffer.length();
			for (final Atom atom : line.atoms()) {
				final int common = commonPrefix(previousPath, atom.path);
				for (int i = previousPath.size() - 1; i >= common; --i) {
					buffer.inlineEnd(previousPath.get(i).box());
				}
				for (int i = common; i < atom.path.size(); ++i) {
					final InlineBox inline = atom.path.get(i).box();
					final AbstractTextParams ip = inline.getTextParams();
					buffer.inlineStart(ip.direction, ip.unicodeBidi, inline);
				}
				previousPath = atom.path;
				atom.event = addAtom(buffer, atom);
			}
			lineLimits[li] = buffer.length();
		}
		for (int i = previousPath.size() - 1; i >= 0; --i) {
			buffer.inlineEnd(previousPath.get(i).box());
		}
		return new ParagraphBuffer(buffer, lineStarts, lineLimits);
	}

	private static void attachParagraphMetadata(final List<LineAtoms> lines, final BidiReplayPrefix replayPrefix,
			final long paragraphId) {
		for (final LineAtoms line : lines) {
			if (line.attach()) {
				line.line().setBidiReplayPrefix(replayPrefix);
				line.line().setBidiParagraphId(paragraphId);
			}
		}
	}

	/** ruby/warichu の内部を、外側とは独立した1行の小段落として並べ替える。 */
	public static TextImpl[] reorderAtomicRuns(final TextImpl[] source, final byte direction,
			final byte unicodeBidi, final boolean semanticAlias) {
		if (source.length == 0) {
			return source;
		}
		final BidiParagraphBuffer probe = new BidiParagraphBuffer(direction, unicodeBidi);
		for (final TextImpl text : source) {
			probe.addText(new String(text.getChars(), 0, text.getCharCount()), text);
		}
		if (!probe.requiresVisualReordering()) {
			return source;
		}
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(direction, unicodeBidi);
		final int start = buffer.length();
		final List<TextImpl> clusters = new ArrayList<>();
		final List<BidiParagraphBuffer.Event> events = new ArrayList<>();
		for (final TextImpl text : source) {
			int charStart = 0;
			for (int glyph = 0; glyph < text.getGlyphCount();) {
				final int glyphLimit = clusterGlyphLimit(text, glyph, charStart);
				final TextImpl cluster = copyCluster(text, glyph, glyphLimit, charStart);
				clusters.add(cluster);
				events.add(buffer.addText(new String(cluster.getChars(), 0, cluster.getCharCount()), cluster));
				charStart += cluster.getCharCount();
				glyph = glyphLimit;
			}
		}
		final int limit = buffer.length();
		if (start == limit) {
			return source;
		}
		final Bidi line = buffer.lineBidi(start, limit);
		final byte[] levels = new byte[clusters.size()];
		for (int i = 0; i < levels.length; ++i) {
			final BidiParagraphBuffer.Event event = events.get(i);
			levels[i] = event.length() == 0 ? (i == 0 ? (byte) line.getBaseLevel() : levels[i - 1])
					: (byte) line.getLevelAt(event.start() - start);
			if ((levels[i] & 1) != 0) {
				mirrorGlyph(clusters.get(i), semanticAlias);
			}
		}
		final int[] order = Itemizer.reorderVisual(levels);
		final TextImpl[] visual = new TextImpl[order.length];
		for (int i = 0; i < order.length; ++i) {
			visual[i] = clusters.get(order[i]);
		}
		return visual;
	}

	private static BidiParagraphBuffer.Event addAtom(final BidiParagraphBuffer buffer, final Atom atom) {
		return switch (atom.kind) {
		case TEXT -> {
			final Text text = (Text) atom.visual;
			yield buffer.addText(new String(text.getChars(), 0, text.getCharCount()), atom);
		}
		case CONTROL -> buffer.addText(String.valueOf(((Control) atom.visual).getControlChar()), atom);
		case ATOMIC -> {
			if (atom.visual instanceof AbstractTextBox.Inline inline
					&& inline.box instanceof AbstractReplacedBox
					&& inline.box.getParams() instanceof AbstractTextParams tp) {
				yield buffer.atomic(atom, tp.direction, tp.unicodeBidi);
			}
			yield buffer.atomic(atom);
		}
		case BARRIER, EMPTY -> buffer.barrier(atom);
		};
	}

	private static int flatten(final List<Object> contents, final List<PathEntry> path, final List<Atom> atoms,
			int ordinal, final boolean splitClusters) {
		for (final Object content : contents) {
			if (content instanceof Text text) {
				if (splitClusters) {
					int charStart = 0;
					for (int g = 0; g < text.getGlyphCount();) {
						final int glyphLimit = clusterGlyphLimit(text, g, charStart);
						final TextImpl cluster = copyCluster(text, g, glyphLimit, charStart);
						atoms.add(new Atom(AtomKind.TEXT, cluster, path, ordinal++));
						charStart += cluster.getCharCount();
						g = glyphLimit;
					}
				} else {
					atoms.add(new Atom(AtomKind.TEXT, text, path, ordinal++));
				}
			} else if (content instanceof Control control) {
				atoms.add(new Atom(AtomKind.CONTROL, control, path, ordinal++));
			} else if (content instanceof AbstractTextBox.Inline inline) {
				if (inline.box.getType() == BoxType.INLINE) {
					final List<PathEntry> nested = new ArrayList<>(path);
					nested.add(new PathEntry((InlineBox) inline.box, inline.verticalAlign));
					final int before = atoms.size();
					ordinal = flatten(((InlineBox) inline.box).getLogicalContents(), nested, atoms, ordinal,
							splitClusters);
					if (atoms.size() == before) {
						atoms.add(new Atom(AtomKind.EMPTY, null, nested, ordinal++));
					}
				} else {
					final AbstractTextBox.Inline copy = new AbstractTextBox.Inline(inline.box);
					copy.verticalAlign = inline.verticalAlign;
					atoms.add(new Atom(AtomKind.ATOMIC, copy, path, ordinal++));
				}
			} else if (content instanceof IAbsoluteBox absolute) {
				atoms.add(new Atom(AtomKind.BARRIER, absolute, path, ordinal++));
			} else if (content instanceof LeaderQuad leader) {
				atoms.add(new Atom(AtomKind.ATOMIC, leader, path, ordinal++));
			} else {
				throw new IllegalStateException(String.valueOf(content));
			}
		}
		return ordinal;
	}

	private static int clusterGlyphLimit(final Text source, final int glyphStart, final int charStart) {
		int glyphLimit = glyphStart + 1;
		int charLimit = charStart + Byte.toUnsignedInt(source.getClusterLengths()[glyphStart]);
		while (glyphLimit < source.getGlyphCount() && continuesCluster(source.getChars(), charLimit,
				source.getCharCount())) {
			charLimit += Byte.toUnsignedInt(source.getClusterLengths()[glyphLimit++]);
		}
		return glyphLimit;
	}

	private static boolean continuesCluster(final char[] chars, final int at, final int limit) {
		if (at <= 0 || at >= limit) {
			return false;
		}
		final int cp = Character.codePointAt(chars, at, limit);
		final int type = Character.getType(cp);
		if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
				|| type == Character.ENCLOSING_MARK || cp >= 0xFE00 && cp <= 0xFE0F
				|| cp >= 0xE0100 && cp <= 0xE01EF || cp >= 0x1F3FB && cp <= 0x1F3FF) {
			return true;
		}
		return cp == 0x200D || Character.codePointBefore(chars, at) == 0x200D;
	}

	private static TextImpl copyCluster(final Text source, final int glyphStart, final int glyphLimit,
			final int charStart) {
		final int charOffset = source.getCharOffset() < 0 ? source.getCharOffset() : source.getCharOffset() + charStart;
		final TextImpl copy = new TextImpl(charOffset, source.getFontStyle(), source.getFontMetrics());
		int chars = charStart;
		for (int glyph = glyphStart; glyph < glyphLimit; ++glyph) {
			final byte charLength = source.getClusterLengths()[glyph];
			copy.appendGlyph(source.getChars(), chars, charLength, source.getGlyphIds()[glyph]);
			chars += Byte.toUnsignedInt(charLength);
		}
		copy.setLetterSpacing(source.getLetterSpacing());
		if (glyphStart > 0) {
			copy.advance -= source.getFontMetrics().getKerning(source.getGlyphIds()[glyphStart - 1],
					source.getGlyphIds()[glyphStart]);
		}
		final GlyphAdvances advances = source.xAdvances();
		if (advances != null) {
			for (int glyph = glyphStart; glyph < glyphLimit; ++glyph) {
				if (advances.get(glyph) != 0) {
					copy.addXAdvance(glyph - glyphStart, advances.get(glyph));
				}
			}
		}
		if (source instanceof TextImpl text) {
			copy.materializedHyphen = text.materializedHyphen;
		}
		copy.pack();
		return copy;
	}

	/** L4: 論理文字を保ったまま、奇数 level の表示 GID だけを鏡像側へ差し替える。 */
	private static void mirrorGlyph(final TextImpl text, final boolean semanticAlias) {
		if (text.getGlyphCount() <= 0 || text.getCharCount() <= 0) {
			return;
		}
		final int codePoint = Character.codePointAt(text.getChars(), 0, text.getCharCount());
		if (!Character.isMirrored(codePoint) || !BidiMirroring.hasMirror(codePoint)) {
			return;
		}
		final int mirrored = mirroredCodePoint(codePoint);
		if (mirrored == codePoint || !text.getFontMetrics().getFontSource().canDisplay(mirrored)) {
			return;
		}
		// 整形に使った Font と同じ実体でなければならない。埋め込み CID フォントの別 wrapper は別の
		// subset 台帳を持つため、alias の CID が頁のフォント資源とは別の glyph を指してしまう
		// (2026-09-04 に実 PDF で発覚)
		if (!(text.getFontMetrics() instanceof net.zamasoft.pdfg2d.font.FontMetricsImpl metrics)) {
			return;
		}
		final Font font = metrics.getFont();
		final int gid = semanticAlias
				? font.toGID(mirrored, codePoint, text.getFontStyle().getFeatures())
				: font.toGID(mirrored, text.getFontStyle().getFeatures());
		if (gid == 0) {
			return;
		}
		final double oldGlyphAdvance = text.getFontMetrics().getAdvance(text.glyphIds[0]);
		text.glyphIds[0] = gid;
		text.advance += text.getFontMetrics().getAdvance(gid) - oldGlyphAdvance;
	}

	private static int mirroredCodePoint(final int codePoint) {
		return BidiMirroring.mirror(codePoint);
	}

	private static Map<Object, String> collectSemanticTexts(final List<LineAtoms> lines) {
		final Map<Object, StringBuilder> text = new IdentityHashMap<>();
		final Map<Object, Map<InlineBox, Boolean>> seen = new IdentityHashMap<>();
		for (final LineAtoms line : lines) {
			for (final Atom atom : line.atoms()) {
				for (final PathEntry entry : atom.path) {
					final Map<InlineBox, Boolean> sources = seen.computeIfAbsent(entry.key(),
							key -> new IdentityHashMap<>());
					if (sources.put(entry.box(), Boolean.TRUE) == null) {
						entry.box().getText(text.computeIfAbsent(entry.key(), key -> new StringBuilder()));
					}
				}
			}
		}
		final Map<Object, String> result = new IdentityHashMap<>();
		for (final Map.Entry<Object, StringBuilder> entry : text.entrySet()) {
			result.put(entry.getKey(), entry.getValue().toString());
		}
		return result;
	}

	private static List<Object> buildVisual(final List<Atom> atoms, final int[] order,
			final Map<Object, Integer> first, final Map<Object, Integer> last,
			final Map<Object, String> semanticTexts, final double lineSize,
			final LogicalLineEmission logicalLine, final java.util.function.Supplier<String> logicalLineVisualText,
			final Map<Object, BidiSlice> slices) {
		final List<Object> root = new ArrayList<>();
		final List<FragmentContext> stack = new ArrayList<>();
		List<PathEntry> currentPath = List.of();
		for (final int index : order) {
			final Atom atom = atoms.get(index);
			final int common = commonPrefix(currentPath, atom.path);
			closeFragments(stack, common, first, last, lineSize);
			for (int i = common; i < atom.path.size(); ++i) {
				final PathEntry entry = atom.path.get(i);
				final FragmentContext parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
				final InlineFragmentView view = new InlineFragmentView(entry.box(), semanticTexts.get(entry.key()),
						logicalLine, logicalLineVisualText, slices);
				final AbstractTextBox.Inline wrapper = new AbstractTextBox.Inline(view);
				wrapper.verticalAlign = entry.verticalAlign();
				if (parent == null) {
					root.add(wrapper);
				} else {
					parent.view.appendFragment(wrapper);
				}
				stack.add(new FragmentContext(view, entry.key(), parent));
			}
			currentPath = atom.path;
			for (final FragmentContext context : stack) {
				context.firstOrdinal = Math.min(context.firstOrdinal, atom.logicalOrdinal);
				context.lastOrdinal = Math.max(context.lastOrdinal, atom.logicalOrdinal);
			}
			if (atom.visual != null) {
				if (stack.isEmpty()) {
					root.add(atom.visual);
				} else {
					stack.get(stack.size() - 1).view.append(atom.visual);
				}
			}
		}
		closeFragments(stack, 0, first, last, lineSize);
		return root;
	}

	private static void closeFragments(final List<FragmentContext> stack, final int keep,
			final Map<Object, Integer> first, final Map<Object, Integer> last, final double lineSize) {
		while (stack.size() > keep) {
			final FragmentContext context = stack.remove(stack.size() - 1);
			final boolean keepStart = context.view.source().hasLineStartEdge()
					&& context.firstOrdinal == first.get(context.key).intValue();
			final boolean keepEnd = context.view.source().hasLineEndEdge()
					&& context.lastOrdinal == last.get(context.key).intValue();
			context.view.finishEdges(keepStart, keepEnd, lineSize);
			if (context.parent != null) {
				context.parent.view.addFragmentAdvance(context.view);
			}
		}
	}

	private static int commonPrefix(final List<PathEntry> a, final List<PathEntry> b) {
		final int limit = Math.min(a.size(), b.size());
		int i = 0;
		while (i < limit && a.get(i).key() == b.get(i).key()) {
			++i;
		}
		return i;
	}

	private static int estimateChars(final List<Object> contents) {
		int count = 0;
		for (final Object content : contents) {
			if (content instanceof Text text) {
				count += text.getCharCount();
			} else if (content instanceof AbstractTextBox.Inline inline && inline.box instanceof InlineBox box) {
				count += estimateChars(box.getLogicalContents());
			} else {
				++count;
			}
		}
		return count;
	}
}
