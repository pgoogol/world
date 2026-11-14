package com.pgoogol.npcworld.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Random;

/**
 * Saves and restores the exact state of a {@link java.util.Random}. Keeping {@code java.util.Random}
 * (instead of a generator with an open state) keeps stages 1 and 2 drawing the same numbers as before.
 */
final class RandomState {

    /** Only a plain Random may come back from a saved state. */
    private static final ObjectInputFilter ONLY_RANDOM = ObjectInputFilter.Config.createFilter(
            "java.util.Random;maxdepth=2;maxrefs=4;!*");

    private RandomState() {
    }

    static String save(Random random) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(random);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static Random restore(String state) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(state)))) {
            in.setObjectInputFilter(ONLY_RANDOM);
            Object object = in.readObject();
            if (object.getClass() != Random.class) {
                throw new IllegalArgumentException("Saved random state is not a java.util.Random");
            }
            return (Random) object;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Saved random state is not a java.util.Random", e);
        }
    }
}
