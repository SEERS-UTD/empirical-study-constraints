package edu.utdallas.seers.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JSON {
    private JSON() {
    }

    /**
     * Writes an object to JSON.
     *
     * @param object   The object to serialize.
     * @param filePath Path to serialize the object to.
     * @param pretty   Whether it should be pretty-printed.
     * @param <T>      Type of the object.
     * @throws IOException If the write fails.
     */
    public static <T extends JSONSerializable<U>, U extends AdapterSupplier>
    void tryWriteJSON(T object, Path filePath, boolean pretty, U adapterSupplier) throws IOException {
        Files.createDirectories(filePath.getParent());

        Gson gson = createGson(pretty, adapterSupplier);

        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            gson.toJson(object, writer);
        }
    }

    /**
     * Writes an object to JSON without throwing checked exceptions.
     *
     * @param <T>             Type of the object.
     * @param object          The object to serialize.
     * @param filePath        Path to serialize the object to.
     * @param pretty          Whether it should be pretty-printed.
     * @param adapterSupplier Supplies any type adapters that the serialized type uses.
     */
    public static <T extends JSONSerializable<U>, U extends AdapterSupplier>
    void writeJSON(T object, Path filePath, boolean pretty, U adapterSupplier) {
        try {
            tryWriteJSON(object, filePath, pretty, adapterSupplier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads JSON.
     *
     * @param <T>             Type to read as.
     * @param filePath        Path of JSON file.
     * @param type            Type to read as.
     * @param adapterSupplier Supplies type adapters for the serialized type.
     * @return Deserialized object.
     * @throws IOException from Gson.
     */
    public static <T extends JSONSerializable<U>, U extends AdapterSupplier>
    T tryReadJSON(Path filePath, Class<T> type, U adapterSupplier) throws IOException {
        Gson gson = createGson(false, adapterSupplier);

        try {
            return gson.fromJson(Files.newBufferedReader(filePath), type);
        } catch (JsonSyntaxException e) {
            throw new IllegalStateException("Error when decoding JSON. Did you forget to register a type adapter?", e);
        }
    }

    /**
     * Reads JSON without throwing checked exceptions.
     *
     * @param <T>             Type to read as.
     * @param filePath        Path of JSON file.
     * @param type            Type to read as.
     * @param adapterSupplier Supplies type adapters.
     * @return Deserialized object.
     */
    public static <T extends JSONSerializable<U>, U extends AdapterSupplier>
    T readJSON(Path filePath, Class<T> type, U adapterSupplier) {
        try {
            return tryReadJSON(filePath, type, adapterSupplier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Gson createGson(boolean pretty, AdapterSupplier adapterSupplier) {
        GsonBuilder gsonBuilder = new GsonBuilder();

        adapterSupplier.getTypeAdapters()
                .forEach(gsonBuilder::registerTypeAdapter);

        adapterSupplier.getTypeHierarchyAdapters()
                .forEach(gsonBuilder::registerTypeHierarchyAdapter);

        if (pretty) {
            gsonBuilder.setPrettyPrinting();
        }

        return gsonBuilder.create();
    }
}
