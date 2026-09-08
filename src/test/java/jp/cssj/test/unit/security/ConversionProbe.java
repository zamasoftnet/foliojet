package jp.cssj.test.unit.security;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 1回の変換を行い、<b>版面と警告の両方</b>を取り出す道具です。
 *
 * <p>
 * 資源アクセス制御の検査では、「変換が成功した」だけでは何も言えません。
 * 取得できなかった資源は黙って無視されるので、<b>成功はフォールバックと
 * 区別できない</b>のです。そこでこの道具は、PDFの内容ストリームを展開して
 * 返し、呼び出し側が<b>固有の画素・字形が出たか</b>で判定できるようにします。
 * </p>
 *
 * @see ProbeServer
 */
public class ConversionProbe {

	private final Map<String, String> properties = new LinkedHashMap<String, String>();

	private boolean genericResolver = true;

	private boolean localAccessAllowed = true;

	/** 入出力プロパティを足します。 */
	public ConversionProbe property(final String name, final String value) {
		this.properties.put(name, value);
		return this;
	}

	private int includeCount = 0;

	/**
	 * ACLを設定します。何も設定しなければ既定(取得しない)のままです。
	 *
	 * <p>
	 * 複数回呼べます。{@code input.include.0}から順に番号を振ります。ACLは
	 * <b>先勝ち</b>なので、足した順に評価されます。
	 * </p>
	 *
	 * <p>
	 * 番号なしの{@code input.include}は使いません。番号付きの読み取りは
	 * {@code .0}から始まって<b>欠番で打ち切られる</b>ため、番号なしと
	 * {@code .1}を混ぜると2つめが黙って無視されます。
	 * </p>
	 */
	public ConversionProbe include(final String pattern) {
		if (pattern != null) {
			this.properties.put("input.include." + this.includeCount, pattern);
			this.includeCount++;
		}
		return this;
	}

	/**
	 * 埋め込み側の汎用リゾルバを差し込むかどうかです。既定は差し込みます
	 * (実運用のCLI・サーバと同じ形)。
	 *
	 * <p>
	 * <b>これを差し込まないことが、信頼しない入力での要点です。</b>
	 * {@code setLocalAccessAllowed(false)}だけでは足りません。
	 * </p>
	 */
	public ConversionProbe genericResolver(final boolean use) {
		this.genericResolver = use;
		return this;
	}

	public ConversionProbe localAccessAllowed(final boolean allowed) {
		this.localAccessAllowed = allowed;
		return this;
	}

	/** HTMLの文字列を組んで、結果を返します。基底URIは作業ディレクトリの下です。 */
	public Result convertHtml(final String html) throws Exception {
		return this.convert(html.getBytes(StandardCharsets.UTF_8), "text/html");
	}

	/**
	 * その<b>URLから</b>主文書を取得して組みます。
	 *
	 * <p>
	 * <b>SVGの中からの取得を測るときは、必ずこちらを使ってください。</b>
	 * 主文書が{@code file:}だと、SVGの中の{@code http:}の取得はBatikの
	 * 既定判定(取得元のホストが文書と違えば拒む)に先に止められ、
	 * <b>経路が起動しているかどうかを測れません</b>。2026-09-08に実際に
	 * これで測り損ねました。主文書を同じ記録用サーバから配れば、
	 * 既定判定は素通りし、FolioJetの制御だけを見られます。
	 * </p>
	 */
	public Result convertUrl(final String url) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> messages = Collections.synchronizedList(new ArrayList<String>());
		final DirectSession session = this.openSession(messages);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.transcode(URI.create(url));
		} finally {
			session.close();
		}
		return new Result(out.toByteArray(), messages);
	}

	public Result convert(final byte[] source, final String mediaType) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> messages = Collections.synchronizedList(new ArrayList<String>());
		final DirectSession session = this.openSession(messages);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			this.transcode(session, source, mediaType);
		} finally {
			session.close();
		}
		return new Result(out.toByteArray(), messages);
	}

	private DirectSession openSession(final List<String> messages) throws Exception {
		final MessageHandler handler = new MessageHandler() {
			@Override
			public void message(final short code, final String[] args, final String mes) {
				messages.add(Integer.toHexString(code & 0xFFFF).toUpperCase() + " " + mes);
			}
		};
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		session.setMessageHandler(handler);
		if (this.genericResolver) {
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		}
		session.setLocalAccessAllowed(this.localAccessAllowed);
		for (final Map.Entry<String, String> e : this.properties.entrySet()) {
			session.property(e.getKey(), e.getValue());
		}
		return session;
	}

	private void transcode(final CTISession session, final byte[] source, final String mediaType) throws Exception {
		// 基底URIは作業ディレクトリの下の実在しないファイルにする。fixtureの
		// 参照はすべて絶対URIなので相対解決は使わないが、基底が無いと
		// 相対URIの誤検出に気づけないため明示する
		final URI base = new java.io.File("files/unittest/probe-source.html").toURI();
		try (java.io.InputStream in = new java.io.ByteArrayInputStream(source)) {
			CTISessionHelper.transcodeStream(session, in, base, mediaType, "UTF-8");
		}
	}

	/** 1回の変換の結果です。 */
	public static class Result {

		private final byte[] pdf;

		private final List<String> messages;

		Result(final byte[] pdf, final List<String> messages) {
			this.pdf = pdf;
			this.messages = messages;
		}

		public byte[] pdf() {
			return this.pdf;
		}

		public List<String> messages() {
			return this.messages;
		}

		/** その番号の警告が出たかどうかです。{@code 2814}は資源の取得拒否です。 */
		public boolean hasMessage(final String hexCode) {
			for (final String m : this.messages) {
				if (m.startsWith(hexCode + " ")) {
					return true;
				}
			}
			return false;
		}

		/**
		 * <b>その URI がその番号で拒まれた</b>かどうかです。
		 *
		 * <p>
		 * 「取得されなかった」だけでは、そもそも経路が起動していない場合と
		 * 区別できません。<b>意図した拒否が、意図した相手に対して起きた</b>
		 * ことまで確かめるために使います。
		 * </p>
		 */
		public boolean deniedResource(final String hexCode, final String uri) {
			for (final String m : this.messages) {
				if (m.startsWith(hexCode + " ") && m.contains(uri)) {
					return true;
				}
			}
			return false;
		}

		/** 内容ストリームを全部つないだものです。 */
		public String operators() throws Exception {
			return String.join("\n", inflateStreams(this.pdf));
		}

		/**
		 * その色の塗り・線が出ているかどうかです。
		 *
		 * @param op {@code rg}なら塗り、{@code RG}なら線
		 */
		public boolean hasColor(final String op, final double r, final double g, final double b) throws Exception {
			final Matcher m = Pattern.compile("([\\d.]+) ([\\d.]+) ([\\d.]+) " + op + "\\b")
					.matcher(this.operators());
			while (m.find()) {
				if (Math.abs(Double.parseDouble(m.group(1)) - r) < 0.01
						&& Math.abs(Double.parseDouble(m.group(2)) - g) < 0.01
						&& Math.abs(Double.parseDouble(m.group(3)) - b) < 0.01) {
					return true;
				}
			}
			return false;
		}

		/** 失敗時の説明に使う要約です。 */
		public String describe() {
			return "PDF " + this.pdf.length + "バイト、警告 " + this.messages;
		}
	}

	private static List<String> inflateStreams(final byte[] pdf) {
		final List<String> result = new ArrayList<String>();
		final String latin = new String(pdf, StandardCharsets.ISO_8859_1);
		final Matcher m = Pattern.compile("stream\\r?\\n(.*?)endstream", Pattern.DOTALL).matcher(latin);
		while (m.find()) {
			final byte[] raw = m.group(1).getBytes(StandardCharsets.ISO_8859_1);
			final Inflater inflater = new Inflater();
			inflater.setInput(raw);
			final ByteArrayOutputStream buff = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			try {
				while (!inflater.finished()) {
					final int n = inflater.inflate(chunk);
					if (n == 0) {
						break;
					}
					buff.write(chunk, 0, n);
				}
				result.add(buff.toString(StandardCharsets.ISO_8859_1));
			} catch (final Exception e) {
				// 圧縮されていない・画像等のストリームは読み飛ばす
			} finally {
				inflater.end();
			}
		}
		return result;
	}
}
