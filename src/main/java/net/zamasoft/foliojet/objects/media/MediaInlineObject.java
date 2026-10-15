package net.zamasoft.foliojet.objects.media;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import net.zamasoft.foliojet.css.InlineObject;
import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.util.LengthUtils;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Reads an {@code <audio>} or {@code <video>} element and its {@code <source>} children into a {@link MediaImage}
 * (2026-10-15). CSSProcessor hands the whole element over, so its fallback text is not laid out, as in a browser.
 *
 * <p>
 * As in HTML, a {@code src} attribute on the element is its only source; otherwise the {@code <source>} children are
 * the candidates in document order. The intrinsic size is the poster's, else HTML's default for video (300x150 px) or
 * the size of a browser's audio controls (300x54 px); {@code width}/{@code height} attributes and CSS override it as for
 * images. The video's poster moved here from {@code HTMLStyle} so that it is read once.
 * </p>
 */
final class MediaInlineObject extends DefaultHandler implements InlineObject {
	private static final double VIDEO_WIDTH = 300, VIDEO_HEIGHT = 150, AUDIO_WIDTH = 300, AUDIO_HEIGHT = 54;

	private String tag;
	private String src, poster, ariaLabel, title;
	private boolean controls, autoplay, loop, muted;
	private final List<String[]> sourceElements = new ArrayList<>();
	private final StringBuilder fallback = new StringBuilder();
	private int depth = 0;
	/** Depth inside a source or track element, whose content is not fallback text. */
	private int skipDepth = 0;

	@Override
	public void startElement(final String uri, final String lName, final String qName, final Attributes atts) {
		++this.depth;
		final String name = lName.toLowerCase(Locale.ROOT);
		if (this.depth == 1) {
			this.tag = name;
			this.src = nonEmpty(atts.getValue("src"));
			this.poster = "video".equals(name) ? nonEmpty(atts.getValue("poster")) : null;
			this.controls = atts.getValue("controls") != null;
			this.autoplay = atts.getValue("autoplay") != null;
			this.loop = atts.getValue("loop") != null;
			this.muted = atts.getValue("muted") != null;
			this.ariaLabel = nonEmpty(atts.getValue("aria-label"));
			this.title = nonEmpty(atts.getValue("title"));
			return;
		}
		if (this.skipDepth > 0) {
			++this.skipDepth;
			return;
		}
		if ("source".equals(name) || "track".equals(name)) {
			this.skipDepth = 1;
			if ("source".equals(name) && this.depth == 2) {
				final String source = nonEmpty(atts.getValue("src"));
				if (source != null) {
					this.sourceElements.add(new String[] { source, nonEmpty(atts.getValue("type")) });
				}
			}
		}
	}

	@Override
	public void endElement(final String uri, final String lName, final String qName) {
		if (this.skipDepth > 0) {
			--this.skipDepth;
		}
		--this.depth;
	}

	@Override
	public void characters(final char[] ch, final int start, final int length) {
		if (this.skipDepth == 0) {
			this.fallback.append(ch, start, length);
		}
	}

	@Override
	public Image getImage(final UserAgent ua) throws IOException {
		if (this.tag == null) {
			return null;
		}
		final DocumentContext context = ua.getDocumentContext();
		final List<MediaImage.Source> sources = new ArrayList<>();
		if (this.src != null) {
			addSource(sources, context, this.src, null);
		} else {
			for (final String[] source : this.sourceElements) {
				addSource(sources, context, source[0], source[1]);
			}
		}
		Image posterImage = null;
		if (this.poster != null) {
			final String text = this.poster;
			posterImage = ImageLoadDiagnostics.loadImage(ua, text,
					() -> HTMLStyle.imageURI(context.getEncoding(), context.getBaseURI(), text), null, true);
		}
		final boolean video = "video".equals(this.tag);
		final double width = posterImage != null ? posterImage.getWidth()
				: LengthUtils.convert(ua, video ? VIDEO_WIDTH : AUDIO_WIDTH, Unit.PX, Unit.PT);
		final double height = posterImage != null ? posterImage.getHeight()
				: LengthUtils.convert(ua, video ? VIDEO_HEIGHT : AUDIO_HEIGHT, Unit.PX, Unit.PT);
		String label = this.ariaLabel != null ? this.ariaLabel : this.title;
		if (label == null) {
			label = nonEmpty(this.fallback.toString().replaceAll("\\s+", " ").trim());
		}
		return new MediaImage(this.tag, sources, this.controls, this.autoplay, this.loop, this.muted, label,
				posterImage, width, height);
	}

	private static void addSource(final List<MediaImage.Source> sources, final DocumentContext context,
			final String src, final String type) {
		try {
			final URI uri = HTMLStyle.imageURI(context.getEncoding(), context.getBaseURI(), src);
			if (uri != null) {
				sources.add(new MediaImage.Source(uri, type));
			}
		} catch (final URISyntaxException e) {
			// An unreadable address is no candidate, as in a browser.
		}
	}

	private static String nonEmpty(final String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
