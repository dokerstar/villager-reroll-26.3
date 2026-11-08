package com.joplayx.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangFilesTest {

	private static final Path LANG = Path.of("src/main/resources/assets/villager-reroll/lang");
	private static final Pattern KEY = Pattern.compile(
			"\"((?:message|status|hud|command|config|screen|state|location|reason|key)\\.villager-reroll\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)*)\"");

	@Test
	void englishAndRussianHaveTheSameKeys() throws IOException {
		Set<String> english = keys(LANG.resolve("en_us.json"));
		Set<String> russian = keys(LANG.resolve("ru_ru.json"));
		assertEquals(english, russian);
		assertTrue(english.contains("reason.villager-reroll.break_unconfirmed"));
		assertTrue(english.contains("reason.villager-reroll.break_occupied"));
		assertTrue(english.contains("reason.villager-reroll.break_cancelled"));
		assertTrue(english.contains("reason.villager-reroll.break_failed"));
		assertTrue(english.contains("key.villager-reroll.start_stop"));
	}

	@Test
	void referencedKeysExistInEnglish() throws IOException {
		Set<String> english = keys(LANG.resolve("en_us.json"));
		List<String> missing = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
				try {
					Matcher matcher = KEY.matcher(Files.readString(path));
					while (matcher.find()) {
						if (!english.contains(matcher.group(1))) missing.add(matcher.group(1));
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
		requireEnum(english, missing, Path.of("src/main/java/com/joplayx/client/state/RerollState.java"),
				"RerollState", "state.villager-reroll.");
		requireEnum(english, missing, Path.of("src/main/java/com/joplayx/client/config/RerollerConfig.java"),
				"HudCorner", "config.villager-reroll.corner.");
		requireEnum(english, missing, Path.of("src/main/java/com/joplayx/client/util/HotbarUtil.java"),
				"LecternLocation", "location.villager-reroll.");
		assertTrue(missing.isEmpty(), () -> "missing lang keys: " + missing);
	}

	private static void requireEnum(Set<String> english, List<String> missing, Path source, String enumName, String keyPrefix) throws IOException {
		String text = Files.readString(source);
		int enumAt = text.indexOf("enum " + enumName);
		int open = text.indexOf('{', enumAt);
		int semi = text.indexOf(';', open);
		int brace = text.indexOf('}', open);
		int close = semi < 0 ? brace : Math.min(semi, brace);
		String body = text.substring(open + 1, close);
		Matcher constants = Pattern.compile("\\b([A-Z][A-Z0-9_]+)\\b").matcher(body);
		while (constants.find()) {
			String key = keyPrefix + constants.group(1).toLowerCase(java.util.Locale.ROOT);
			if (!english.contains(key)) missing.add(key);
		}
	}

	private static Set<String> keys(Path file) throws IOException {
		JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		return new TreeSet<>(object.keySet());
	}
}
