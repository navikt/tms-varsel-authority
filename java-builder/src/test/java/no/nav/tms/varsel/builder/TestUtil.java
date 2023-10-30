package no.nav.tms.varsel.builder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class TestUtil {
    static <T> T withEnv(Map<String, String> env, Supplier<T> block) {
        try {

            Map<String, String> original = new HashMap<>(System.getenv());

            Map<String, String> toMutate = System.getenv();

            Class<? extends Map> classOfMap = toMutate.getClass();

            Field field = classOfMap.getDeclaredField("m");
            field.setAccessible(true);

            Map<String, String> mutable = (Map<String, String>) field.get(toMutate);

            mutable.clear();
            mutable.putAll(env);

            T retval = block.get();

            mutable.clear();
            mutable.putAll(original);

            return retval;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
