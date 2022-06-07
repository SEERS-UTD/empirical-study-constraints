package edu.utdallas.seers.json;

import com.google.gson.TypeAdapter;

import java.util.Collections;
import java.util.Map;

public interface AdapterSupplier {
    default Map<Class<?>, TypeAdapter<?>> getTypeAdapters() {
        return Collections.emptyMap();
    }

    default Map<Class<?>, TypeAdapter<?>> getTypeHierarchyAdapters() {
        return Collections.emptyMap();
    }
}
