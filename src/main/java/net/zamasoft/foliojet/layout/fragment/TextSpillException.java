package net.zamasoft.foliojet.layout.fragment;

import java.io.IOException;

/**
 * text payloadのspill I/O(書き込み・読み出し)が失敗したことを示す
 * 型付きレイアウト失敗です(E-6増分3b-2、2026-07-24新設)。
 *
 * <p>
 * SpillStoreの{@link IOException}は黙殺もlive継続へのフォールバックも
 * せず、この例外でレイアウト失敗として伝播する——spillの成否で出力が
 * 変わる非決定性を作らない(クラッシュ型の一貫性。
 * docs/consultations/consult-e6b-remaining-increments-codex.md §5)。
 * </p>
 */
public class TextSpillException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public TextSpillException(final String message, final IOException cause) {
		super(message, cause);
	}
}
