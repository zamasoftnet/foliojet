package net.zamasoft.foliojet.objects.media;

import java.net.URI;
import java.util.List;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * The replaced content of an HTML {@code <audio>} or {@code <video>} (2026-10-15, TECH-20261015-005).
 *
 * <p>
 * It draws the video's poster, or nothing: the box's own background and border are drawn by the replaced box, so an
 * audio element styled with a speaker icon keeps it. The media itself is not drawn; outputs that can play it take the
 * element's place and its sources from this image ({@code AbstractVisitor#addMedia}): the paged-svg page data for the
 * web reader, later PDF.
 * </p>
 *
 * <p>
 * Immutable, since a recorded layout shares it between passes ({@code ReplacedRecipe.freeze}).
 * </p>
 */
public final class MediaImage implements Image {
	/** One candidate file: the resolved URI and the {@code type} attribute (null if not given). */
	public record Source(URI uri, String type) {
	}

	public final String tag;
	public final List<Source> sources;
	public final boolean controls, autoplay, loop, muted;
	/** The accessible name ({@code aria-label}, {@code title} or the fallback text), or null. */
	public final String label;
	private final Image poster;
	private final double width, height;

	MediaImage(final String tag, final List<Source> sources, final boolean controls, final boolean autoplay,
			final boolean loop, final boolean muted, final String label, final Image poster, final double width,
			final double height) {
		this.tag = tag;
		this.sources = List.copyOf(sources);
		this.controls = controls;
		this.autoplay = autoplay;
		this.loop = loop;
		this.muted = muted;
		this.label = label;
		this.poster = poster;
		this.width = width;
		this.height = height;
	}

	/** The poster drawn in the element's place, or null. */
	public Image getPoster() {
		return this.poster;
	}

	public boolean isVideo() {
		return "video".equals(this.tag);
	}

	@Override
	public double getWidth() {
		return this.width;
	}

	@Override
	public double getHeight() {
		return this.height;
	}

	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		if (this.poster != null) {
			gc.drawImage(this.poster);
		}
	}

	@Override
	public String getAltString() {
		return this.label;
	}
}
