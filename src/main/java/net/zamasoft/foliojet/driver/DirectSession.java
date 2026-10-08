package net.zamasoft.foliojet.driver;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.AbstractCTISession;
import jp.cssj.cti2.helpers.CTIMessageCodes;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.progress.ProgressListener;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException;
import net.zamasoft.foliojet.layout.RetainedTextLimitException;
import net.zamasoft.foliojet.FolioJetVersion;
import net.zamasoft.foliojet.formatter.Formatter;
import net.zamasoft.foliojet.formatter.MultiDocumentFormatter;
import net.zamasoft.foliojet.ua.MultiDocumentOutput;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.UserAgentFactory;
import net.zamasoft.foliojet.ua.UserAgentFactory.Type;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.foliojet.xml.filter.XSLTUtils;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.protocol.file.FileSource;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.zstream.resolver.protocol.stream.StreamSource;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceManager;
import net.zamasoft.pdfg2d.pdf.font.ConfigurablePDFFontSourceManager;
import net.zamasoft.pdfg2d.pdf.font.PDFFontSource;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.input.TeeInputStream;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import net.zamasoft.foliojet.ua.PrepareMode;

/**
 * @author MIYABE Tatsuhiko
 */
public class DirectSession extends AbstractCTISession
		implements CTISession, MessageHandler, net.zamasoft.foliojet.message.MessageHandler {
	private static final Logger LOG = Logger.getLogger(DirectSession.class.getName());

	private static final Set<String> SPECIAL_PROPERTIES = Set.of(UAProps.INPUT_INCLUDE, UAProps.INPUT_EXCLUDE);

	/** The URI for version information. */
	private static final URI VERSION_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/version");

	/** The URI for output format information. */
	private static final URI OUTPUT_TYPES_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/output-types");

	/** The URI for available font information. */
	private static final URI FONTS_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/fonts");

	/**
	 * <b>A lightweight font list</b> (B-4, 2026-08-29). {@link #FONTS_INFO_URI} lists
	 * aliases for every font face, making it too heavy for a font-selection UI
	 * (over 4 MB with the bundled fonts). This list groups entries by each <b>name users
	 * can specify in {@code font-family}</b> (primary names and aliases),
	 * returning combined weights, italics, font classifications, and supported scripts.
	 */
	private static final URI FONT_FAMILIES_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/fonts/families");

	private static final int PIPE_BUFFER_SIZE = 64 * 1024;

	private Results results = null;

	private ProgressListener progressListener = null;

	private MessageHandler messageHandler = CTIMessageHelper.NULL;

	private Map<String, String> props = new HashMap<String, String>();

	private final MySourceResolver resolver = new MySourceResolver();

	private Thread pipeThread = null;

	private PipedOutputStream pipeOut = null;

	private IOException pipeException = null;

	/** The current document conversion's deadline on the monotonic clock. Zero means unlimited. */
	private volatile long processingDeadlineNanos;

	private File profileFile;

	private static final Map<File, FontSourceManager> FONT_CACHE = Collections
			.synchronizedMap(new LRUCache<File, FontSourceManager>(32));

	/**
	 * The conversion UA. Its type states that it accepts a result set ({@link RandomResultUserAgent})
	 * (2026-09-02). Previously stored as {@code UserAgent} and cast at every use.
	 */
	private RandomResultUserAgent ua;

	/**
	 * Retains Paged SVG font subsets for the next conversion in the session
	 * (2026-08-29). Since the UA is recreated for each conversion, this keeps them alive.
	 */
	private net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry pagedSvgFontCarry = new net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry();

	private boolean continuous = false;

	private boolean aborted = false;

	/**
	 * The abort type passed to {@link #abort(byte)} ({@link AbortException#ABORT_NORMAL}
	 * or {@code ABORT_FORCE}). Retains it to report exceptions raised after abort closes down I/O
	 * as an abort instead of an I/O error (2026-09-21).
	 */
	private byte abortMode = 0;

	private boolean decodeMessage = true;

	private boolean middlePath = false;

	public DirectSession() {
		// ignore
	}

	private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
		private static final long serialVersionUID = 0;

		private final int maxEntries;

		LRUCache(int maxEntries) {
			super(maxEntries + 1, 0.75f, true);
			this.maxEntries = maxEntries;
		}

		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			return this.size() > this.maxEntries;
		}
	}

	public InputStream getServerInfo(URI uri) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			TransformerHandler handler = XSLTUtils.createIdentityTransformerHandler();
			handler.setResult(new StreamResult(out));
			Transformer tr = handler.getTransformer();
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			AttributesImpl atts = new AttributesImpl();
			if (uri.equals(VERSION_INFO_URI)) {
				// Version information.
				handler.startDocument();
				handler.startElement("", "version", "version", atts);
				{
					handler.startElement("", "long-version", "long-version", atts);
					String data = FolioJetVersion.INSTANCE.longVersion;
					handler.characters(data.toCharArray(), 0, data.length());
					handler.endElement("", "long-version", "long-version");
				}
				{
					handler.startElement("", "name", "name", atts);
					String data = FolioJetVersion.INSTANCE.name;
					handler.characters(data.toCharArray(), 0, data.length());
					handler.endElement("", "name", "name");
				}
				{
					handler.startElement("", "number", "number", atts);
					String data = FolioJetVersion.INSTANCE.version;
					handler.characters(data.toCharArray(), 0, data.length());
					handler.endElement("", "number", "number");
				}
				{
					handler.startElement("", "build", "build", atts);
					String data = FolioJetVersion.INSTANCE.build;
					handler.characters(data.toCharArray(), 0, data.length());
					handler.endElement("", "build", "build");
				}
				{
					handler.startElement("", "copyrights", "copyrights", atts);
					String data = FolioJetVersion.INSTANCE.copyrights;
					handler.characters(data.toCharArray(), 0, data.length());
					handler.endElement("", "copyrights", "copyrights");
				}
				{
					handler.startElement("", "credits", "credits", atts);
					String data = FolioJetVersion.INSTANCE.credits;
					handler.characters(data.toCharArray(), 0, data.length());
					handler.endElement("", "credits", "credits");
				}
				handler.endElement("", "version", "version");
				handler.endDocument();
			} else if (uri.equals(OUTPUT_TYPES_INFO_URI)) {
				// Output formats.
				handler.startDocument();
				handler.startElement("", "output-types", "output-types", atts);
				for (UserAgentFactory uaf : PluginRegistry.getInstance().plugins(UserAgentFactory.class)) {
					for (Iterator<?> j = uaf.types(); j.hasNext();) {
						Type type = (Type) j.next();
						atts.addAttribute("", "name", "name", "CDATA", type.name);
						atts.addAttribute("", "mimeType", "mimeType", "CDATA", type.mimeType);
						atts.addAttribute("", "suffix", "suffix", "CDATA", type.suffix);
						handler.startElement("", "type", "type", atts);
						atts.clear();
						handler.endElement("", "type", "type");
					}
				}
				handler.endElement("", "output-types", "output-types");
				handler.endDocument();
			} else if (uri.equals(FONT_FAMILIES_INFO_URI)) {
				// Fonts (lightweight list grouped by family).
				this.writeFontFamilies(handler, atts);
			} else if (uri.equals(FONTS_INFO_URI)) {
				// Fonts.
				final FontSourceManager fsm = this.getFontSourceManager();
				FontSource[] fonts = fsm.lookup(null);

				handler.startDocument();
				handler.startElement("", "fonts", "fonts", atts);
				for (int i = 0; i < fonts.length; ++i) {
					FontSource font = fonts[i];
					atts.addAttribute("", "name", "name", "CDATA", font.getFontName());
					if (font.isItalic()) {
						atts.addAttribute("", "italic", "italic", "CDATA", "true");
					}
					atts.addAttribute("", "weight", "weight", "CDATA", String.valueOf(font.getWeight()));
					if (font instanceof PDFFontSource) {
						String typeStr = switch (((PDFFontSource) font).getType()) {
						case MISSING -> "missing";
						case EMBEDDED -> "embedded";
						case CORE -> "core";
						case CID_KEYED -> "cid-keyed";
						case CID_IDENTITY -> "cid-identity";
						};
						atts.addAttribute("", "type", "type", "CDATA", String.valueOf(typeStr));
					}

					String directionStr = switch (font.getDirection()) {
					case LTR -> "ltr";
					case RTL -> "rtl";
					case TB -> "tb";
					};
					atts.addAttribute("", "direction", "direction", "CDATA", String.valueOf(directionStr));

					// Font classification and supported scripts (2026-08-27). Used by the webapp font list's
					// font-type and language filters. Classification is inferred from Panose/IBM
					// familyClass and is omitted for fonts without that information.
					final String generic = genericOf(font);
					if (generic != null) {
						atts.addAttribute("", "generic", "generic", "CDATA", generic);
					}
					final String scripts = scriptsOf(font);
					if (!scripts.isEmpty()) {
						atts.addAttribute("", "scripts", "scripts", "CDATA", scripts);
					}

					handler.startElement("", "font", "font", atts);
					atts.clear();

					String[] aliases = font.getAliases();
					for (int j = 0; j < aliases.length; ++j) {
						atts.addAttribute("", "name", "name", "CDATA", aliases[j]);
						handler.startElement("", "alias", "alias", atts);
						atts.clear();
						handler.endElement("", "alias", "alias");
					}

					handler.endElement("", "font", "font");
				}
				handler.endElement("", "fonts", "fonts");
				handler.endDocument();
			}
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		} catch (SAXException e) {
			throw new RuntimeException(e);
		}
		return new ByteArrayInputStream(out.toByteArray());
	}

	/**
	 * Writes the font list grouped by family (B-4, 2026-08-29).
	 *
	 * <p>
	 * Groups by <b>names users can specify in {@code font-family}</b>: both primary names
	 * and aliases. Grouping by aliases follows CSS matching, directly expressing how
	 * {@code font-family: Arial} matches Helvetica. Weights are in ascending order;
	 * italics indicates whether the name has an italic face; font classifications and
	 * supported scripts are merged.
	 * </p>
	 */
	private void writeFontFamilies(final TransformerHandler handler, final AttributesImpl atts)
			throws IOException, SAXException {
		// Do not pretty-print the lightweight list: a newline and indent for every attribute
		// makes whitespace larger than the data (measured: 4.4 MB → 1.9 MB → 0.5 MB).
		handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
		final FontSourceManager fsm = this.getFontSourceManager();
		final FontSource[] fonts = fsm.lookup(null);
		record Family(java.util.TreeSet<Short> weights, boolean[] italic, java.util.TreeSet<String> scripts,
				String[] generic, int[] faces) {
		}
		final Map<String, Family> families = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (final FontSource font : fonts) {
			// Group by **aliases**: an alias is the family name ("Noto Sans JP"),
			// whereas the primary name identifies a face ("NotoSansJP-Bold"). Including primary names
			// as keys prevents effective grouping (measured: 14,959 faces shrank to only 13,738 entries).
			// Use primary names only for fonts without aliases.
			final java.util.List<String> names = new java.util.ArrayList<>();
			for (final String alias : font.getAliases()) {
				names.add(alias);
			}
			if (names.isEmpty()) {
				names.add(font.getFontName());
			}
			final String generic = genericOf(font);
			final String scripts = scriptsOf(font);
			for (final String name : names) {
				if (name == null || name.isEmpty()) {
					continue;
				}
				if (name.indexOf(';') >= 0) {
					// Version strings from broken name tables ("0.000;NONE;…").
					// They cannot be specified in CSS font-family (the declaration ends midway),
					// so omit them from the selection UI list. Keep them in the raw list.
					continue;
				}
				final Family family = families.computeIfAbsent(name,
						k -> new Family(new java.util.TreeSet<>(), new boolean[1], new java.util.TreeSet<>(),
								new String[1], new int[1]));
				family.weights().add(font.getWeight().w);
				family.italic()[0] |= font.isItalic();
				++family.faces()[0];
				if (generic != null && family.generic()[0] == null) {
					family.generic()[0] = generic;
				}
				if (!scripts.isEmpty()) {
					// scriptsOf is space-separated. Remove duplicates when grouping.
					for (final String script : scripts.split("\s+")) {
						if (!script.isEmpty()) {
							family.scripts().add(script);
						}
					}
				}
			}
		}
		handler.startDocument();
		handler.startElement("", "font-families", "font-families", atts);
		for (final Map.Entry<String, Family> e : families.entrySet()) {
			final Family family = e.getValue();
			atts.addAttribute("", "name", "name", "CDATA", e.getKey());
			final StringBuilder weights = new StringBuilder();
			for (final Short w : family.weights()) {
				if (weights.length() != 0) {
					weights.append(' ');
				}
				weights.append(w);
			}
			atts.addAttribute("", "weights", "weights", "CDATA", weights.toString());
			if (family.italic()[0]) {
				atts.addAttribute("", "italic", "italic", "CDATA", "true");
			}
			if (family.generic()[0] != null) {
				atts.addAttribute("", "generic", "generic", "CDATA", family.generic()[0]);
			}
			if (!family.scripts().isEmpty()) {
				atts.addAttribute("", "scripts", "scripts", "CDATA", String.join(" ", family.scripts()));
			}
			atts.addAttribute("", "faces", "faces", "CDATA", String.valueOf(family.faces()[0]));
			handler.startElement("", "family", "family", atts);
			atts.clear();
			handler.endElement("", "family", "family");
		}
		handler.endElement("", "font-families", "font-families");
		handler.endDocument();
	}

	/**
	 * A script-detection table based on representative code points (the scripts attribute of ctip/fonts,
	 * 2026-08-27). Labels identify writing systems, not languages. Uses only cmap queries,
	 * requiring just a few dozen lookups per font.
	 *
	 * <p>
	 * Lists several characters per script and reports support **only when all can be displayed**
	 * (2026-09-01). With a single-character check, fonts supporting only part of a script
	 * claimed support, but actual layout produced missing characters or mixed fonts.
	 * Gothic A1 had Ω but not ά (accented), and JejuGothic had empty kanji glyphs, including 漢.
	 * The selected characters are common in each writing system.
	 * </p>
	 */
	private static final Object[][] SCRIPT_PROBES = { //
			{ "latin", new int[] { 0x41, 0x7A, 0x30 } }, // A z 0
			{ "cyrillic", new int[] { 0x416, 0x449, 0x451, 0x44A } }, // Ж щ ё ъ
			{ "greek", new int[] { 0x3A9, 0x3BC, 0x3AC } }, // Ω μ ά
			{ "arabic", new int[] { 0x627, 0x628, 0x647 } }, // ا ب ه
			{ "hebrew", new int[] { 0x5D0, 0x5DE, 0x5E0 } }, // א מ נ
			{ "devanagari", new int[] { 0x905, 0x913, 0x918 } }, // अ ओ घ
			{ "thai", new int[] { 0xE01, 0xE40, 0xE47 } }, // ก เ ็
			{ "kana", new int[] { 0x3042, 0x306C, 0x30F3 } }, // あ ぬ ン
			{ "cjk", new int[] { 0x6F22, 0x6C38, 0x6771 } }, // 漢 永 東
			{ "hangul", new int[] { 0xAC00, 0xC950, 0xD034 } }, // 가 쥐 퀴
	};

	private static String scriptsOf(final FontSource font) {
		final StringBuilder sb = new StringBuilder();
		for (final Object[] probe : SCRIPT_PROBES) {
			boolean all = true;
			for (final int c : (int[]) probe[1]) {
				if (!font.canDisplay(c)) {
					all = false;
					break;
				}
			}
			if (all) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append((String) probe[0]);
			}
		}
		return sb.toString();
	}

	/**
	 * Infers font classification from Panose (familyType/serifStyle/proportion) and OS/2's
	 * IBM familyClass (the generic attribute of ctip/fonts, 2026-08-27).
	 * Returns null for fonts with neither specified (both zero).
	 */
	private static String genericOf(final FontSource font) {
		net.zamasoft.pdfg2d.gc.font.Panose p = null;
		if (font instanceof net.zamasoft.pdfg2d.pdf.font.cid.CIDFontSource cid) {
			p = cid.getPanose();
		} else if (font instanceof net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource ot) {
			p = ot.getPanose();
		}
		if (p == null) {
			return null;
		}
		final int ibm = p.familyClassId() & 0xFF;
		final int familyType = p.familyType() & 0xFF;
		if (p.proportion() == 9) {
			return "monospace";
		}
		if (familyType == 3 || ibm == 10) {
			return "cursive";
		}
		if (familyType == 4 || ibm == 9) {
			return "fantasy";
		}
		if (familyType == 5 || ibm == 12) {
			return "symbol";
		}
		if (familyType == 2) {
			final int serif = p.serifStyle() & 0xFF;
			if (serif >= 11 && serif <= 15) {
				return "sans-serif";
			}
			if (serif >= 2 && serif <= 10) {
				return "serif";
			}
			// For serifStyle 0/1 (any/no fit), defer to IBM familyClass.
		}
		if (ibm == 8) {
			return "sans-serif";
		}
		if (ibm >= 1 && ibm <= 7) {
			return "serif";
		}
		return null;
	}

	public File getProfileFile() {
		if (this.profileFile == null) {
			this.profileFile = DirectDriver.getProfileFile(null);
		}
		return this.profileFile;
	}

	public void setProfileFile(File profileFile) {
		this.profileFile = profileFile;
	}

	public void message(short code, String... args) {
		this.message(code, args, null);
	}

	public void message(short code, String[] args, String mes) {
		if (this.messageHandler != null) {
			if (this.decodeMessage && mes == null) {
				mes = MessageCodeUtils.toString(code, args);
			}
			this.messageHandler.message(code, args, mes);
		}
	}

	public void setDecodeMessage(boolean decodeMessage) {
		this.decodeMessage = decodeMessage;
	}

	public void setResults(Results results) {
		assert results != null;
		this.results = results;
	}

	public void setUserAgent(UserAgent ua) {
		assert ua != null;
		this.ua = asResultUserAgent(ua);
		this.applyOperatorLimits(this.ua);
	}

	private static RandomResultUserAgent asResultUserAgent(final UserAgent ua) {
		if (ua instanceof RandomResultUserAgent results) {
			return results;
		}
		throw new IllegalArgumentException("the user agent must accept results (RandomResultUserAgent): "
				+ ua.getClass().getName());
	}

	public void setProgressListener(ProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	public void setMessageHandler(MessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public void property(String name, String value) throws IOException {
		if (name != null && name.startsWith(SERVER_PROPERTY_PREFIX)) {
			// **Do not let clients change server-side settings** (design review on 2026-09-02).
			// system.fonts reads any file as font configuration via a path relative to the profile
			// and even writes an index (.db) beside it, so allowing requests to set it
			// would provide an access path. Load profile settings through
			// profileProperty().
			this.message(MessageCodes.WARN_CANNOT_OVERRIDE_PROPERTY, new String[] { name });
			return;
		}
		if (this.operatorLimits.loosens(name, value)) {
			// Accept the value as is and apply the limit when reading it (OperatorLimits).
			this.message(MessageCodes.WARN_OPERATOR_LIMIT,
					new String[] { name, String.valueOf(this.operatorLimits.ceilings().get(name)), value });
		}
		this.profileProperty(name, value);
	}

	/** The prefix for server-side settings that requests must not set. */
	private static final String SERVER_PROPERTY_PREFIX = "system.";

	/** Settings from the profile (server-side configuration file). Also accepts {@code system.*}. */
	private void profileProperty(String name, String value) {
		if (SPECIAL_PROPERTIES.contains(name)) {
			this.specialProperty(name, value);
		} else {
			if (value == null || value.length() == 0) {
				this.props.remove(name);
			} else {
				this.props.put(name, value);
			}
		}
	}

	public void setContinuous(boolean continuous) {
		this.continuous = continuous;
	}

	private void specialProperty(String name, String value) {
		if (name.equals(UAProps.INPUT_INCLUDE)) {
			// URI encoding is fixed to UTF-8.
			try {
				URI uri = URIHelper.create("UTF-8", value);
				this.resolver.include(uri);
			} catch (URISyntaxException e) {
				this.message(MessageCodes.WARN_BAD_URI_PATTERN, new String[] { value });
			}
		} else if (name.equals(UAProps.INPUT_EXCLUDE)) {
			try {
				URI uri = URIHelper.create("UTF-8", value);
				this.resolver.exclude(uri);
			} catch (URISyntaxException e) {
				this.message(MessageCodes.WARN_BAD_URI_PATTERN, new String[] { value });
			}
		}
	}

	public OutputStream resource(final SourceMetadata metaSource) throws IOException {
		File file = this.resolver.putFile(metaSource);
		OutputStream out = new FileOutputStream(file);
		return out;
	}

	public void resource(final Source source) throws IOException {
		try (OutputStream out = this.resource((SourceMetadata) source); InputStream in = source.getInputStream()) {
			in.transferTo(out);
		}
	}

	public void setSourceResolver(SourceResolver resolver) {
		this.resolver.setUserResolver(resolver);
	}

	/**
	 * Determines whether this session may retrieve local resources (such as {@code file:}).
	 *
	 * <p>
	 * <b>This is not an I/O property.</b> Clients cannot change it; the server (daemon)
	 * decides for each authenticated user. Defaults to allowing access, so embedded use
	 * and command-line behavior stay the same: the principals running them can already
	 * read files accessible to the process.
	 * </p>
	 *
	 * <p>
	 * Allowing remote users access lets them specify arbitrary local files for conversion
	 * and read server configuration, keys, and {@code /proc}. Do not allow it on public servers.
	 * Clients can still send resources themselves (CTIP resource upload and on-demand transfer)
	 * regardless of this setting.
	 * </p>
	 */
	public void setLocalAccessAllowed(boolean allowed) {
		this.resolver.setLocalAccessAllowed(allowed);
	}

	public void transcode(URI uri) throws IOException, TranscoderException {
		this.prepareTranscode(uri);
		final Source source;
		try {
			source = this.resolveMainDocument(uri);
		} catch (final TranscoderException e) {
			// Discard the prepared UA. Keeping it made the next conversion skip prepareTranscode and run without resetting
			// default properties, retrieval permissions, or the abort flag (2026-10-04).
			this.discardUserAgent();
			throw e;
		}
		this.transcodeResolved(uri, source);
	}

	/** Retrieves the main document. If retrieval fails, aborts with a message for the user. */
	private Source resolveMainDocument(final URI uri) throws TranscoderException {
		try {
			return this.resolver.resolve(uri, true);
		} catch (SecurityException e) {
			// Denied a remote user access to a server-internal destination, etc. (MySourceResolver). Previously, RuntimeException
			// escaped through the CTIP server and disconnected the client, which received only "EOF within CTIP response"
			// (2026-09-14, TECH-20260911-008). Turn it into an abort with a message.
			throw this.serverSideDocumentError(MessageCodes.ERROR_FORBIDDEN_SERVERSIDE_DOCUMENT, uri, e);
		} catch (FileNotFoundException e) {
			throw this.serverSideDocumentError(MessageCodes.ERROR_MISSING_SERVERSIDE_DOCUMENT, uri, null);
		} catch (IOException e) {
			throw this.serverSideDocumentError(MessageCodes.ERROR_UNREACHABLE_SERVERSIDE_DOCUMENT, uri, e);
		}
	}

	private void transcodeResolved(final URI uri, final Source source) throws IOException, TranscoderException {
		// Close the stream opened in advance for progress notifications ourselves (2026-08-27).
		// resolver.release(source) only returns the Source object; it does not close
		// the actual stream opened by getInputStream(). Leaving it open kept
		// the OS file lock in place even after local file conversion finished
		// (in practice, file: HTML converted in the webapp could no longer be deleted).
		InputStream progressIn = null;
		final List<InputStream> progressStreams = new ArrayList<>();
		try {
			Source xsource = source;
			if (this.progressListener != null) {
				try {
					long srcLength = source.getLength();
					if (srcLength != -1) {
						this.progressListener.sourceLength(srcLength);
					}
					if (source.isFile()) {
						// **Preserve the ability to reread files** (2026-09-02). Previously,
						// rewrapping in StreamSource to count progress constrained subsequent
						// getInputStream() calls to an 8 KiB mark, so image main documents
						// failed when reread after peeking at their beginning
						// (cti.li report, 2026-09-01). Reopening a file allows reading from
						// the beginning, so wrap each opened stream to count progress.
						final ProgressListener listener = this.progressListener;
						xsource = new net.zamasoft.zstream.resolver.util.SourceWrapper(source) {
							@Override
							public InputStream getInputStream() throws IOException {
								final InputStream in = new BufferedInputStream(
										new ProgressInputStream(super.getInputStream(), listener));
								synchronized (progressStreams) {
									progressStreams.add(in);
								}
								return in;
							}
						};
					} else {
						InputStream in = source.getInputStream();
						in = new BufferedInputStream(new ProgressInputStream(in, this.progressListener));
						progressIn = in;
						xsource = new StreamSource(uri, in, source.getMimeType(), source.getEncoding());
					}
				} catch (FileNotFoundException e) {
					throw this.serverSideDocumentError(MessageCodes.ERROR_MISSING_SERVERSIDE_DOCUMENT, uri, null);
				} catch (IOException e) {
					// Connection refusal, disconnection, etc. Previously became FileNotFoundException and reported "not found".
					throw this.serverSideDocumentError(MessageCodes.ERROR_UNREACHABLE_SERVERSIDE_DOCUMENT, uri, e);
				}
			}
			this.transcode(xsource);
		} finally {
			if (progressIn != null) {
				try {
					progressIn.close();
				} catch (IOException e) {
					// A close failure does not affect the conversion result.
				}
			}
			// Close reopened streams too. Leaks leave file locks on Windows.
			synchronized (progressStreams) {
				for (final InputStream in : progressStreams) {
					try {
						in.close();
					} catch (IOException e) {
						// Same as above.
					}
				}
			}
			this.resolver.release(source);
		}
	}

	/**
	 * Reports a message, then creates an abort (STATE_BROKEN) when the server cannot retrieve the main document.
	 * A code without a cause (3806) takes one argument; codes with a cause (3810/3811) take the reason as the second.
	 */
	private TranscoderException serverSideDocumentError(final short code, final URI uri, final Exception cause) {
		final String[] args = cause == null ? new String[] { uri.toString() }
				: new String[] { uri.toString(), String.valueOf(cause.getMessage()) };
		this.message(code, args);
		final TranscoderException te = new TranscoderException(TranscoderException.STATE_BROKEN, code, args,
				MessageCodeUtils.toString(code, args));
		if (cause != null) {
			te.initCause(cause);
		}
		return te;
	}

	public OutputStream transcode(final SourceMetadata metaSource) throws IOException {
		this.prepareTranscode(metaSource.getURI());
		PipedOutputStream out = new PipedOutputStream() {
			public void close() throws IOException {
				super.close();
				DirectSession.this.flush();
			}
		};
		// Use the caller's body type (HTML if absent). Until 2026-10-04, passed the output type (application/pdf, image/png),
		// reading XHTML/Markdown/image bodies as HTML and, for image output, trying to read the body as an image.
		final String inputType = metaSource.getMimeType() == null ? "text/html" : metaSource.getMimeType();
		final PipedInputStream in = new PipedInputStream(out, PIPE_BUFFER_SIZE);
		this.pipeOut = out;
		this.pipeException = null;
		this.pipeThread = Thread.ofVirtual().name("foliojet-direct-session").start(() -> {
			try {
				InputStream xin = in;
				if (DirectSession.this.progressListener != null) {
					if (metaSource.getLength() != -1L) {
						DirectSession.this.progressListener.sourceLength(metaSource.getLength());
					}
					xin = new BufferedInputStream(new ProgressInputStream(in, DirectSession.this.progressListener));
				}
				Source source = new StreamSource(metaSource.getURI(), xin, inputType, metaSource.getEncoding());
				DirectSession.this.transcode(source);
			} catch (IOException e) {
				DirectSession.this.pipeException = e;
			} catch (RuntimeException e) {
				// Pass unchecked exceptions to flush() as failures too (until 2026-10-04, they died with the thread
				// and success was reported).
				DirectSession.this.pipeException = new IOException(e);
			}
		});
		return out;
	}

	protected FontSourceManager getFontSourceManager() throws IOException {
		final File dir = this.getProfileFile().getParentFile();
		String systemFonts = (String) this.props.get("system.fonts");
		if (systemFonts == null) {
			systemFonts = "fonts/fonts.xml";
		}
		final File fontSource = new File(dir, systemFonts).getCanonicalFile();
		FontSourceManager fsm;
		synchronized (FONT_CACHE) {
			fsm = FONT_CACHE.get(fontSource);
			if (fsm == null) {
				fsm = new ConfigurablePDFFontSourceManager(new FileSource(fontSource), fontIndexFile(dir, systemFonts));
				FONT_CACHE.put(fontSource, fsm);
			}
		}
		return fsm;
	}

	/**
	 * The font index ({@code fonts-print.xml.db}) location. Defaults to beside the configuration file,
	 * but {@code jp.cssj.font.index.dir} places it in that directory (2026-08-29).
	 * Containers mounting fonts read-only could not write beside the configuration file,
	 * so the index could not be saved and all fonts were reread on every startup.
	 */
	static File fontIndexFile(final File profileDir, final String systemFonts) throws IOException {
		final String indexDir = System.getProperty("jp.cssj.font.index.dir");
		if (indexDir == null || indexDir.isEmpty()) {
			return new File(profileDir, systemFonts + ".db");
		}
		final File dir = new File(indexDir);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("cannot create the font index directory: " + dir);
		}
		// The configuration filename alone could collide between fonts/fonts-print.xml and another configuration,
		// so use the relative path (the system.fonts value) as the name, with separators flattened.
		return new File(dir, systemFonts.replace('/', '-') + ".db");
	}

	public void transcode(Source source) throws IOException, TranscoderException {
		URI uri = source.getURI();
		this.prepareTranscode(uri);

		// UA setup.
		this.ua.setSourceResolver(this.resolver);
		this.ua.setMessageHandler(this);
		this.ua.setProperties(this.props);
		this.ua.getUAContext().setPagedSvgFontCarry(this.pagedSvgFontCarry);
		// Numbers the conversions into one output (setContinuous), for destination names that do not collide
		this.ua.getUAContext().nextConversion();

		final FontSourceManager fsm = this.getFontSourceManager();
		this.ua.getUAContext().setFontSourceManager(fsm);

		// Reread limits and reset the high-water mark for every conversion, even in continuous mode.
		this.ua.getRetainedTextLimit().reset();
		// Run conversion. Share the deadline across all passes;
		// do not reset it for each pass.
		final long timeLimitMillis = UAProps.PROCESSING_TIME_LIMIT.getLong(this.ua);
		if (timeLimitMillis > 0) {
			final long now = System.nanoTime();
			final long duration = timeLimitMillis > Long.MAX_VALUE / 1_000_000L
					? Long.MAX_VALUE : timeLimitMillis * 1_000_000L;
			this.processingDeadlineNanos = duration == Long.MAX_VALUE || now > Long.MAX_VALUE - duration
					? Long.MAX_VALUE : now + duration;
		} else {
			this.processingDeadlineNanos = 0;
		}
		try {
			this.format(source);
			if (!this.continuous) {
				this.ua.finish();
			}
		} catch (AbortException e) {
			// Abort.
			this.continuous = false;
			short code = CTIMessageCodes.INFO_ABORT;
			String mes = MessageCodeUtils.toString(code, null);
			if (e.getState() == ABORT_NORMAL) {
				try {
					this.ua.finish();
				} catch (BrokenResultException e1) {
					throw new TranscoderException(TranscoderException.STATE_BROKEN, code, null, mes);
				}
			} else {
				throw new TranscoderException(TranscoderException.STATE_BROKEN, code, null, mes);
			}
		} catch (TranscoderException e) {
			this.continuous = false;
			final RetainedTextLimitException retained = RetainedTextLimitException.findIn(e);
			if (retained != null) throw failure(retained.getCode(), retained.getMessage(), e);
			if (ContinuationInvariantViolationException.findIn(e) != null
					|| net.zamasoft.foliojet.layout.FootnoteProbeException.findIn(e) != null) {
				throw failure(e.getCode(), e.getMessage(), e);
			}
			// Abort.
			if (e.getState() == TranscoderException.STATE_READABLE) {
				try {
					this.ua.finish();
				} catch (BrokenResultException e1) {
					throw new TranscoderException(TranscoderException.STATE_BROKEN, e.getCode(), e.getArgs(),
							e.getMessage());
				}
				return;
			}
			throw e;
		} catch (FileNotFoundException e) {
			this.continuous = false;
			short code = MessageCodes.ERROR_MISSING_SERVERSIDE_DOCUMENT;
			String[] args = new String[] { uri.toString() };
			this.message(code, args);
			throw new TranscoderException(TranscoderException.STATE_BROKEN, code, args,
					MessageCodeUtils.toString(code, args));
		} catch (Throwable t) {
			this.continuous = false;
			final RetainedTextLimitException retained = RetainedTextLimitException.findIn(t);
			if (retained != null) throw failure(retained.getCode(), retained.getMessage(), t);
			// Return the original code for coded failures wrapped by the drawing layer (e.g., configuration errors already reported).
			// (Until 2026-10-05, PDF/X output-intent errors and PDF/UA language errors also became 4001.)
			final TranscoderException coded = codedCause(t);
			if (coded != null) throw failure(coded.getCode(), coded.getMessage(), t);
			this.ua.message(CTIMessageCodes.FATAL_UNEXPECTED, t.getMessage());
			LOG.log(Level.SEVERE, "予期しないエラー", t);
			short code = CTIMessageCodes.FATAL_UNEXPECTED;
			String mes = MessageCodeUtils.toString(code, new String[] { t.getMessage() });
			if (ContinuationInvariantViolationException.findIn(t) == null
					&& net.zamasoft.foliojet.layout.FootnoteProbeException.findIn(t) == null
					&& !UAProps.PROCESSING_FAIL_ON_FATAL_ERROR.getBoolean(this.ua)) {
				try {
					this.ua.finish();
				} catch (BrokenResultException e1) {
					throw failure(code, mes, t);
				}
				return;
			}
			// **Chain the cause** (2026-08-02): it was logged, but the exception passed
			// to the caller lacked its cause, collapsing sweep classifications into
			// a single "TranscoderException@DirectSession.transcode" category.
			// This made it impossible to count the distinct remaining defects.
			throw failure(code, mes, t);
		} finally {
			this.processingDeadlineNanos = 0;
			this.discardUserAgent();
		}
	}

	/** Cleans up and detaches the UA unless this is continuous conversion. */
	private void discardUserAgent() {
		if (!this.continuous && this.ua != null) {
			this.ua.dispose();
			this.ua = null;
		}
	}

	/** A coded exception in the cause chain whose code is not unexpected failure (4001). */
	private static TranscoderException codedCause(final Throwable thrown) {
		for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof TranscoderException te && te.getCode() != CTIMessageCodes.FATAL_UNEXPECTED) {
				return te;
			}
		}
		return null;
	}

	/** Wraps an unexpected failure while preserving its cause. */
	private static TranscoderException failure(final short code, final String mes, final Throwable cause) {
		final RetainedTextLimitException retained = RetainedTextLimitException.findIn(cause);
		final TranscoderException e = new TranscoderException(TranscoderException.STATE_BROKEN, code,
				retained == null ? null : retained.getArgs(), mes);
		e.initCause(cause);
		return e;
	}

	private void prepareDefaultProperties() throws IOException {
		File profileFile = this.getProfileFile();
		Properties defaultProperties = new Properties();
		try (InputStream in = new FileInputStream(profileFile)) {
			defaultProperties.load(in);
		} catch (IOException e) {
			this.message(MessageCodes.WARN_MISSING_PROFILE, new String[] { String.valueOf(profileFile) });
		}
		for (Iterator<?> i = defaultProperties.entrySet().iterator(); i.hasNext();) {
			Entry<?, ?> e = (Entry<?, ?>) i.next();
			String name = (String) e.getKey();
			String value = (String) e.getValue();
			// Do not overwrite values already set by the CTI client with defaults
			// from a profile read later. In particular, when the profile contained
			// a font policy, output.pdf.fonts.policy=outlines was ignored.
			// include/exclude are cumulative, so append them as before.
			if (SPECIAL_PROPERTIES.contains(name) || !this.props.containsKey(name)) {
				this.profileProperty(name, value);
			}
		}
	}

	private void prepareTranscode(URI uri) throws IOException, TranscoderException {
		if (this.ua != null) {
			return;
		}
		if (this.results == null) {
			throw new IllegalStateException("Resultsが設定されていません。");
		}
		this.prepareDefaultProperties();

		// include/exclude
		for (int i = 0;; ++i) {
			String exName = UAProps.INPUT_EXCLUDE + "." + i;
			String inName = UAProps.INPUT_INCLUDE + "." + i;
			String exValue = (String) this.props.get(exName);
			String inValue = (String) this.props.get(inName);
			if (exValue == null && inValue == null) {
				break;
			}
			if (exValue != null) {
				this.specialProperty(UAProps.INPUT_EXCLUDE, exValue);
			}
			if (inValue != null) {
				this.specialProperty(UAProps.INPUT_INCLUDE, inValue);
			}
		}

		this.resolver.setup(uri, this.operatorLimits.clampAll(this.props), this);
		this.aborted = false;
		this.abortMode = 0;

		final String outputType = UAProps.OUTPUT_TYPE.getString(this.props);
		UserAgentFactory factory = PluginRegistry.getInstance().search(UserAgentFactory.class,
				outputType);
		if (factory != null) {
			this.ua = asResultUserAgent(factory.createUserAgent());
			this.applyOperatorLimits(this.ua);
		} else {
			// Reject configuration errors while leaving the session usable (until 2026-10-05, failed with an unexpected exception).
			final short code = MessageCodes.ERROR_UNSUPPORTED_OUTPUT_TYPE;
			final String[] args = { outputType };
			this.message(code, args);
			throw new TranscoderException(TranscoderException.STATE_READABLE, code, args,
					MessageCodeUtils.toString(code, args));
		}
	}

	protected void flush() throws IOException, TranscoderException {
		Thread thread = this.pipeThread;
		if (thread != null) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				InterruptedIOException ioe = new InterruptedIOException("Interrupted while waiting for transcode pipe");
				ioe.initCause(e);
				throw ioe;
			} finally {
				this.pipeThread = null;
				this.pipeOut = null;
			}
			try {
				if (this.pipeException != null) {
					throw this.pipeException;
				}
			} finally {
				this.pipeException = null;
			}
		}
	}

	public void join() throws IOException {
		this.middlePath = false;
		if (this.ua == null) {
			return;
		}
		try {
			this.ua.finish();
		} catch (BrokenResultException e) {
			short code = CTIMessageCodes.FATAL_UNEXPECTED;
			String mes = MessageCodeUtils.toString(code, new String[] { e.getMessage() });
			// Chain the cause (discarded until 2026-10-04).
			throw failure(code, mes, e);
		} finally {
			this.ua.dispose();
			this.ua = null;
		}
	}

	public void abort(byte mode) throws IOException {
		if (!this.aborted && this.ua != null) {
			this.ua.abort(mode);
			this.aborted = true;
			this.abortMode = mode;
		}
	}

	public void setup() throws IOException {
		this.operatorLimits = OperatorLimits.current();
		this.prepareDefaultProperties();
	}

	/** Operator-defined limits that users cannot relax (2026-10-03). Read in {@link #setup()}. */
	private OperatorLimits operatorLimits = OperatorLimits.NONE;

	/** Passes the operator's limits to the UA. */
	private void applyOperatorLimits(final UserAgent ua) {
		if (ua instanceof net.zamasoft.foliojet.ua.impl.AbstractUserAgent abstractUa) {
			abstractUa.setOperatorLimits(this.operatorLimits);
		}
	}

	public void reset() throws IOException {
		try {
			if (this.ua != null) {
				this.abort(ABORT_FORCE);
				PipedOutputStream out = this.pipeOut;
				if (out != null) {
					out.close();
				}
			}
		} finally {
			this.ua = null;
			this.middlePath = false;
			this.props.clear();
			// The instruction discards session state, so discard retained state too.
			this.pagedSvgFontCarry = new net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry();
			this.prepareDefaultProperties();
			this.resolver.reset();
		}
	}

	public void close() throws IOException {
		this.reset();
	}

	/** One actual format invocation passed to {@link #runOnLargeStack}. */
	@FunctionalInterface
	private interface LargeStackTask {
		void run() throws AbortException, TranscoderException;
	}

	/**
	 * The stack size of the thread that runs layout (made a constant on 2026-07-26).
	 *
	 * <h2>Why always use this thread</h2>
	 *
	 * <p>
	 * The page-break and continuation mechanisms still contain <b>mutual recursion that has not
	 * been made iterative</b>, exceeding the JVM's default stack in deep documents and causing
	 * {@code StackOverflowError}. There are two such paths; iteration was considered and deferred
	 * for both: {@code FlowContainer.splitPageAxis}↔{@code AbstractBlockBox
	 * .splitForContinuation} (investigated on 2026-07-22), and the cycle
	 * {@code RootBuilder.restyleFrame} → replay → {@code pageBreak} → resume →
	 * {@code restyleFrame}. Two independent consultations on 2026-07-26 agreed that this would
	 * require rewriting all layout construction and event replay as a cooperative state machine,
	 * which was harder than the already-deferred {@code splitPageAxis} change.
	 * </p>
	 *
	 * <p>
	 * Previously opt-in via {@code processing.large-stack-thread}, it became an always-enabled
	 * constant because <b>disabling it by default forces users to configure it only after a failure</b>.
	 * </p>
	 *
	 * <h2>Basis for the value (measurements on 2026-07-26)</h2>
	 *
	 * <table border="1">
	 * <caption>Nesting depth and required stack size (two consecutive conversions in the same process)</caption>
	 * <tr><th>Document</th><th>Required stack</th></tr>
	 * <tr><td>Nesting depth 200</td><td>1 MB</td></tr>
	 * <tr><td>Nesting depth 1000 (real-document scale: comparable to an e-gov legislation page)</td><td>2 MB</td></tr>
	 * <tr><td>Nesting depth 5000</td><td>8 MB</td></tr>
	 * <tr><td>Nested multi-column layout + tiny pages (216 levels of the restyle cycle)</td><td>2 MB</td></tr>
	 * </table>
	 *
	 * <p>
	 * The measured maximum is 8 MB, so <b>64 MB provides an eightfold margin</b>. Stack size is
	 * a <b>reservation</b> from the OS; only pages actually touched are committed. Thus, even with
	 * concurrent conversions, physical memory grows only in proportion to recursion depth.
	 * </p>
	 *
	 * <h2>Cost (measured)</h2>
	 *
	 * <p>
	 * Creating one thread per conversion costs <b>+0.34 ms/conversion</b>
	 * (5.05 → 5.39 ms for small documents, about 6.7%). Real documents take hundreds of ms
	 * or more per conversion, so the relative cost is negligible.
	 * </p>
	 */
	private static final int LAYOUT_STACK_SIZE = net.zamasoft.foliojet.layout.util.LayoutThreadContext.LAYOUT_STACK_SIZE;

	/**
	 * Runs {@code task} (in practice, one {@code formatter.format(...)} call)
	 * on a dedicated thread with a stack of {@link #LAYOUT_STACK_SIZE}.
	 */
	private void runOnLargeStack(final LargeStackTask task) throws AbortException, TranscoderException {
		final int stackSize = LAYOUT_STACK_SIZE;
		final Throwable[] failure = new Throwable[1];
		// Running on another thread means **the caller thread's ThreadLocal values are not
		// inherited**. Explicitly pass only externally set policies.
		// (When this became always enabled on 2026-07-26, four test failures exposed
		// the missing RescuePolicy transfer.) LayoutThreadContext defines what is transferred
		// in one place (2026-09-02); EPUB item workers transfer the same context.
		final net.zamasoft.foliojet.layout.util.LayoutThreadContext context = net.zamasoft.foliojet.layout.util.LayoutThreadContext
				.capture();
		final Thread worker = new Thread(null, () -> {
			try (var scope = context.apply()) {
				task.run();
			} catch (Throwable t) {
				failure[0] = t;
			}
		}, "foliojet-layout", stackSize);
		worker.start();
		// Keep joining until the worker finishes, even when interrupted (2026-07-25).
		// Leaving early could let the worker keep writing to the same UA and result output
		// after the caller closes the session and input stream, providing a path to
		// output corruption, races with the session, and a non-daemon thread keeping the JVM alive.
		// Do not swallow the interrupt; restore it on the caller thread after completion.
		boolean interrupted = false;
		boolean timedOut = false;
		for (;;) {
			try {
				final long deadline = this.processingDeadlineNanos;
				if (deadline == 0 || timedOut) {
					worker.join();
					break;
				}
				final long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					timedOut = true;
					// abort is the source of truth for cooperative cancellation. interrupt only helps
					// release waits such as HTTP reads sooner; always join until the worker exits.
					this.ua.abort(ABORT_FORCE);
					worker.interrupt();
					continue;
				}
				final long waitMillis = Math.max(1L,
						Math.min(1000L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining)));
				worker.join(waitMillis);
				if (!worker.isAlive()) {
					break;
				}
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		if (timedOut) {
			throw new AbortException(ABORT_FORCE);
		}
		if (failure[0] instanceof AbortException ae) {
			throw ae;
		}
		if (failure[0] instanceof TranscoderException te) {
			throw te;
		}
		if (failure[0] instanceof RuntimeException re) {
			throw re;
		}
		if (failure[0] instanceof Error err) {
			throw err;
		}
		assert failure[0] == null : failure[0];
	}

	/**
	 * Converts a document.
	 *
	 * @param source
	 * @throws AbortException
	 * @throws TranscoderException
	 */
	protected void format(Source source) throws AbortException, TranscoderException {
		final long inputLimit = UAProps.INPUT_SIZE_LIMIT.getLong(this.ua);
		if (inputLimit >= 0) {
			source = new InputLimitedSource(source,
					new InputByteBudget(inputLimit, UAProps.INPUT_SIZE_LIMIT.getName()));
		}
		final Formatter formatter = PluginRegistry.getInstance().search(Formatter.class, source);
		MySourceResolver.PREFETCH_LOG.fine(() -> "input.prefetch=" + UAProps.INPUT_PREFETCH.getBoolean(this.ua));
		if (UAProps.INPUT_PREFETCH.getBoolean(this.ua)) {
			// Asynchronous prefetch of external resources (input.prefetch). Read the main document
			// through a read-ahead buffer and fetch discovered stylesheet/img resources in parallel
			// after ACL checks. See ResourcePrefetcher's Javadoc for details.
			source = ResourcePrefetcher.wrap(source, this.resolver);
		}
		Results results = this.results;
		long limit = UAProps.OUTPUT_SIZE_LIMIT.getLong(this.ua);
		// Negative means unlimited (not just -1; until 2026-10-05, values below -1 aborted immediately).
		if (limit >= 0) {
			results = new LimitedResults(results, limit, this.ua);
		}
		int passCount = UAProps.PROCESSING_PASS_COUNT.getInteger(this.ua);
		try {
			if (formatter instanceof MultiDocumentFormatter multi
					&& this.ua instanceof MultiDocumentOutput multiOut) {
				// A combination that can lay out multiple documents (EPUB spine items) as independent units
				// (2026-09-02). The formatter drives passes for each item, so
				// prepare the parent only once here. Items are laid out in parallel,
				// and results are released in spine order.
				this.ua.setResults(results);
				this.middlePath = false;
				this.ua.prepare(PrepareMode.DOCUMENT);
				this.ua.getDocumentContext().setBaseURI(source.getURI());
				this.ua.getUAContext().setPassCount(passCount);
				final Source formatSource = source;
				this.runOnLargeStack(() -> multi.formatDocuments(formatSource, multiOut, passCount));
				return;
			}
			if (passCount == 1) {
				// Single pass.
				PrepareMode mode = PrepareMode.DOCUMENT;
				boolean middlePath = UAProps.PROCESSING_MIDDLE_PASS.getBoolean(this.ua);
				if (this.middlePath != middlePath) {
					mode = middlePath ? PrepareMode.MIDDLE_PASS : PrepareMode.LAST_PASS;
					if (middlePath) {
						this.ua.setResults(results);
					}
				}
				if (!middlePath) {
					this.ua.setResults(results);
				}
				this.middlePath = middlePath;
				this.ua.prepare(mode);
				this.ua.getDocumentContext().setBaseURI(source.getURI());
				this.ua.getUAContext().setPassCount(passCount);
				this.ua.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(passCount));
				// source is reassigned to an input-limit wrapper within this method, so it is not
				// effectively final. Take a copy because the lambda cannot capture it directly.
				final Source formatSource = source;
				this.runOnLargeStack(() -> formatter.format(formatSource, this.ua));
			} else {
				// Multiple passes.
				this.ua.setResults(results);
				File tmpFile = null;
				try {
					tmpFile = File.createTempFile("copper", ".tmp");
					// STRUCTURE_SCAN: a lightweight preliminary scan with no box construction or layout
					// (for resolving :has()/:last-child variants;
					// see the development plan "2パス制御モード" (two-pass control mode)).
					// An independent, one-time additional phase that does not count toward
					// processing.pass-count. Read source to the end once and save it
					// to a temporary file used by all subsequent passes.
					// (Since source is assumed to be readable only once, all later reads
					// must use tmpFile. The old processing combined saving with MIDDLE_PASS;
					// this dedicated step now handles saving separately.)
					this.ua.prepare(PrepareMode.STRUCTURE_SCAN);
					this.ua.getDocumentContext().setBaseURI(source.getURI());
					try (final FileOutputStream out = new FileOutputStream(tmpFile);
							final TeeInputStream in = new TeeInputStream(source.getInputStream(), out)) {
						final Source fileSource = new StreamSource(source.getURI(), in, source.getMimeType(),
								source.getEncoding());
						this.runOnLargeStack(() -> formatter.format(fileSource, this.ua));
					}
					// Intermediate processing.
					for (int remaining = passCount; remaining > 1; --remaining) {
						this.ua.prepare(PrepareMode.MIDDLE_PASS);
						this.ua.getDocumentContext().setBaseURI(source.getURI());
						this.ua.getUAContext().setPassCount(remaining);
						this.ua.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(remaining));
						try (final InputStream in = new FileInputStream(tmpFile)) {
							final Source fileSource = new StreamSource(source.getURI(), in, source.getMimeType(),
									source.getEncoding());
							this.runOnLargeStack(() -> formatter.format(fileSource, this.ua));
						}
					}
					// Generate the output.
					this.ua.prepare(PrepareMode.LAST_PASS);
					this.ua.getDocumentContext().setBaseURI(source.getURI());
					try (final InputStream in = new FileInputStream(tmpFile)) {
						final Source fileSource = new StreamSource(source.getURI(), in, source.getMimeType(),
								source.getEncoding());
						this.ua.getUAContext().setPassCount(1);
						this.ua.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(1));
						this.runOnLargeStack(() -> formatter.format(fileSource, this.ua));
					}
					// Report convergence (2026-08-02): if values read by forward references
					// during the final pass changed within that pass, pass-count
					// is insufficient. Forward references themselves are normal and do not trigger warnings.
					if (this.ua.getUAContext().getPageRef().isUnconverged()) {
						LOG.warning("target-counter()/target-counters()/target-text() resolved to a value"
								+ " that changed during the final layout pass;"
								+ " consider increasing processing.pass-count.");
					}
					// @container G5 (2026-08-15 stage 5, design §3/§4): if the container's
					// used inline-size has not reached a fixed point relative to the previous value
					// in the final pass, report a diagnostic instead of silently outputting it
					// (output still uses final-pass values: design §4 "fail closedを破らない" (preserve fail-closed behavior)).
					if (!this.ua.getUAContext().getContainerFacts().isConverged()) {
						LOG.warning("@container query evaluated against a container inline-size that"
								+ " changed during the final layout pass;"
								+ " consider increasing processing.pass-count.");
					}
					// A period-2 oscillation was resolved by fixing it to the narrower size. The page
					// does not break, but layout no longer follows the author's query exactly.
					if (this.ua.getUAContext().getContainerFacts().hasOscillation()) {
						LOG.warning("@container query oscillated between two container inline-sizes;"
								+ " pinned the narrower one. The layout is stable but may not match"
								+ " the authored query.");
					}
				} finally {
					if (tmpFile != null) {
						// Do not silently ignore deletion failures (2026-07-24 architecture review E-1).
						// Main processing is complete, so only WARN; do not propagate the exception.
						try {
							java.nio.file.Files.deleteIfExists(tmpFile.toPath());
						} catch (IOException | RuntimeException e) {
							LOG.log(Level.WARNING, "Failed to delete temporary file: " + tmpFile, e);
						}
					}
				}
			}
		} catch (IOException e) {
			final RetainedTextLimitException retained = RetainedTextLimitException.findIn(e);
			if (retained != null) throw retained;
			final ContinuationInvariantViolationException invariant = ContinuationInvariantViolationException.findIn(e);
			if (invariant != null) throw invariant;
			// **An abort is not an I/O error** (2026-09-21). abort() closes down the body
			// input, so the parser sees a plain IOException (e.g., terminated prefetch).
			// Wrapping it in ERROR_IO caused clients to receive an abort as
			// "I/O error. prefetch read-ahead terminated".
			// Use the AbortException path for all abort reporting.
			if (this.aborted) {
				throw new AbortException(this.abortMode == 0 ? AbortException.ABORT_FORCE : this.abortMode);
			}
			// Rewrapping an already typed failure (TranscoderException) in ERROR_IO
			// adds another prefix to the already composed message, producing "I/O error. I/O error. ...".
			// Pass it through, preserving the original code and state.
			if (e instanceof TranscoderException) {
				throw (TranscoderException) e;
			}
			short code = CTIMessageCodes.ERROR_IO;
			String[] args = new String[] { e.getMessage() };
			String mes = MessageCodeUtils.toString(code, args);
			this.ua.message(code, args);
			LOG.log(Level.WARNING, mes, e);
			final TranscoderException failure = new TranscoderException(code, args, mes);
			failure.initCause(e);
			throw failure;
		}
	}
}

class ProgressInputStream extends CountingInputStream {
	final ProgressListener progressListener;

	ProgressInputStream(InputStream in, ProgressListener progressListener) {
		super(in);
		assert in != null;
		assert progressListener != null;
		this.progressListener = progressListener;
	}

	public int read(byte[] b, int off, int len) throws IOException {
		len = super.read(b, off, len);
		if (len != -1) {
			this.progressListener.progress(this.getByteCount());
		}
		return len;
	}

	public int read(byte[] b) throws IOException {
		int len = super.read(b);
		if (len != -1) {
			this.progressListener.progress(this.getByteCount());
		}
		return len;
	}

	public int read() throws IOException {
		int b = super.read();
		if (b != -1) {
			this.progressListener.progress(this.getByteCount());
		}
		return b;
	}
}
