package net.zamasoft.foliojet.layout.fragment;

/**
 * shadow比較({@link ResumeProgramTrace})で、既存executor
 * ({@code RootBuilder.resumeFrame()})が実際に選んだ操作が、
 * {@link ResumeProgram}が予告した操作列と食い違ったことを示します
 * (2026-07-21新設、M6b Phase B B2)。
 */
public class ResumeProgramMismatchException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public ResumeProgramMismatchException(final String message) {
		super(message);
	}
}
