package net.zamasoft.foliojet.xml.filter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import net.zamasoft.zstream.resolver.Source;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XSLTUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class XSLTUtils {
	private XSLTUtils() {
		// unused
	}

	public static javax.xml.transform.Source toTrAXSource(Source source) throws IOException {
		StreamSource traxSource;
		if (source.isReader()) {
			traxSource = new StreamSource(source.getReader(), source.getURI().toString());
		} else {
			traxSource = new StreamSource(source.getInputStream(), source.getURI().toString());
		}
		return traxSource;
	}

	/**
	 * XSLT の変換器を作ります。
	 *
	 * <p>
	 * <b>成果物以外への書き出しを禁じます。</b>Saxon の
	 * {@code xsl:result-document} は既定の出力リゾルバでファイルを作れ、
	 * {@code URLConnection.getOutputStream()} も持ちます。Copper の
	 * {@link net.zamasoft.zstream.io.Results 結果}にも出力サイズの上限にも
	 * 掛からない経路なので、任意の場所へ書けてしまいます
	 * (2026-09-08 に実測。file: へ書き込めた)。入力側は
	 * {@code URIResolver} が{@code input.include}/{@code input.exclude}の
	 * ACL を引くので、出力側だけが素通りしていました。
	 * </p>
	 */
	public static SAXTransformerFactory createTransformerFactory() {
		final net.sf.saxon.TransformerFactoryImpl tf = new net.sf.saxon.TransformerFactoryImpl();
		final net.sf.saxon.Configuration config = tf.getConfiguration();
		// xsl:result-document の書き出し先を全部拒む
		config.setOutputURIResolver(NO_OUTPUT);
		// 拡張関数・リフレクションによる Java 呼び出しも塞ぐ
		config.setBooleanProperty(net.sf.saxon.lib.Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
		// collection()/uri-collection() はディレクトリ列挙・ZIP・ネットワーク取得へ
		// 進む独自経路で、ALLOW_EXTERNAL_FUNCTIONS では閉じない。
		// input.include/input.exclude の判定も通らないので拒む
		config.setCollectionFinder(NO_COLLECTION);
		return tf;
	}

	/**
	 * 入力側の取得を<b>すべて</b>FolioJetのリゾルバへ回します。
	 *
	 * <p>
	 * Saxon 12 の {@code ResourceResolver} は、{@code document()}・
	 * {@code unparsed-text()}・{@code xsl:import}/{@code xsl:include}・
	 * 外部実体の取得が<b>1本に集まる</b>差し込み口です。旧来の
	 * {@code URIResolver}だけでは{@code unparsed-text()}を受け持てず、
	 * Saxonが代用するときに相対URIを{@code null}で渡すため
	 * {@code URIHelper}が落ちていました(2026-09-08に実測)。
	 * </p>
	 *
	 * <p>
	 * 取得は{@code ua.resolve()}を通るので、
	 * {@code input.include}/{@code input.exclude}とローカル資源の可否が
	 * そのまま効きます。<b>拒めば{@link SecurityException}が上がり、
	 * 変換はそこで止まります。</b>
	 * </p>
	 */
	static void setResourceResolver(final javax.xml.transform.sax.SAXTransformerFactory tf,
			final net.zamasoft.foliojet.ua.UserAgent ua) {
		final net.sf.saxon.Configuration config = ((net.sf.saxon.TransformerFactoryImpl) tf).getConfiguration();
		config.setResourceResolver(request -> {
			if (request.uri == null) {
				return null;
			}
			final java.net.URI uri;
			try {
				uri = net.zamasoft.zstream.resolver.util.URIHelper.create("UTF-8", request.uri);
			} catch (final java.net.URISyntaxException e) {
				throw new net.sf.saxon.trans.XPathException("Invalid URI: " + request.uri, e);
			}
			final byte[] body;
			try {
				final net.zamasoft.zstream.resolver.Source source = ua.resolve(uri);
				try (java.io.InputStream in = source.getInputStream()) {
					body = in.readAllBytes();
				} finally {
					ua.release(source);
				}
			} catch (final java.io.IOException e) {
				throw new net.sf.saxon.trans.XPathException(e.getMessage(), e);
			}
			// **読み切ってから返す。**Saxonがいつ読むか分からないので、
			// Sourceを開いたまま渡すと解放の時期が決められない
			final javax.xml.transform.stream.StreamSource result = new javax.xml.transform.stream.StreamSource(
					new java.io.ByteArrayInputStream(body));
			result.setSystemId(uri.toString());
			return result;
		});
	}

	/** {@code collection()}/{@code uri-collection()}を拒むコレクション取得です。 */
	private static final net.sf.saxon.lib.CollectionFinder NO_COLLECTION = (context, collectionURI) -> {
		throw new net.sf.saxon.trans.XPathException(
				"collection() is not permitted: " + collectionURI);
	};

	/** {@code xsl:result-document}の書き出しを全部拒む出力リゾルバです。 */
	private static final net.sf.saxon.lib.OutputURIResolver NO_OUTPUT = new net.sf.saxon.lib.OutputURIResolver() {
		public net.sf.saxon.lib.OutputURIResolver newInstance() {
			return this;
		}

		public javax.xml.transform.Result resolve(final String href, final String base)
				throws javax.xml.transform.TransformerException {
			throw new javax.xml.transform.TransformerException(
					"xsl:result-document is not permitted: " + href);
		}

		public void close(final javax.xml.transform.Result result) {
			// nothing to close
		}
	};

	public static TransformerHandler createIdentityTransformerHandler() throws TransformerConfigurationException {
		return createTransformerFactory().newTransformerHandler();
	}

	public static StreamSource toStreamSource(Source source) throws IOException {
		StreamSource streamSource;
		if (source.isReader()) {
			streamSource = new StreamSource(new BufferedReader(source.getReader()));
		} else {
			streamSource = new StreamSource(new BufferedInputStream(source.getInputStream()));
		}
		streamSource.setSystemId(source.getURI().toString());
		return streamSource;
	}
}
