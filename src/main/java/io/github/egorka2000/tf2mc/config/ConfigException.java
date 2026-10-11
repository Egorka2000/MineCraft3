package io.github.egorka2000.tf2mc.config;

/** Ошибка в файле настроек; сообщение показывается пользователю как есть. */
public class ConfigException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ConfigException(String message) {
		super(message);
	}
}
