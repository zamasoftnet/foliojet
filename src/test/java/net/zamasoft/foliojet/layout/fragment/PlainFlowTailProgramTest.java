package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.BoxSubtype;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link PlainFlowTailProgram}の単体テストです(2026-07-22新設、B6a0)。
 * {@link ContinuationProgram}(直接構成した{@link PageResumeProgram})
 * だけを入力とし、実文書やライブbox・{@link ResumeProgramCompiler}を
 * 経由しない——{@code compile()}は{@code tail()}/{@code snapshot()}
 * だけを読む純粋な変換であるため、そのcontractを直接固定する。
 */
public class PlainFlowTailProgramTest extends TestCase {

	private static OpenPathSnapshot.OpenLevelDescriptor plainFlowLevel(final int index) {
		return new OpenPathSnapshot.OpenLevelDescriptor(index, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1,
				index, new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.PLAIN_FLOW));
	}

	private static OpenPathSnapshot.OpenLevelDescriptor anchorLevel() {
		return new OpenPathSnapshot.OpenLevelDescriptor(0, FlowBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 1, 0,
				new OpenPathSnapshot.OpenLevelRole.Anchor(OpenPathSnapshot.AnchorKind.PAGE_ROOT));
	}

	private static PageResumeProgram programWithTail(final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors,
			final ResumeTail tail) {
		final OpenPathSnapshot snapshot = new OpenPathSnapshot(WritingMode.TB, descriptors, Optional.empty());
		return new PageResumeProgram(snapshot, List.of(), tail, Map.of());
	}

	/** tailがOpenText(完全収集済み)の場合は予測すべき残存tailが無い。 */
	public void testEmptyForOpenTextTail() {
		final PageResumeProgram program = programWithTail(List.of(anchorLevel(), plainFlowLevel(1)),
				new ResumeTail.OpenText());
		assertTrue(PlainFlowTailProgram.compile(program).isEmpty());
	}

	/** LegacyOpenの残存tailが全レベルPLAIN_FLOWならallPlainFlow=true。 */
	public void testAllPlainFlowTrueWhenAllLevelsArePlainFlow() {
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(anchorLevel(), plainFlowLevel(1),
				plainFlowLevel(2));
		final PageResumeProgram program = programWithTail(descriptors,
				new ResumeTail.LegacyOpen(1, 3, new LegacyTailCause.SplitStopped(1)));

		final Optional<PlainFlowTailProgram> tail = PlainFlowTailProgram.compile(program);
		assertTrue(tail.isPresent());
		// index 0(anchor)はfirstOpenPathIndex=1より前なので含まれない
		assertEquals(2, tail.get().levels().size());
		assertEquals(1, tail.get().levels().get(0).index());
		assertEquals(2, tail.get().levels().get(1).index());
		assertTrue(tail.get().allPlainFlow());
	}

	/**
	 * 残存tailにPLAIN_FLOW以外(例: FLOW_SUBTYPE、ruby-body相当)が
	 * 1つでも混じるとallPlainFlow=false(改ページ契約のもとでは
	 * audit対象——このtailは削除も強制もせず統計に記録するだけに留める)。
	 */
	public void testAllPlainFlowFalseWhenNonPlainFlowLevelPresent() {
		final OpenPathSnapshot.OpenLevelDescriptor rubyBodyLevel = new OpenPathSnapshot.OpenLevelDescriptor(2,
				FlowBlockBox.class, BoxSubtype.RUBY_BODY, WritingMode.TB, 1, 2,
				new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.FLOW_SUBTYPE));
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(anchorLevel(), plainFlowLevel(1),
				rubyBodyLevel);
		final PageResumeProgram program = programWithTail(descriptors,
				new ResumeTail.LegacyOpen(1, 3,
						new LegacyTailCause.CapabilityBarrier(
								new OpenPathSnapshot.CapabilityBarrier(2, ContinuationCapability.FLOW_SUBTYPE))));

		final Optional<PlainFlowTailProgram> tail = PlainFlowTailProgram.compile(program);
		assertTrue(tail.isPresent());
		assertEquals(2, tail.get().levels().size());
		assertFalse(tail.get().allPlainFlow());
	}

	/** MULTICOLも(FLOW_SUBTYPEと同じく)PLAIN_FLOWではないためfalse。 */
	public void testAllPlainFlowFalseForMulticolLevel() {
		final OpenPathSnapshot.OpenLevelDescriptor multicolLevel = new OpenPathSnapshot.OpenLevelDescriptor(1,
				MulticolumnBlockBox.class, BoxSubtype.NONE, WritingMode.TB, 2, 1,
				new OpenPathSnapshot.OpenLevelRole.Ancestor(ContinuationCapability.MULTICOL));
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = List.of(anchorLevel(), multicolLevel,
				plainFlowLevel(2));
		final PageResumeProgram program = programWithTail(descriptors,
				new ResumeTail.LegacyOpen(1, 3, new LegacyTailCause.SplitStopped(1)));

		final Optional<PlainFlowTailProgram> tail = PlainFlowTailProgram.compile(program);
		assertTrue(tail.isPresent());
		assertEquals(2, tail.get().levels().size());
		assertFalse("先頭がMULTICOLなのでtail全体がPLAIN_FLOWとは言えない", tail.get().allPlainFlow());
	}

	/** recordのequals/hashCodeは値ベース(record由来)。 */
	public void testValueEquality() {
		final List<OpenPathSnapshot.OpenLevelDescriptor> levels = List.of(plainFlowLevel(1));
		assertEquals(new PlainFlowTailProgram(levels, true), new PlainFlowTailProgram(levels, true));
		assertFalse(new PlainFlowTailProgram(levels, true).equals(new PlainFlowTailProgram(levels, false)));
	}
}
