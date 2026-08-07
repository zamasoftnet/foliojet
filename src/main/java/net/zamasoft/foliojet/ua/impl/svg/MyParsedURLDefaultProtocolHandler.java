package net.zamasoft.foliojet.ua.impl.svg;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.batik.util.AbstractParsedURLProtocolHandler;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.ParsedURLData;

import net.zamasoft.zstream.resolver.util.URIHelper;

class MyParsedURLDefaultProtocolHandler extends AbstractParsedURLProtocolHandler {
	public static final MyParsedURLDefaultProtocolHandler INSTANCE = new MyParsedURLDefaultProtocolHandler();

	private MyParsedURLDefaultProtocolHandler() {
		super(null);
	}

	public ParsedURLData parseURL(String url) {
		ParsedURLData pURL = this.createParsedURLData();
		if (url == null) {
			return pURL;
		}
		try {
			URI uri = URIHelper.create("UTF-8", url);
			this.buildParsedURLData(pURL, uri);
			return pURL;
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ParsedURLData parseURL(ParsedURL base, String href) {
		ParsedURLData pURL = this.createParsedURLData();
		URI uri;
		try {
			if (base == null) {
				if (href == null) {
					return pURL;
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
						// scheme/ssp無し)を返してしまう——BatikがそれをS
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
			this.buildParsedURLData(pURL, uri);
			return pURL;
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	protected void buildParsedURLData(ParsedURLData pURL, URI uri) {
		pURL.protocol = uri.getScheme();
		pURL.host = uri.getHost();
		pURL.port = uri.getPort();
		pURL.path = uri.getPath();
		pURL.ref = uri.getFragment();
	}

	protected ParsedURLData createParsedURLData() {
		return new ParsedURLData() {
			public boolean complete() {
				return true;
			}
		};
	}
}
