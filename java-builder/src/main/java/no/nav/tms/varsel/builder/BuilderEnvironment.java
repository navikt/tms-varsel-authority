package no.nav.tms.varsel.builder;

import java.util.HashMap;
import java.util.Map;

public class BuilderEnvironment {

    private BuilderEnvironment() {}

    private final static Map<String, String> baseEnv;
    private final static HashMap<String, String> env;

    static {
        baseEnv = System.getenv();
        env = new HashMap<>();
        env.putAll(baseEnv);
    }

    public static void extend(Map<String, String> environment) {
        env.putAll(environment);
    }

    public static void reset() {
        env.clear();
        env.putAll(baseEnv);
    }

    static String get(String name) {
        return env.get(name);
    }
}
