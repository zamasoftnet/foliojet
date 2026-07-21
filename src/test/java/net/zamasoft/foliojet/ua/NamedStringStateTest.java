package net.zamasoft.foliojet.ua;

import junit.framework.TestCase;

/**
 * {@link NamedStringState}の順序保証(呼び出し順ではなく
 * {@code elementKey}で先後を判定すること)と4モードの検証。
 * {@code string-set}に{@code content()}が含まれる場合、draw時完成が
 * build時解決より呼び出しタイミング上「後」になりうるため、
 * この順序保証が正しさの前提になる。
 */
public class NamedStringStateTest extends TestCase {
	public void testFirstAndLastWithinPage() {
		NamedStringState state = new NamedStringState();
		state.set("h", "A", 10);
		state.set("h", "B", 20);
		assertEquals("A", state.get("h", NamedStringState.FIRST));
		assertEquals("B", state.get("h", NamedStringState.LAST));
	}

	/**
	 * draw時完成(content())の呼び出しがbuild時解決より後になっても、
	 * elementKeyの大小で先後を判定するので正しい結果になることを確認する。
	 */
	public void testOutOfOrderCallsStillRespectElementKey() {
		NamedStringState state = new NamedStringState();
		// 文書順ではelementKey=5が先、20が後だが、呼び出し順は逆。
		state.set("h", "later-in-doc-but-called-first", 20);
		state.set("h", "earlier-in-doc-but-called-second", 5);
		assertEquals("earlier-in-doc-but-called-second", state.get("h", NamedStringState.FIRST));
		assertEquals("later-in-doc-but-called-first", state.get("h", NamedStringState.LAST));
	}

	public void testFirstFallsBackToEntryValueWhenNothingSetOnPage() {
		NamedStringState state = new NamedStringState();
		state.set("h", "page1", 1);
		state.endPage();
		// ページ2では何も代入していない。
		assertEquals("page1", state.get("h", NamedStringState.FIRST));
		assertEquals("page1", state.get("h", NamedStringState.LAST));
		assertEquals("page1", state.get("h", NamedStringState.START));
	}

	public void testFirstExceptIsEmptyOnThePageWhereAssigned() {
		NamedStringState state = new NamedStringState();
		state.set("h", "page1", 1);
		assertEquals("", state.get("h", NamedStringState.FIRST_EXCEPT));
		state.endPage();
		// 次ページでは代入されていないので通常のfirst相当(空文字列にならない)。
		assertEquals("page1", state.get("h", NamedStringState.FIRST_EXCEPT));
	}

	public void testUnsetNameReturnsNull() {
		NamedStringState state = new NamedStringState();
		assertNull(state.get("nope", NamedStringState.LAST));
		assertNull(state.get("nope", NamedStringState.FIRST));
		assertNull(state.get("nope", NamedStringState.START));
		assertNull(state.get("nope", NamedStringState.FIRST_EXCEPT));
	}
}
