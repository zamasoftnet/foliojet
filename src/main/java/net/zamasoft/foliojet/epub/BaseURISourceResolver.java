package net.zamasoft.foliojet.epub;

import java.io.IOException;
import java.net.URI;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;

/**
 * EPUB内の相対パスを、基底URIの下の実体へ結び付けるリゾルバです。
 *
 * <p>
 * ZIPのEPUBでは{@code zip:}スキームと{@link ZIPFileSourceResolver}がこの役目を
 * 果たしますが、EPUBの中身がディレクトリとして与えられる場合は、
 * <b>基底URIへの相対解決</b>がそのまま同じ役目になります。基底URIが
 * {@code http:}ならウェブ上のEPUB展開物を、CTIPでクライアントが
 * ソースリゾルバを設定していればクライアント側のEPUBを、
 * それぞれ<b>必要な項目だけ</b>取得して組版できます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class BaseURISourceResolver implements SourceResolver {
	private final SourceResolver enclosed;

	private final URI base;

	/**
	 * @param enclosed 実体を取得するリゾルバ。
	 * @param base     末尾が{@code /}の階層URI。
	 */
	public BaseURISourceResolver(final SourceResolver enclosed, final URI base) {
		this.enclosed = enclosed;
		this.base = base;
	}

	/** 基底URIの下の絶対URIへ直します。 */
	public URI toAbsolute(final URI uri) {
		return this.base.resolve(uri);
	}

	@Override
	public Source resolve(final URI uri) throws IOException {
		return this.enclosed.resolve(this.toAbsolute(uri));
	}

	@Override
	public void release(final Source source) {
		this.enclosed.release(source);
	}
}
