package net.zamasoft.foliojet.ua.impl.svg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.apache.batik.util.AbstractParsedURLProtocolHandler;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.ParsedURLData;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * BatikのURL解析と<b>取得</b>をFolioJetへ引き受けるハンドラです。
 *
 * <p>
 * Batikは資源を自分で取りに行きます。SVGの中のCSS({@code @import}・
 * {@code <?xml-stylesheet?>})、色プロファイル、外部文書などです。それらは
 * {@link ParsedURL#openStream()}系を呼び、実体は{@link ParsedURLData}が
 * {@code java.net.URL}で開きます。<b>ここを塞がないと、FolioJetの
 * {@code input.include}/{@code input.exclude}を通らない取得口が残ります。</b>
 * </p>
 *
 * <p>
 * <b>Batikの取得は全部ここを通ります。</b>{@code ParsedURL.getHandler()}は
 * プロトコル専用のハンドラが無ければ既定ハンドラを返し、Batik 1.19の
 * どのjarにも{@code ParsedURLProtocolHandler}のサービス定義はありません。
 * このクラスは{@code super(null)}で登録されるので<b>その既定ハンドラ</b>です。
 * だから経路を1つずつ差し替える必要はなく、<b>ここ1か所で足ります</b>——
 * まだ数え上げていない経路も含めて。XSLT側で
 * {@code XSLTProcessorFilter}が{@code URIResolver}を差しているのと同じ形です。
 * </p>
 *
 * <p>
 * <b>取得元のUserAgentはスレッドに束ねます。</b>ハンドラは
 * {@link ParsedURL#registerHandler}でプロセスに1つだけ登録される静的な
 * 存在で、引数にも文脈を受け取れないためです。SVGの組み立ては
 * {@link SVGImageLoader#getImage}の中で同期的に進み、Batik自体が
 * スレッド安全ではないので、そこで束ねれば足ります。
 * <b>束ねられていなければ取得を拒みます</b>(素通りさせない)。
 * </p>
 */
class MyParsedURLDefaultProtocolHandler extends AbstractParsedURLProtocolHandler {
	public static final MyParsedURLDefaultProtocolHandler INSTANCE = new MyParsedURLDefaultProtocolHandler();

	/** いま組み立て中のSVGのUserAgentです。 */
	private static final ThreadLocal<UserAgent> CURRENT = new ThreadLocal<UserAgent>();

	/**
	 * このスレッドの取得をこのUserAgentへ回します。
	 *
	 * @return 元の値。{@link #leave(UserAgent)}へ渡して戻すこと
	 */
	static UserAgent enter(final UserAgent ua) {
		final UserAgent previous = CURRENT.get();
		CURRENT.set(ua);
		return previous;
	}

	/** {@link #enter(UserAgent)}が返した値で元に戻します。 */
	static void leave(final UserAgent previous) {
		if (previous == null) {
			CURRENT.remove();
		} else {
			CURRENT.set(previous);
		}
	}

	private MyParsedURLDefaultProtocolHandler() {
		super(null);
	}

	public ParsedURLData parseURL(String url) {
		if (url == null) {
			return this.createParsedURLData();
		}
		try {
			URI uri = URIHelper.create("UTF-8", url);
			return this.build(uri);
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ParsedURLData parseURL(ParsedURL base, String href) {
		URI uri;
		try {
			if (base == null) {
				if (href == null) {
					return this.createParsedURLData();
				}
				uri = URIHelper.create("UTF-8", href);
			} else {
				uri = URIHelper.create("UTF-8", base.toString());
				if (href != null) {
					if (uri.isOpaque() && href.startsWith("#")) {
						// **opaque URI(data:等)を基底にした同一文書内の断片参照**
						// (2026-08-06、premiumアイコンのclip-path="url(#id)"が
						// 空白になる問題で発覚)。java.net.URI#resolve()は
						// opaqueな基底に対してRFC3986の相対解決規則を適用
						// できず、基底を無視してhrefそのもの(#clip0のみ、
						// scheme/ssp無し)を返してしまう——Batikがそれを
						// 「別文書」と誤認してclip-path等のurl(#id)参照を
						// 解決できず、クリップ領域が空(＝描画結果が消える)
						// になっていた。scheme+生のscheme-specific-partは
						// 保ったままfragmentだけ差し替えて同一文書参照に
						// する(getRawSchemeSpecificPart()を使い、既にpercent
						// エンコード済みのデータを再エンコードして壊さない)
						uri = new URI(uri.getScheme() + ":" + uri.getRawSchemeSpecificPart() + href);
					} else {
						uri = uri.resolve(href);
					}
				}
			}
			return this.build(uri);
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	private ParsedURLData build(final URI uri) {
		final MyParsedURLData pURL = this.createParsedURLData();
		pURL.uri = uri;
		pURL.protocol = uri.getScheme();
		pURL.host = uri.getHost();
		pURL.port = uri.getPort();
		pURL.path = uri.getPath();
		pURL.ref = uri.getFragment();
		return pURL;
	}

	protected MyParsedURLData createParsedURLData() {
		return new MyParsedURLData();
	}

	/**
	 * 取得をFolioJetのリゾルバへ回す{@link ParsedURLData}です。
	 *
	 * <p>
	 * {@code openStream}も{@code openStreamRaw}も
	 * {@code openStreamInternal}へ集まるので、そこだけを上書きします。
	 * </p>
	 */
	static class MyParsedURLData extends ParsedURLData {
		/** 解析した元のURIです。文字列へ戻して解析し直さないために持ちます。 */
		URI uri = null;

		public boolean complete() {
			return true;
		}

		protected InputStream openStreamInternal(final String userAgent, final Iterator mimeTypes,
				final Iterator encodingTypes) throws IOException {
			if (this.stream != null) {
				return this.stream;
			}
			this.hasBeenOpened = true;
			if (this.uri == null) {
				throw new IOException("URIがありません。");
			}
			final UserAgent ua = CURRENT.get();
			if (ua == null) {
				// **素通りさせない。** ここへ来るのはSVGの組み立ての外からの
				// 取得で、FolioJetのACLを引く手段が無い
				throw new IOException("SVGの外からの取得は許しません: " + this.uri);
			}
			// **読み切ってから返す。**Batikがストリームをいつ閉じるかは
			// 経路によって違い、閉じない経路があるとSourceが解放されない。
			// ここを通るのはCSSと色プロファイルで、どちらも小さい
			final byte[] body;
			final Source source = ua.resolve(this.uri);
			try {
				final String mimeType = source.getMimeType();
				if (mimeType != null) {
					this.contentType = mimeType;
				}
				final String encoding = source.getEncoding();
				if (encoding != null) {
					this.contentEncoding = encoding;
				}
				try (InputStream in = source.getInputStream()) {
					body = in.readAllBytes();
				}
			} finally {
				ua.release(source);
			}
			this.stream = new ByteArrayInputStream(body);
			return this.stream;
		}
	}
}
