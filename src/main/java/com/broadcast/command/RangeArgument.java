package com.broadcast.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/** 读取 "起始:终了" 这种包含冒号、不带空格的参数。 */
public final class RangeArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType ERROR =
            new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("需要 起始:终了"));

    private RangeArgument() {
    }

    public static RangeArgument range() {
        return new RangeArgument();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        String value = reader.getString().substring(start, reader.getCursor());
        if (value.isEmpty() || value.indexOf(':') < 0) throw ERROR.create();
        return value;
    }
}
