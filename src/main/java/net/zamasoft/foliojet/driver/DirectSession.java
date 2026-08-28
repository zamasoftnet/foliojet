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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import net.zamasoft.foliojet.FolioJetVersion;
import net.zamasoft.foliojet.formatter.Formatter;
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

	/** バージョン情報のURIです。 */
	private static final URI VERSION_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/version");

	/** 出力データ形式情報のURIです。 */
	private static final URI OUTPUT_TYPES_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/output-types");

	/** 利用可能なフォント情報のURIです。 */
	private static final URI FONTS_INFO_URI = URI.create("http://www.cssj.jp/ns/ctip/fonts");

	private static final int PIPE_BUFFER_SIZE = 64 * 1024;

	private Results results = null;

	private ProgressListener progressListener = null;

	private MessageHandler messageHandler = CTIMessageHelper.NULL;

	private Map<String, String> props = new HashMap<String, String>();

	private final MySourceResolver resolver = new MySourceResolver();

	private Thread pipeThread = null;

	private PipedOutputStream pipeOut = null;

	private IOException pipeException = null;

	/** 現在の文書変換の単調時刻による締切。0は無制限。 */
	private volatile long processingDeadlineNanos;

	private File profileFile;

	private static final Map<File, FontSourceManager> FONT_CACHE = Collections
			.synchronizedMap(new LRUCache<File, FontSourceManager>(32));

	private UserAgent ua;

	/**
	 * Paged SVGのフォントサブセットをセッション内の次の変換へ持ち越す控え
	 * (2026-08-29)。UAは変換ごとに作り直すので、ここで寿命を保つ。
	 */
	private net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry pagedSvgFontCarry = new net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry();

	private boolean continuous = false;

	private boolean aborted = false;

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
				// バージョン情報
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
				// 出力形式
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
			} else if (uri.equals(FONTS_INFO_URI)) {
				// フォント
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

					// 書体分類と対応スクリプト(2026-08-27)。webappのフォント
					// 一覧の「書体」「言語」絞り込みが使う。分類はPanose/IBM
					// familyClassからの推定で、無指定のフォントでは付かない
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
	 * 代表コードポイントによるスクリプト判定表(ctip/fontsのscripts属性、
	 * 2026-08-27)。ラベルは言語でなく文字体系。cmap照会だけなので
	 * フォントあたり十数回のルックアップで済む。
	 */
	private static final Object[][] SCRIPT_PROBES = { //
			{ "latin", 0x41 }, // A
			{ "cyrillic", 0x416 }, // Ж
			{ "greek", 0x3A9 }, // Ω
			{ "arabic", 0x627 }, // ا
			{ "hebrew", 0x5D0 }, // א
			{ "devanagari", 0x905 }, // अ
			{ "thai", 0xE01 }, // ก
			{ "kana", 0x3042 }, // あ
			{ "cjk", 0x6F22 }, // 漢
			{ "hangul", 0xAC00 }, // 가
	};

	private static String scriptsOf(final FontSource font) {
		final StringBuilder sb = new StringBuilder();
		for (final Object[] probe : SCRIPT_PROBES) {
			if (font.canDisplay((Integer) probe[1])) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append((String) probe[0]);
			}
		}
		return sb.toString();
	}

	/**
	 * Panose(familyType/serifStyle/proportion)とOS/2のIBM familyClassから
	 * 書体分類を推定します(ctip/fontsのgeneric属性、2026-08-27)。
	 * どちらも無指定(ゼロ)のフォントはnull。
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
			// serifStyle 0/1(any/no fit)はIBM familyClassへ委ねる
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

	public boolean isDecodeMessage() {
		return this.decodeMessage;
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
		this.ua = ua;
	}

	public void setProgressListener(ProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	public void setMessageHandler(MessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public void property(String name, String value) throws IOException {
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
			// URIのエンコーディングはUTF-8で固定
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
	 * このセッションがローカル資源({@code file:}など)を取得してよいかを決めます。
	 *
	 * <p>
	 * <b>入出力プロパティではありません。</b> クライアントからは変更できず、
	 * サーバー(デーモン)が認証済みの利用者ごとに決めます。既定は許可なので、
	 * 組み込み利用とコマンドラインの動作は変わりません——それらを動かす主体は
	 * 元々そのプロセスのファイルを読めるためです。
	 * </p>
	 *
	 * <p>
	 * 遠隔の利用者に許すと、変換対象として任意のローカルファイルを指定でき、
	 * サーバーの設定・鍵・{@code /proc}が読み出せます。公開するサーバーでは
	 * 許可しないでください。クライアント自身が資源を送る経路(CTIPの
	 * リソース送信・要求時送信)は、この指定に関わらず使えます。
	 * </p>
	 */
	public void setLocalAccessAllowed(boolean allowed) {
		this.resolver.setLocalAccessAllowed(allowed);
	}

	public void transcode(URI uri) throws IOException, TranscoderException {
		this.prepareTranscode(uri);
		final Source source = this.resolver.resolve(uri, true);
		// 進行通知用に先開きしたストリームは自分で閉じる(2026-08-27)。
		// resolver.release(source)はSourceオブジェクトを返すだけで、
		// getInputStream()で開いた実ストリームまでは閉じない。閉じ漏れる
		// と、ローカルファイル変換後もOSのファイルロックが残り続けた
		// (webappでfile:のHTMLを変換すると削除できなくなる実害)
		InputStream progressIn = null;
		try {
			Source xsource = source;
			if (this.progressListener != null) {
				try {
					long srcLength = source.getLength();
					if (srcLength != -1) {
						this.progressListener.sourceLength(srcLength);
					}
					InputStream in = source.getInputStream();
					in = new BufferedInputStream(new ProgressInputStream(in, this.progressListener));
					progressIn = in;
					xsource = new StreamSource(uri, in, source.getMimeType(), source.getEncoding());
				} catch (IOException e) {
					throw new FileNotFoundException();
				}
			}
			this.transcode(xsource);
		} catch (FileNotFoundException e) {
			final short code = MessageCodes.ERROR_MISSING_SERVERSIDE_DOCUMENT;
			final String[] args = new String[] { uri.toString() };
			this.message(code, args);
			throw new TranscoderException(TranscoderException.STATE_BROKEN, code, args,
					MessageCodeUtils.toString(code, args));
		} finally {
			if (progressIn != null) {
				try {
					progressIn.close();
				} catch (IOException e) {
					// closeの失敗は変換結果に影響しない
				}
			}
			this.resolver.release(source);
		}
	}

	public OutputStream transcode(final SourceMetadata metaSource) throws IOException {
		this.prepareTranscode(metaSource.getURI());
		PipedOutputStream out = new PipedOutputStream() {
			public void close() throws IOException {
				super.close();
				DirectSession.this.flush();
			}
		};
		final String outputType = UAProps.OUTPUT_TYPE.getString(this.props);
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
				Source source = new StreamSource(metaSource.getURI(), xin, outputType, metaSource.getEncoding());
				DirectSession.this.transcode(source);
			} catch (IOException e) {
				DirectSession.this.pipeException = e;
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
				File fontDb = new File(dir, systemFonts + ".db");
				fsm = new ConfigurablePDFFontSourceManager(new FileSource(fontSource), fontDb);
				FONT_CACHE.put(fontSource, fsm);
			}
		}
		return fsm;
	}

	public void transcode(Source source) throws IOException, TranscoderException {
		URI uri = source.getURI();
		this.prepareTranscode(uri);

		// UAのセットアップ
		this.ua.setSourceResolver(this.resolver);
		this.ua.setMessageHandler(this);
		this.ua.setProperties(this.props);
		this.ua.getUAContext().setPagedSvgFontCarry(this.pagedSvgFontCarry);

		final FontSourceManager fsm = this.getFontSourceManager();
		this.ua.getUAContext().setFontSourceManager(fsm);

		// 変換を実行。締切は複数パス全体で共有し、各パスごとに
		// リセットしない。
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
			// 中断
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
			// 中断
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
			this.ua.message(CTIMessageCodes.FATAL_UNEXPECTED, t.getMessage());
			LOG.log(Level.SEVERE, "予期しないエラー", t);
			short code = CTIMessageCodes.FATAL_UNEXPECTED;
			String mes = MessageCodeUtils.toString(code, new String[] { t.getMessage() });
			if (!UAProps.PROCESSING_FAIL_ON_FATAL_ERROR.getBoolean(this.ua)) {
				try {
					this.ua.finish();
				} catch (BrokenResultException e1) {
					throw failure(code, mes, t);
				}
				return;
			}
			// **原因を繋ぐ**(2026-08-02): ログには出していたが呼び出し側へ
			// 渡す例外には原因が付いておらず、掃過の分類が
			// 「TranscoderException@DirectSession.transcode」1種類へ潰れて
			// いた。何種類の欠陥が残っているかが数えられない
			throw failure(code, mes, t);
		} finally {
			this.processingDeadlineNanos = 0;
			if (!this.continuous) {
				this.ua.dispose();
				this.ua = null;
			}
		}
	}

	/** 予期しない失敗を、原因を保ったまま包みます。 */
	private static TranscoderException failure(final short code, final String mes, final Throwable cause) {
		final TranscoderException e = new TranscoderException(TranscoderException.STATE_BROKEN, code, null, mes);
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
			// CTIクライアントが先に指定した値を、後から読むプロファイルの
			// 既定値で上書きしてはならない。特にフォント方針をプロファイルが
			// 持つ場合、output.pdf.fonts.policy=outlines が無視されていた。
			// include/exclude は累積指定なので従来どおり追加する。
			if (SPECIAL_PROPERTIES.contains(name) || !this.props.containsKey(name)) {
				this.property(name, value);
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

		this.resolver.setup(uri, this.props, this);
		this.aborted = false;

		final String outputType = UAProps.OUTPUT_TYPE.getString(this.props);
		UserAgentFactory factory = PluginRegistry.getInstance().search(UserAgentFactory.class,
				outputType);
		if (factory != null) {
			this.ua = factory.createUserAgent();
		} else {
			throw new IllegalStateException("UnsupportedType: " + outputType);
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
			throw new TranscoderException(TranscoderException.STATE_BROKEN, code, null, mes);
		} finally {
			this.ua.dispose();
			this.ua = null;
		}
	}

	public void abort(byte mode) throws IOException {
		if (!this.aborted && this.ua != null) {
			this.ua.abort(mode);
			this.aborted = true;
		}
	}

	public void setup() throws IOException {
		this.prepareDefaultProperties();
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
			// セッションの状態を捨てる指示なので、持ち越しも捨てる
			this.pagedSvgFontCarry = new net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry();
			this.prepareDefaultProperties();
			this.resolver.reset();
		}
	}

	public void close() throws IOException {
		this.reset();
	}

	/** {@link #runOnLargeStack}へ渡す、実際のformat呼び出し1回分です。 */
	@FunctionalInterface
	private interface LargeStackTask {
		void run() throws AbortException, TranscoderException;
	}

	/**
	 * レイアウトを実行するスレッドのstackサイズです(2026-07-26に定数化)。
	 *
	 * <h2>なぜ常にこのスレッドを使うのか</h2>
	 *
	 * <p>
	 * 改ページ・継続の機構には<b>反復化されていない相互再帰</b>が残っており、
	 * 深い文書でJVM既定のstackを超えて{@code StackOverflowError}になります。
	 * 該当するのは2系統で、いずれも反復化を検討したうえで見送っています——
	 * {@code FlowContainer.splitPageAxis}↔{@code AbstractBlockBox
	 * .splitForContinuation}(2026-07-22の調査)と、
	 * {@code RootBuilder.restyleFrame}→再生→{@code pageBreak}→再開→
	 * {@code restyleFrame}の循環(2026-07-26に独立2者へ相談。
	 * 「レイアウト構築とイベント再生全体を協調的状態機械へ全面書き換え」が
	 * 必要で、難度は見送った{@code splitPageAxis}より上、という評価で一致)。
	 * </p>
	 *
	 * <p>
	 * 従来は{@code processing.large-stack-thread}というオプトインでしたが、
	 * <b>既定で無効だと利用者が事故に遭ってから設定することになる</b>ため、
	 * 常時有効の定数へ変更しました。
	 * </p>
	 *
	 * <h2>値の根拠(2026-07-26の実測)</h2>
	 *
	 * <table border="1">
	 * <caption>ネスト深さと必要stackサイズ(同一プロセスで2回連続変換)</caption>
	 * <tr><th>文書</th><th>必要stack</th></tr>
	 * <tr><td>ネスト深さ200</td><td>1MB</td></tr>
	 * <tr><td>ネスト深さ1000(実文書規模——e-gov法令ページ相当)</td><td>2MB</td></tr>
	 * <tr><td>ネスト深さ5000</td><td>8MB</td></tr>
	 * <tr><td>段組の入れ子+極小ページ(restyle循環が216段)</td><td>2MB</td></tr>
	 * </table>
	 *
	 * <p>
	 * 実測の最大が8MBなので、<b>64MBは8倍の余裕</b>があります。stackサイズは
	 * OSへの<b>予約</b>であって、実際に触れたページ分しかコミットされません。
	 * したがって並行変換しても実メモリは再帰の深さ分しか増えません。
	 * </p>
	 *
	 * <h2>コスト(実測)</h2>
	 *
	 * <p>
	 * 変換ごとにスレッドを1つ作るコストは<b>+0.34 ms/変換</b>
	 * (小さい文書で5.05→5.39 ms、約6.7%)。実文書は1変換あたり数百ms〜
	 * なので相対的には無視できます。
	 * </p>
	 */
	private static final int LAYOUT_STACK_SIZE = 64 * 1024 * 1024;

	/**
	 * {@code task}(実際には{@code formatter.format(...)}の1呼び出し)を、
	 * {@link #LAYOUT_STACK_SIZE}のstackを持つ専用スレッドで実行します。
	 */
	private void runOnLargeStack(final LargeStackTask task) throws AbortException, TranscoderException {
		final int stackSize = LAYOUT_STACK_SIZE;
		final Throwable[] failure = new Throwable[1];
		// 別スレッドで走らせるので、**呼び出し側スレッドのThreadLocalは
		// 引き継がない**。外から設定される方針だけを明示的に渡す
		// (2026-07-26に常時有効化した際、RescuePolicyの引き継ぎ漏れで
		// テストが4件落ちて発覚。ThreadLocalを増やしたらここも増やすこと)。
		final net.zamasoft.foliojet.layout.rescue.RescuePolicy rescuePolicy = net.zamasoft.foliojet.layout.rescue.RescuePolicy
				.current();
		final String displayListDir = net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDir();
		final boolean detailedDisplayListGeometry = net.zamasoft.foliojet.layout.draw.DisplayListDumper
				.currentDetailedGeometry();
		final Thread worker = new Thread(null, () -> {
			try (var policy = rescuePolicy.scoped();
					var dump = net.zamasoft.foliojet.layout.draw.DisplayListDumper.scopedDir(displayListDir);
					var geometry = net.zamasoft.foliojet.layout.draw.DisplayListDumper
							.scopedDetailedGeometry(detailedDisplayListGeometry)) {
				task.run();
			} catch (Throwable t) {
				failure[0] = t;
			}
		}, "foliojet-layout", stackSize);
		worker.start();
		// 割り込まれてもworkerの完了までjoinし続ける(2026-07-25)。
		// 途中で抜けると、呼び出し側がsessionをcloseし入力ストリームを
		// 閉じたあともworkerが同じUA・同じ結果出力へ書き続けうる——
		// 出力破損・sessionとの競合・非daemonスレッドによるJVM残留の経路。
		// 割り込みは握りつぶさず、完了後に呼び出し元スレッドへ復元する。
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
					// abortは協調的中断の真実源。interruptはHTTP読み取り等の
					// 待機を早く解除する補助であり、workerの終了までは必ずjoinする。
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
	 * 文書を変換します。
	 *
	 * @param source
	 * @throws AbortException
	 * @throws TranscoderException
	 */
	protected void format(Source source) throws AbortException, TranscoderException {
		// @format
		final long inputLimit = UAProps.INPUT_SIZE_LIMIT.getLong(this.ua);
		if (inputLimit >= 0) {
			source = new InputLimitedSource(source,
					new InputByteBudget(inputLimit, UAProps.INPUT_SIZE_LIMIT.getName()));
		}
		final Formatter formatter = PluginRegistry.getInstance().search(Formatter.class, source);
		MySourceResolver.PREFETCH_LOG.fine(() -> "input.prefetch=" + UAProps.INPUT_PREFETCH.getBoolean(this.ua));
		if (UAProps.INPUT_PREFETCH.getBoolean(this.ua)) {
			// 外部リソースの非同期先読み(input.prefetch)。主文書を読み
			// 先行バッファ越しに流し、発見したstylesheet/imgをACL検査の上で
			// 並列取得する。詳細はResourcePrefetcherのjavadoc
			source = ResourcePrefetcher.wrap(source, this.resolver);
		}
		Results results = this.results;
		long limit = UAProps.OUTPUT_SIZE_LIMIT.getLong(this.ua);
		if (limit != -1L) {
			results = new LimitedResults(results, limit, this.ua);
		}
		int passCount = UAProps.PROCESSING_PASS_COUNT.getInteger(this.ua);
		try {
			if (passCount == 1) {
				// 1パス
				PrepareMode mode = PrepareMode.DOCUMENT;
				boolean middlePath = UAProps.PROCESSING_MIDDLE_PASS.getBoolean(this.ua);
				if (this.middlePath != middlePath) {
					mode = middlePath ? PrepareMode.MIDDLE_PASS : PrepareMode.LAST_PASS;
					if (middlePath) {
						((RandomResultUserAgent) this.ua).setResults(results);
					}
				}
				if (!middlePath) {
					((RandomResultUserAgent) this.ua).setResults(results);
				}
				this.middlePath = middlePath;
				this.ua.prepare(mode);
				this.ua.getDocumentContext().setBaseURI(source.getURI());
				this.ua.getUAContext().setPassCount(passCount);
				this.ua.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(passCount));
				// source をラムダで直接捕捉しない: このメソッドはproduct側の
				// ライセンスチェック拡張で source を再代入する変種が存在し
				// (copperpdf4/product、期限切れ時にexpired.htmlへ差し替える)、
				// 再代入されるとeffectively finalでなくなりコンパイルできない
				// (2026-07-23、imageTestのfresh jarビルドで実際に検出)。
				final Source formatSource = source;
				this.runOnLargeStack(() -> formatter.format(formatSource, this.ua));
			} else {
				// 複数パス
				((RandomResultUserAgent) this.ua).setResults(results);
				File tmpFile = null;
				try {
					tmpFile = File.createTempFile("copper", ".tmp");
					// STRUCTURE_SCAN: ボックス構築・レイアウトを一切行わない
					// 軽量な事前走査(:has()/:last-child系の解決用、
					// docs/PLAN.md「2パス制御モード」参照)。
					// processing.pass-countの反復回数には数えない、独立した
					// 1回だけの追加フェーズ。sourceを一度だけ読み切り、
					// 以降の全パスが使うテンポラリファイルへ保存する
					// (sourceは1回しか読めない前提のため、以降は必ず
					// tmpFileから読む——保存とMIDDLE_PASSを兼ねていた旧処理を
					// この専用ステップへ分離した)。
					this.ua.prepare(PrepareMode.STRUCTURE_SCAN);
					this.ua.getDocumentContext().setBaseURI(source.getURI());
					try (final FileOutputStream out = new FileOutputStream(tmpFile);
							final TeeInputStream in = new TeeInputStream(source.getInputStream(), out)) {
						final Source fileSource = new StreamSource(source.getURI(), in, source.getMimeType(),
								source.getEncoding());
						this.runOnLargeStack(() -> formatter.format(fileSource, this.ua));
					}
					// 中間処理
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
					// 目的物生成
					this.ua.prepare(PrepareMode.LAST_PASS);
					this.ua.getDocumentContext().setBaseURI(source.getURI());
					try (final InputStream in = new FileInputStream(tmpFile)) {
						final Source fileSource = new StreamSource(source.getURI(), in, source.getMimeType(),
								source.getEncoding());
						this.ua.getUAContext().setPassCount(1);
						this.ua.message(MessageCodes.INFO_PASS_REMAINDER, String.valueOf(1));
						this.runOnLargeStack(() -> formatter.format(fileSource, this.ua));
					}
					// 収束の報告(2026-08-02): 最終パスで前方参照が読んだ
					// 値が、そのパスのうちに変わっていたら pass-count が
					// 足りない。前方参照そのものは正常なので警告しない
					if (this.ua.getUAContext().getPageRef().isUnconverged()) {
						LOG.warning("target-counter()/target-counters()/target-text() resolved to a value"
								+ " that changed during the final layout pass;"
								+ " consider increasing processing.pass-count.");
					}
					// @container G5(2026-08-15段5、設計§3/§4): 最終パスで
					// コンテナのused inline-sizeが直前の値から不動点に達して
					// いなければ、黙って出さず診断する(出力自体は最終パスの
					// 値のまま使う——設計§4「fail closedを破らない」)
					if (!this.ua.getUAContext().getContainerFacts().isConverged()) {
						LOG.warning("@container query evaluated against a container inline-size that"
								+ " changed during the final layout pass;"
								+ " consider increasing processing.pass-count.");
					}
					// 振動(周期2)は狭いほうへ固定して確定させた。紙面は破綻
					// しないが、著者の書いたクエリのとおりには組まれていない
					if (this.ua.getUAContext().getContainerFacts().hasOscillation()) {
						LOG.warning("@container query oscillated between two container inline-sizes;"
								+ " pinned the narrower one. The layout is stable but may not match"
								+ " the authored query.");
					}
				} finally {
					if (tmpFile != null) {
						// 削除失敗を黙殺しない(2026-07-24アーキレビューE-1)。
						// 本処理は完了しているため例外は伝播させずWARNのみ
						try {
							java.nio.file.Files.deleteIfExists(tmpFile.toPath());
						} catch (IOException | RuntimeException e) {
							LOG.log(Level.WARNING, "Failed to delete temporary file: " + tmpFile, e);
						}
					}
				}
			}
		} catch (IOException e) {
			short code = CTIMessageCodes.ERROR_IO;
			String[] args = new String[] { e.getMessage() };
			String mes = MessageCodeUtils.toString(code, args);
			this.ua.message(code, args);
			LOG.log(Level.WARNING, mes, e);
			throw new TranscoderException(code, args, mes);
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
