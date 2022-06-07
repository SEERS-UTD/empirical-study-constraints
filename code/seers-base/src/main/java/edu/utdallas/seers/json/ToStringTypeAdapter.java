package edu.utdallas.seers.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public abstract class ToStringTypeAdapter<T> extends TypeAdapter<T> {

    protected abstract T fromString(String string);

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        out.value(value.toString());
    }

    @Override
    public T read(JsonReader in) throws IOException {
        return fromString(in.nextString());
    }
}
