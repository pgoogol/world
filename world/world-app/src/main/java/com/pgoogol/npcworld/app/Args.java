package com.pgoogol.npcworld.app;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Minimal {@code --name value} command line parser. */
public final class Args {

    private final Map<String, String> values = new HashMap<>();

    public Args(String[] args) {
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value pairs, got: " + args[i]);
            }
            values.put(args[i].substring(2), args[i + 1]);
        }
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }
}
