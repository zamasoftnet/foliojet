package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * An entry point for directly writing <b>a self-contained SVG</b> (B-1, user request on 2026-08-29).
 *
 * <p>
 * Uses exactly the same writers as page-split SVG ({@link DirectPagedSVGGC},
 * {@link SVGWriter}, and {@link WebFontSubset}). Only <b>resource delivery</b> differs:
 * fonts and images are embedded in the SVG as {@code data:}.
 * Single SVG is intended to be portable as one file, so it cannot retain external references.
 * </p>
 *
 * <p>
 * Only this class is public. The writers are implementation details
 * and are not exposed outside the package.
 * </p>
 *
 * <p>
 * <b>Limitation</b>: glyphs use private-use area (PUA) code points to output GIDs directly.
 * Appearance is exact, but copied text contains private-use characters.
 * The original text is in {@code aria-label} and {@code data-copper-text},
 * which support read-aloud and search.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SelfContainedSVGPage implements AutoCloseable {

	private final PagedSVGResources resources;

	private final SVGPageOutput page;

	private final DirectPagedSVGGC gc;

	public SelfContainedSVGPage(final Writer out, final double width, final double height,
			final FontManager fonts) throws IOException {
		// Resources are not emitted as results. Embedding does not call the emitter,
		// but calling it would be a design error, so fail instead of silently discarding.
		this.resources = new PagedSVGResources((uri, mimeType, bytes) -> {
			throw new IllegalStateException("自己完結SVGは資源を別に出しません: " + uri);
		});
		this.resources.setResourceMode(net.zamasoft.foliojet.ua.props.PagedSvgResourceMode.EMBED);
		this.page = new SVGPageOutput(out, width, height);
		this.gc = new DirectPagedSVGGC(this.page.writer(), fonts, this.resources,
				new PagedSVGResources.PageData(1, width, height));
	}

	public GC gc() {
		return this.gc;
	}

	/**
	 * Builds subsets, inserts them into {@code @font-face}, and closes the SVG.
	 *
	 * <p>
	 * Subsets can be built <b>only now</b>, once all glyphs used on the page are known.
	 * Because {@code defs} is at the end, the {@code src} finalized here can be written directly
	 * (see {@link SVGPageOutput}).
	 * </p>
	 */
	@Override
	public void close() throws IOException {
		final Map<String, String> sources = this.resources.inlineFontSources();
		this.page.writer().setFontSrc(uri -> sources.getOrDefault(uri, uri));
		this.page.close();
	}
}
