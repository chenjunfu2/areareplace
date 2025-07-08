package chenjunfu2.areareplace;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class DoubleArgumentType implements ArgumentType<Double> {
	private final double minimum;
	private final double maximum;
	
	private static final Collection<String> EXAMPLES = Collections.singletonList("1.5");
	
	private static final DynamicCommandExceptionType DOUBLE_TOO_LOW = new DynamicCommandExceptionType(min ->
			Text.literal("Double must not be less than " + min));
	
	private static final DynamicCommandExceptionType DOUBLE_TOO_HIGH = new DynamicCommandExceptionType(max ->
			Text.literal("Double must not be more than " + max));
	
	private DoubleArgumentType(double minimum, double maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
	}
	
	public static DoubleArgumentType doubleArg() {
		return doubleArg(Double.MIN_VALUE);
	}
	
	public static DoubleArgumentType doubleArg(double min) {
		return doubleArg(min, Double.MAX_VALUE);
	}
	
	public static DoubleArgumentType doubleArg(double min, double max) {
		return new DoubleArgumentType(min, max);
	}
	
	public static double getDouble(final CommandContext<?> context, final String name) {
		return context.getArgument(name, Double.class);
	}
	
	@Override
	public Double parse(StringReader reader) throws CommandSyntaxException {
		final double start = reader.getCursor();
		final double value = reader.readDouble();
		
		if (value < minimum) {
			throw DOUBLE_TOO_LOW.create(minimum);
		}
		if (value > maximum) {
			throw DOUBLE_TOO_HIGH.create(maximum);
		}
		
		return value;
	}
	
	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		// 不提供具体建议,只返回空的建议列表
		return builder.buildFuture();
	}
	
	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
}