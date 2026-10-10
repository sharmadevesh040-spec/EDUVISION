package com.eduvision.oops;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Registry that resolves a {@link ContentProcessor} by its {@link ContentProcessor#kind()}.
 *
 * <p>Uses collections + generics ({@code Map<String, ContentProcessor>}) and dispatches
 * polymorphically through the {@link ContentProcessor} interface. Spring injects every
 * {@code ContentProcessor} bean; tests can construct it directly with a list.
 */
@Component
public class ProcessorRegistry {

    private final Map<String, ContentProcessor> processors;

    public ProcessorRegistry(List<ContentProcessor> discovered) {
        Map<String, ContentProcessor> byKind = new LinkedHashMap<>();
        for (ContentProcessor processor : discovered) {
            byKind.put(processor.kind(), processor);
        }
        this.processors = Collections.unmodifiableMap(byKind);
    }

    /** @throws IllegalArgumentException when no processor is registered for {@code kind}. */
    public ContentProcessor resolve(String kind) {
        ContentProcessor processor = processors.get(kind);
        if (processor == null) {
            throw new IllegalArgumentException(
                    "No ContentProcessor registered for kind '" + kind + "'. Known kinds: "
                            + processors.keySet());
        }
        return processor;
    }

    public boolean supports(String kind) {
        return processors.containsKey(kind);
    }

    public Set<String> kinds() {
        return processors.keySet();
    }

    public Collection<ContentProcessor> all() {
        return processors.values();
    }

    public int size() {
        return processors.size();
    }
}
