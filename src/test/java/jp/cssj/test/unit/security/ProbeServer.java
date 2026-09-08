package jp.cssj.test.unit.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

/**
 * 資源アクセス制御の試験で使う<b>記録用</b>のHTTPサーバです。
 *
 * <p>
 * 「組まれた」「サーバに来なかった」だけでは、<b>資源を無視しただけの場合と
 * 区別できません</b>。第1版の対策では、SVGの経路を起動できていないのに
 * 「遮断できた」と報告する誤りを実際に犯しました。そこで、この試験群では
 * どの検査でも次の3つを記録します。
 * </p>
 *
 * <ol>
 * <li><b>経路への到達</b> — この記録用サーバに何が要求されたか</li>
 * <li><b>取得元</b> — 押し込み/独自リゾルバ/HTTP/キャッシュのどれが本文を出したか</li>
 * <li><b>結果</b> — その固有の内容が実際に使われたか、意図した拒否が起きたか</li>
 * </ol>
 *
 * <p>
 * 3つ目のために、配る本文はすべて<b>識別できる固有のもの</b>にします。
 * 「取得できたか」ではなく「取得した中身が版面に出たか」で判定するためです。
 * </p>
 *
 * @see <a href=
 *      "file:../../../../../../../../../copperpdf4/docs/design/resource-access-policy-design.md">設計(第3版)</a>
 */
public class ProbeServer implements AutoCloseable {

	/** 届いた要求のパスを、届いた順に持ちます。 */
	private final List<String> hits = Collections.synchronizedList(new ArrayList<String>());

	private final Map<String, byte[]> bodies = new HashMap<String, byte[]>();

	private final Map<String, String> types = new HashMap<String, String>();

	/** パスごとのリダイレクト先です。値があればそちらへ302します。 */
	private final Map<String, String> redirects = new HashMap<String, String>();

	private final HttpServer server;

	public ProbeServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			final String path = exchange.getRequestURI().getPath();
			this.hits.add(path);
			final String to = this.redirects.get(path);
			if (to != null) {
				exchange.getResponseHeaders().add("Location", to);
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			final byte[] body = this.bodies.get(path);
			if (body == null) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			final String type = this.types.get(path);
			if (type != null) {
				exchange.getResponseHeaders().add("Content-Type", type);
			}
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		this.server.start();
	}

	public ProbeServer put(final String path, final String type, final byte[] body) {
		this.bodies.put(path, body);
		this.types.put(path, type);
		return this;
	}

	public ProbeServer put(final String path, final String type, final String body) {
		return this.put(path, type, body.getBytes(StandardCharsets.UTF_8));
	}

	public ProbeServer redirect(final String path, final String to) {
		this.redirects.put(path, to);
		return this;
	}

	/** このサーバの{@code http://127.0.0.1:ポート}です。 */
	public String base() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	public String url(final String path) {
		return this.base() + path;
	}

	/** そのパスへ何回来たかです。<b>0は「取得しなかった」の証拠になります。</b> */
	public int hits(final String path) {
		int n = 0;
		for (final String hit : this.hits) {
			if (hit.equals(path)) {
				n++;
			}
		}
		return n;
	}

	/** 全部で何回来たかです。 */
	public int totalHits() {
		return this.hits.size();
	}

	/** 届いた順のパスです。失敗時の説明に使います。 */
	public List<String> hitPaths() {
		return new ArrayList<String>(this.hits);
	}

	public void reset() {
		this.hits.clear();
	}

	@Override
	public void close() {
		this.server.stop(0);
	}
}
