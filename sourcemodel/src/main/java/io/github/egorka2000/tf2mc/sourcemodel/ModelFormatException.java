package io.github.egorka2000.tf2mc.sourcemodel;

/** Файл модели не того формата, другой версии или повреждён. */
public final class ModelFormatException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ModelFormatException(String message) {
		super(message);
	}
}
