package net.zamasoft.foliojet.css.style.running;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJPageContent;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.RunningPositionValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;

/** 部分木の捕捉状態です。深さは通常のスタイル状態機械と独立に管理します。 */
public final class RunningCapture {
	/** legacyも捕捉し、確定頁のpageContents層で再生します。 */
	static final boolean CAPTURE_LEGACY = true;

	public static final int MAX_EVENTS = 10_000;
	public static final int MAX_TEXT_BYTES = 100 * 1024;
	public static final int MAX_IMAGE_REFERENCES = 50;

	private static final class Frame {
		final String name;
		final long order;
		final byte pages;
		final boolean legacy;
		final List<RunningTemplate.Event> events = new ArrayList<RunningTemplate.Event>();
		int depth;
		int textBytes;
		int images;
		boolean rejected;

		Frame(final String name, final long order, final byte pages, final boolean legacy) {
			this.name = name;
			this.order = order;
			this.pages = pages;
			this.legacy = legacy;
		}
	}

	private final Deque<Frame> frames = new ArrayDeque<Frame>();
	private final Deque<CSSStyle> styles = new ArrayDeque<CSSStyle>();
	private final StyleSnapshot.Copier copier = new StyleSnapshot.Copier();
	private final UserAgent ua;
	private final StyleContext context;
	private final RunningRegistry registry;
	private final Consumer<Long> token;
	private final Runnable invalidElementContent;

	public RunningCapture(final UserAgent ua, final StyleContext context, final Consumer<Long> token,
			final Runnable invalidElementContent) {
		this.ua = ua;
		this.context = context;
		this.registry = ua.getPassContext().getRunningRegistry();
		this.token = token;
		this.invalidElementContent = invalidElementContent;
	}

	public boolean isCapturing() {
		return !this.frames.isEmpty();
	}

	/** 入力側の親スタイル解決専用です。完成テンプレートには参照を渡しません。 */
	public CSSStyle currentStyle() {
		return this.styles.peek();
	}

	/** runningの根または捕捉中の子ならtrueを返し、通常処理を迂回させます。 */
	public boolean start(final CSSStyle style) {
		final CSSElement ce = style.getCSSElement();
		if ((ce == CSSElement.FOOTNOTE_CALL || ce == CSSElement.FOOTNOTE_MARKER)
				&& style.get(CSSPosition.INFO) instanceof RunningPositionValue) {
			// callの箱は脚注の所属頁を決めるアンカー。捕捉の入口で適用対象外にする。
			this.ua.message(MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION, "position: running()",
					"running() is not applicable to ::footnote-call/::footnote-marker");
			style.set(CSSPosition.INFO, net.zamasoft.foliojet.css.value.PositionValue.STATIC_VALUE,
					CSSStyle.MODE_IMPORTANT);
		}
		final String name = style.get(CSSPosition.INFO) instanceof RunningPositionValue running
				? running.name() : (CAPTURE_LEGACY ? CSSJPageContent.getName(style) : null);
		if (name != null && !(style.get(CSSPosition.INFO) instanceof RunningPositionValue)) {
			style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
		}
		if (this.frames.isEmpty() && (name == null || Display.get(style) == DisplayValue.NONE)) {
			return false;
		}
		if (name != null) {
			final String[] clears = net.zamasoft.foliojet.css.impl.property.ext.CSSJPageContentClear.get(style);
			if (clears.length != 0) {
				final long clearOrder = this.registry.nextOrder();
				this.registry.clear(clearOrder, List.of(clears));
				this.token.accept(clearOrder);
			}
			final long order = this.registry.nextOrder();
			if (!this.frames.isEmpty()) {
				this.add(new RunningTemplate.Token(name, order));
			}
			this.frames.push(new Frame(name, order, CSSJPageContent.getPages(style),
					!(style.get(CSSPosition.INFO) instanceof RunningPositionValue)));
			// 全ての入れ子を同じ原位置の非描画アンカーへ渡す。
			this.token.accept(order);
		}
		this.styles.push(style);
		++this.frames.peek().depth;
		if (this.hasElementContent(style)) {
			style.set(Content.INFO, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
		}
		this.snapshot(style, ce == CSSElement.BEFORE ? "before" : ce == CSSElement.AFTER ? "after"
				: ce == CSSElement.FIRST_LETTER ? "first-letter" : null);
		this.pseudo(style, CSSElement.BEFORE);
		return true;
	}

	public void characters(final char[] chars, final int offset, final int length) {
		final Frame frame = this.frames.peek();
		if (frame.rejected) {
			return;
		}
		if ((long) frame.textBytes + (long) length * 2 > MAX_TEXT_BYTES) {
			this.reject("text bytes");
			return;
		}
		frame.textBytes += length * 2;
		this.add(new RunningTemplate.Text(new String(chars, offset, length)));
	}

	public void end() {
		final CSSStyle style = this.styles.peek();
		this.pseudo(style, CSSElement.AFTER);
		this.styles.pop();
		this.add(new RunningTemplate.End());
		final Frame frame = this.frames.peek();
		if (--frame.depth != 0) {
			return;
		}
		this.frames.pop();
		if (!frame.rejected) {
			this.registry.complete(frame.order, new RunningTemplate(frame.name, frame.pages, frame.legacy,
					frame.events, frame.textBytes, frame.images));
		} else {
			this.registry.reject(frame.order);
		}
	}

	private void pseudo(final CSSStyle parent, final CSSElement ce) {
		if (parent.getCSSElement().isPseudoElement() || this.frames.peek().rejected) {
			return;
		}
		this.context.startElement(ce);
		try {
			final Declaration declaration = this.context.merge(null);
			final boolean br = ce == CSSElement.AFTER
					&& net.zamasoft.foliojet.xml.vocab.XHTML.BR_ELEM.equalsElement(parent.getCSSElement());
			final boolean html = ce == CSSElement.BEFORE ? HTMLStyle.hasBeforeContent(parent.getCSSElement())
					: HTMLStyle.hasAfterContent(parent.getCSSElement());
			if (declaration == null && !html && !br) {
				return;
			}
			final CSSStyle style = CSSStyle.getCSSStyle(this.ua, parent, ce);
			if (ce == CSSElement.BEFORE) {
				HTMLStyle.applyBeforeStyle(style);
			} else {
				HTMLStyle.applyAfterStyle(style);
			}
			if (br) {
				// 通常のHTML経路と同じ改行を、未評価contentのまま保存する。
				style.set(Content.INFO, new net.zamasoft.foliojet.css.value.ValueListValue(
						new net.zamasoft.foliojet.css.value.Value[] { new net.zamasoft.foliojet.css.value.StringValue("\n") }));
				style.set(net.zamasoft.foliojet.css.impl.property.box.Clear.INFO,
						net.zamasoft.foliojet.css.value.KeywordValue.INHERIT);
			}
			if (declaration != null) {
				declaration.applyProperties(style);
			}
			if (Display.get(style) != DisplayValue.NONE && !this.hasElementContent(style)) {
				// 通常の子と同じ入口を通し、running疑似要素も独立登録する。
				this.start(style);
				this.end();
			}
		} finally {
			this.context.endElement();
		}
	}

	private void snapshot(final CSSStyle style, final String pseudo) {
		final Frame frame = this.frames.peek();
		if (frame.rejected) {
			return;
		}
		try {
			final StyleSnapshot snapshot = this.copier.capture(style, MAX_TEXT_BYTES - frame.textBytes);
			if ((long) frame.textBytes + snapshot.textBytes() > MAX_TEXT_BYTES) {
				this.reject("text bytes");
				return;
			}
			frame.textBytes += (int) snapshot.textBytes();
			frame.images += snapshot.imageUris().size() + (snapshot.svgSource() == null ? 0 : 1);
			if (frame.images > MAX_IMAGE_REFERENCES) {
				this.reject("image references");
				return;
			}
			this.add(new RunningTemplate.Start(snapshot, pseudo));
		} catch (final IllegalArgumentException e) {
			this.reject(e.getMessage());
		}
	}

	private boolean hasElementContent(final CSSStyle style) {
		final Value[] contents = Content.get(style);
		if (contents != null) {
			for (final Value value : contents) {
				if (value instanceof ElementFunctionValue) {
					this.invalidElementContent.run();
					return true;
				}
			}
		}
		return false;
	}

	private void add(final RunningTemplate.Event event) {
		final Frame frame = this.frames.peek();
		if (frame.rejected) {
			return;
		}
		if (frame.events.size() == MAX_EVENTS) {
			this.reject("events");
			return;
		}
		if (event instanceof RunningTemplate.Token token) {
			final long bytes = (long) token.name().length() * 2;
			if (bytes > MAX_TEXT_BYTES - frame.textBytes) {
				this.reject("text bytes");
				return;
			}
			frame.textBytes += (int) bytes;
		}
		frame.events.add(event);
	}

	private void reject(final String reason) {
		final Frame frame = this.frames.peek();
		if (!frame.rejected) {
			frame.rejected = true;
			frame.events.clear();
			this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX,
					String.valueOf(this.ua.getDocumentContext().getBaseURI()),
					"running(" + frame.name + "): template rejected (" + reason + ")");
		}
	}
}
