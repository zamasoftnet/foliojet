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

/** Lays out running content in a finalized page's margin boxes. Owned working state lives for one page. */
public final class RunningRenderer {
	private final UserAgent ua;
	private final TemplateExpander expander;
	private final Set<RunningTemplate> active = Collections.newSetFromMap(new IdentityHashMap<RunningTemplate, Boolean>());

	public RunningRenderer(final UserAgent ua, final PageValueSnapshot page) {
		this.ua = ua;
		this.expander = new TemplateExpander(ua, page);
	}

	/** Resolved events. Measurement and actual placement each use a fresh DocumentBuilder. */
	public final class Content {
		private final List<SegmentEvent> events;

		private Content(final List<SegmentEvent> events) {
			this.events = events;
		}

		public PageBox layout(final BlockParams params, final double width, final double height) {
			final MeasurePageGenerator generator = new MeasurePageGenerator(ua, params, width, height, null, false);
			final DocumentBuilder doc = new DocumentBuilder(generator);
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
			doc.startBox(new FlowBlockBox(params, new FlowPos()));
			new SegmentExecutor(doc, SegmentExecutor.AnchorMode.NONE).drive(this.events);
			doc.endBox();
			doc.end();
			return generator.getLastPage();
		}
	}

	/** Expands only references with values. Multiple references to the same name can be replayed separately. */
	public Content prepare(final ElementFunctionValue reference, final CSSStyle container) {
		final var value = this.ua.getPassContext().getRunningState().resolve(reference.name(), reference.mode());
		if (value.presence() != Presence.VALUE) {
			return null;
		}
		return this.prepare(value.value(), container);
	}

	/** Expands a resolved template. */
	public Content prepare(final RunningTemplate template, final CSSStyle container) {
		if (!this.active.add(template)) {
			this.warn("recursive element(" + template.name() + ")");
			return null;
		}
		try {
			return new Content(this.expander.expand(template, container));
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

	/** Draws background/borders and content as artifacts. Does not register with the document from visit. */
	public static void draw(final PageBox mini, final Drawer drawer, final double x, final double y) {
		mini.setReplayOrigin(x, y);
		final Drawer artifact = drawer.artifactView();
		mini.frames(mini, artifact, null, new AffineTransform(), x, y);
		mini.draw(mini, artifact, ArtifactVisitor.INSTANCE, null, new AffineTransform(), x, y, x, y);
	}
}
