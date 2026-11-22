/*
 * Copyright 2007 - 2008 JEuclid, http://jeuclid.sf.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package net.sourceforge.jeuclid.font;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlgraphics.util.ClasspathResource;

import javax.annotation.concurrent.ThreadSafe;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete FontFactory implementation that does simple caching of Fonts
 * loaded via {@link Font#createFont(int, File)} APIs.
 *
 * @version $Revision$
 */
@ThreadSafe
public class DefaultFontFactory extends FontFactory {

	private static final int CACHE_FONT_SIZE = 12;

	private static final int NUM_STYLES = 4;

	/**
	 * Logger for this class
	 */
	private static final Log LOGGER = LogFactory
			.getLog(DefaultFontFactory.class);

	/** The most names kept in {@link #requestedFonts}. */
	private static final int MAX_REQUESTED_FONTS = 256;

	/**
	 * The fonts found at start-up and the registered fonts, by family name and font name. Each array is complete
	 * when it is put in the map; its style slots are read and written under the array's monitor.
	 */
	private final Map<String, Font[]> fontCache = new ConcurrentHashMap<String, Font[]>();

	/**
	 * Fonts made on demand for names that are neither installed nor registered. A document can ask for any name,
	 * and an unknown one still yields a (fallback) font, so these are kept apart from the permanent entries and
	 * bounded: the least recently used name goes first. Guarded by its own monitor.
	 */
	private final Map<String, Font[]> requestedFonts = new LinkedHashMap<String, Font[]>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, Font[]> eldest) {
			return this.size() > DefaultFontFactory.MAX_REQUESTED_FONTS;
		}
	};

	public DefaultFontFactory() {
	}

	private void autoloadFontsFromAWT() {
		final String[] fam = GraphicsEnvironment
				.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		for (final String element : fam) {
			for (int i = 0; i < DefaultFontFactory.NUM_STYLES; i++) {
				final Font f = new Font(element, i,
						DefaultFontFactory.CACHE_FONT_SIZE);
				this.cacheFont(f);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void autoloadFontsFromClasspath() {
		final List<URL> fonts = ClasspathResource.getInstance()
				.listResourcesOfMimeType("application/x-font");
		for (final URL u : fonts) {
			try {
				try {
					this.cacheFont(Font.createFont(Font.TRUETYPE_FONT, u
							.openStream()));
				} catch (final FontFormatException e) {
					try {
						this.cacheFont(Font.createFont(Font.TYPE1_FONT, u
								.openStream()));
					} catch (final FontFormatException e1) {
						LOGGER.warn(e.getMessage());
					}
				}
			} catch (final IOException e) {
				LOGGER.warn(e.getMessage());
			}
		}

	}

	private final Object fontLoadLock = new Object();
	/**
	 * Create a font object with specified properties. Font name may refer to
	 * either 'built-in' or loaded externally and 'cached' font.
	 *
	 * @param name  font name or font family name
	 * @param style font style
	 * @param size  font size
	 * @return Font instance
	 * @see java.awt.Font#Font(String, int, int)
	 */
	@Override
	public Font getFont(final String name, final int style, final float size) {
		synchronized (fontLoadLock) {
			if (this.fontCache.size() == 0) {
				/*
				 * We have *no* fonts yet, lets auto load all fonts we can find
				 */
				LOGGER.debug("No fonts registered yet, searching classpath and AWT for fonts");
				this.autoloadFontsFromAWT();
				this.autoloadFontsFromClasspath();
			}
		}
		final String key = name.toLowerCase(Locale.ENGLISH);
		Font[] fonts = this.fontCache.get(key);
		if (fonts == null) {
			fonts = this.requestedFont(key, name);
		}
		final Font font;
		synchronized (fonts) {
			if (fonts[style] == null) {
				fonts[style] = fonts[0].deriveFont(style, size);
				return fonts[style];
			}
			font = fonts[style];
		}
		return font.deriveFont(size);
	}

	/**
	 * Returns the fonts of a name that is neither installed nor registered, making the plain one on first use.
	 */
	private Font[] requestedFont(final String key, final String name) {
		synchronized (this.requestedFonts) {
			Font[] fonts = this.requestedFonts.get(key);
			if (fonts == null) {
				fonts = new Font[DefaultFontFactory.NUM_STYLES];
				fonts[0] = new Font(name, Font.PLAIN,
						DefaultFontFactory.CACHE_FONT_SIZE);
				this.requestedFonts.put(key, fonts);
			}
			return fonts;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Font getFont(final List<String> preferredFonts,
						final int codepoint, final int style, final float size) {
		Font font = this.searchFontList(preferredFonts, codepoint, style,
				size);
		if (font == null) {
			font = this.searchFontList(this.fontCache.keySet(), codepoint,
					style, size);
		}
		return font;
	}

	private Font searchFontList(final Collection<String> fontList,
								final int codepoint, final int style, final float size) {
		for (final String fontName : fontList) {
			final Font font = this.getFont(fontName, style, size);
			final String desiredFont = fontName.trim();
			if (((font.getFamily().equalsIgnoreCase(desiredFont)) || (font
					.getFontName().equalsIgnoreCase(desiredFont)))
					&& (font.canDisplay(codepoint))) {
				return font;
			}
		}
		return null;
	}

	/**
	 * Load an external font from a file and 'register' (aka 'cache') it for
	 * future use.
	 *
	 * @param format   font format (TTF or TYPE_1 currently supported by the
	 *                 platform)
	 * @param fontFile file which contains the font
	 * @return The newly created Font instance
	 * @throws FontFormatException if font contained in the file doesn't match the specified
	 *                             format
	 * @throws IOException         in case of problem while reading the file
	 * @see java.awt.Font#createFont(int, File)
	 */
	@Override
	public Font registerFont(final int format, final File fontFile)
			throws IOException, FontFormatException {

		return this.cacheFont(Font.createFont(format, fontFile));
	}

	/**
	 * Load an external font from a stream and 'register' (aka 'cache') it for
	 * future use.
	 *
	 * @param format     font format (TTF or TYPE_1 currently supported by the
	 *                   platform)
	 * @param fontStream file which contains the font
	 * @return The newly created Font instance
	 * @throws FontFormatException if font contained in the stream doesn't match the specified
	 *                             format
	 * @throws IOException         in case of problem while reading the stream
	 * @see java.awt.Font#createFont(int, InputStream)
	 */
	@Override
	public Font registerFont(final int format, final InputStream fontStream)
			throws IOException, FontFormatException {

		return this.cacheFont(Font.createFont(format, fontStream));
	}

	/**
	 * Actually stores a font in the cache. Uses font name and font family as
	 * keys.
	 *
	 * @param font Font instance to cache
	 * @return the font instance that was cached
	 */
	private Font cacheFont(final Font font) {
		final String family = font.getFamily().trim().toLowerCase(
				Locale.ENGLISH);
		final String fontname = font.getName().trim().toLowerCase(
				Locale.ENGLISH);
		int style = font.getStyle();
		if (fontname.contains("italic")) {
			style |= Font.ITALIC;
		}
		if (fontname.contains("oblique")) {
			style |= Font.ITALIC;
		}
		if (fontname.contains("bold")) {
			style |= Font.BOLD;
		}
		this.cacheFontWithStyle(font, family, style);
		this.cacheFontWithStyle(font, fontname, style);
		return font;
	}

	private void cacheFontWithStyle(final Font font, final String cacheName,
									final int style) {
		this.fontCache.compute(cacheName, (key, fonts) -> {
			if (fonts == null) {
				// Complete before the map shows it: another thread reads the fallback element as soon as it finds
				// the array
				final Font[] created = new Font[DefaultFontFactory.NUM_STYLES];
				created[0] = font;
				created[style] = font;
				return created;
			}
			synchronized (fonts) {
				fonts[style] = font;
			}
			return fonts;
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<String> listFontNames() {
		final Set<String> retVal;
		retVal = new HashSet<String>(this.fontCache.keySet());
		return retVal;
	}
}
