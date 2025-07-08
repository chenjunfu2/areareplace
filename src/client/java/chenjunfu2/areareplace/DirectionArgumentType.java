package chenjunfu2.areareplace;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class DirectionArgumentType implements ArgumentType<DirectionArgumentType.Direction> {
	private static final Collection<String> EXAMPLES = Arrays.asList("north", "south", "west", "east", "up", "down");
	
	public static DirectionArgumentType direction() {
		return new DirectionArgumentType();
	}
	
	@Override
	public Direction parse(StringReader reader) throws CommandSyntaxException {
		String input = reader.readUnquotedString().toLowerCase(Locale.ROOT);
		try {
			return Direction.valueOf(input.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create(input);
		}
	}
	
	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(com.mojang.brigadier.context.CommandContext<S> context, SuggestionsBuilder builder) {
		for (String example : EXAMPLES) {
			if (example.startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
				builder.suggest(example);
			}
		}
		return builder.buildFuture();
	}
	
	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
	
	public enum Direction {
		NORTH,
		SOUTH,
		WEST,
		EAST,
		UP,
		DOWN
	}
}