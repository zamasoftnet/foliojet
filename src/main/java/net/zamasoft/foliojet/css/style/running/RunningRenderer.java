package net.zamasoft.foliojet.css.style.running;

import java.awt.geom.AffineTransform;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.layout.segment.SegmentExecutor;
import net.zamasoft.foliojet.layout.visitor.ArtifactVisitor;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.PageAssignmentState.Presence;
import net.zamasoft.foliojet.ua.UserAgent;

/** 確定頁のマージンボックスへrunningを組版します。所有する作業状態の寿命は一頁です。 */
public final class RunningRenderer {
	private final UserAgent ua;
	private final TemplateExpander expander;
	private final Set<RunningTemplate> active = Collections.newSetFromMap(new IdentityHashMap<RunningTemplate, Boolean>());

	public RunningRenderer(final UserAgent ua, final PageValueSnapshot page) {
		this.ua = ua;
		this.expander = new TemplateExpander(ua, page);
	}

	/** 解決済みのイベントです。測定と本配置は毎回新品のDocumentBuilderを使います。 */
	public final class Content {
		private final List<SegmentEvent> events;

		private Content(final List<SegmentEvent> events) {
			this.events = events;
		}

		public PageBox layout(final BlockParams params, final double width, final double height) {
			final MeasurePageGenerator generator = new MeasurePageGenerator(ua, params, width, height);
			final DocumentBuilder doc = new DocumentBuilder(generator);
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
			doc.startBox(new FlowBlockBox(params, new FlowPos()));
			new SegmentExecutor(doc, SegmentExecutor.AnchorMode.NONE).drive(this.events);
			doc.endBox();
			doc.end();
			return generator.getLastPage();
		}
	}

	/** 値のある参照だけを展開します。同名の複数参照は個別に再生できます。 */
	public Content prepare(final ElementFunctionValue reference, final CSSStyle container) {
		final var value = this.ua.getPassContext().getRunningState().resolve(reference.name(), reference.mode());
		if (value.presence() != Presence.VALUE) {
			return null;
		}
		return this.prepare(value.value(), container, false);
	}

	/** 解決済みテンプレートを展開します。fixedは頁固定層への配置にだけ使います。 */
	public Content prepare(final RunningTemplate template, final CSSStyle container, final boolean fixed) {
		if (!this.active.add(template)) {
			this.warn("recursive element(" + template.name() + ")");
			return null;
		}
		try {
			return new Content(this.expander.expand(template, container, fixed));
		} catch (final IllegalArgumentException e) {
			this.warn("element(" + template.name() + "): " + e.getMessage());
			return null;
		} finally {
			this.active.remove(template);
		}
	}

	private void warn(final String message) {
		this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX,
				String.valueOf(this.ua.getDocumentContext().getBaseURI()), "running: " + message);
	}

	/** 背景・罫線と内容をartifactとして描きます。visitから文書への登録は行いません。 */
	public static void draw(final PageBox mini, final Drawer drawer, final double x, final double y) {
		mini.setReplayOrigin(x, y);
		final Drawer artifact = drawer.artifactView();
		mini.frames(mini, artifact, null, new AffineTransform(), x, y);
		mini.draw(mini, artifact, ArtifactVisitor.INSTANCE, null, new AffineTransform(), x, y, x, y);
	}
}
