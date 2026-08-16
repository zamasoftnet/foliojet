package net.zamasoft.foliojet.epub;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * EPUBの中身がディレクトリとして与えられる場合の{@link ArchiveFile}です。
 *
 * <p>
 * ZIPを開く代わりに、項目のパスをリゾルバへ渡して都度取得します。
 * <b>要る項目しか取りません</b>。CTIPでクライアントがソースリゾルバを
 * 設定していれば、クライアント側のEPUBから必要な項目だけが送られてくるので、
 * 画像を出力しない処理では画像が転送されません。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class ResolvedArchiveFile implements ArchiveFile {
	private final SourceResolver resolver;

	public ResolvedArchiveFile(final SourceResolver resolver) {
		this.resolver = resolver;
	}

	private URI toURI(final String path) {
		try {
			return URIHelper.create("UTF-8", path);
		} catch (final Exception e) {
			return URI.create(path);
		}
	}

	@Override
	public boolean exists(final String path) {
		Source source = null;
		try {
			source = this.resolver.resolve(this.toURI(path));
			return source.exists();
		} catch (final IOException | SecurityException e) {
			return false;
		} finally {
			if (source != null) {
				this.resolver.release(source);
			}
		}
	}

	@Override
	public InputStream getInputStream(final String path) throws IOException {
		final Source source = this.resolver.resolve(this.toURI(path));
		// 読み終わりで必ず解放する。ZipArchiveFileが再オープン版でしている
		// のと同じ形で、呼び出し側の作法を変えずに済ませる
		return new FilterInputStream(source.getInputStream()) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					ResolvedArchiveFile.this.resolver.release(source);
				}
			}
		};
	}

	@Override
	public void close() throws IOException {
		// 個々の取得ごとに解放しているので、まとめて閉じるものは無い
	}
}
