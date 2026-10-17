package io.github.egorka2000.tf2mc.sourcemodel;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Материал из файла VMT: имя шейдера и параметры верхнего уровня. Формат текстовый — пары
 * «ключ значение» в фигурных скобках, как в остальных текстовых файлах Source; вложенные блоки
 * (например, {@code Proxies}) пропускаются.
 */
public final class VmtMaterial {
	private static final Pattern TOKEN = Pattern.compile("//[^\\n]*|\"([^\"]*)\"|[{}]|[^\\s{}\"]+");

	private final String shader;
	private final Map<String, String> parameters;

	private VmtMaterial(String shader, Map<String, String> parameters) {
		this.shader = shader;
		this.parameters = parameters;
	}

	public static VmtMaterial parse(String text) {
		Matcher matcher = TOKEN.matcher(text);
		String shader = null;
		String key = null;
		// Имя блока второго уровня: у материала-заплатки параметры лежат в «insert» и «replace».
		String block = null;
		int depth = 0;
		Map<String, String> parameters = new LinkedHashMap<>();
		Map<String, String> patched = new LinkedHashMap<>();

		while (matcher.find()) {
			String token = matcher.group();

			if (token.startsWith("//")) {
				continue;
			}

			if (token.equals("{")) {
				depth++;

				if (depth == 2) {
					block = key;
				}

				key = null;
				continue;
			}

			if (token.equals("}")) {
				depth--;
				key = null;
				continue;
			}

			String value = matcher.group(1) != null ? matcher.group(1) : token;

			if (depth == 0 && shader == null) {
				shader = value;
			} else if (depth == 1 || (depth == 2 && ("insert".equals(block) || "replace".equals(block)))) {
				if (key == null) {
					key = value.toLowerCase(Locale.ROOT);
				} else {
					(depth == 1 ? parameters : patched).putIfAbsent(key, value);
					key = null;
				}
			} else if (depth == 2 && key == null) {
				key = value.toLowerCase(Locale.ROOT);
			} else if (depth == 2) {
				key = null;
			}
		}

		parameters.putAll(patched);

		if (shader == null) {
			throw new ModelFormatException("VMT: нет имени шейдера");
		}

		return new VmtMaterial(shader, Map.copyOf(parameters));
	}

	/** Имя шейдера строчными буквами, например {@code vertexlitgeneric} или {@code patch}. */
	public String shader() {
		return shader.toLowerCase(Locale.ROOT);
	}

	/** Параметр верхнего уровня; ключ без учёта регистра, например {@code $basetexture}. */
	public Optional<String> parameter(String name) {
		return Optional.ofNullable(parameters.get(name.toLowerCase(Locale.ROOT)));
	}

	/**
	 * Основная текстура: путь внутри {@code materials/} без расширения, через «/» и строчными.
	 */
	public Optional<String> baseTexture() {
		return parameter("$basetexture").map(path -> path.replace('\\', '/').toLowerCase(Locale.ROOT).replaceFirst("\\.vtf$", ""));
	}

	/**
	 * Текстура цвета: {@code $basetexture}, а у глаз (шейдер {@code EyeRefract}, у которого её нет)
	 * — текстура радужки {@code $iris}.
	 */
	public Optional<String> colorTexture() {
		return baseTexture().or(() -> parameter("$iris").map(path -> path.replace('\\', '/').toLowerCase(Locale.ROOT).replaceFirst("\\.vtf$", "")));
	}

	/**
	 * Есть ли в материале прозрачные места: {@code $alphatest} или {@code $translucent}. Без них
	 * альфа-канал текстуры TF2 — не прозрачность, а маска блеска ({@code $basemapalphaphongmask}).
	 */
	public boolean seeThrough() {
		return flag("$alphatest") || flag("$translucent");
	}

	private boolean flag(String name) {
		return parameter(name).map(value -> {
			try {
				return Float.parseFloat(value.trim()) != 0.0F;
			} catch (NumberFormatException e) {
				return false;
			}
		}).orElse(false);
	}

	/** Для шейдера {@code patch}: материал, на котором основан этот. */
	public Optional<String> include() {
		return parameter("include").map(path -> path.replace('\\', '/').toLowerCase(Locale.ROOT));
	}
}
