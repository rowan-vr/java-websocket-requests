package dev.rvr.wsr;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;

/**
 * The request type interface, this is used to send requests to the server.
 * In general, it is useful to implement this interface as a record class.
 * @param <T> The {@link RequestType} this handler handles.
 */
public interface RequestType <T extends RequestType> {
    /**
     * The {@link RequestType.TypeAdapter} of the request type that (de)serializes this request type.
     * @return The {@link RequestType.TypeAdapter} of the request type this handler handles.
     */
    TypeAdapter<T> getAdapter();

    /**
     * The string type of the request.
     * @return The type of the request.
     */
    String getType();

    /**
     * Serialize this request type.
     * @return The serialized request type.
     */
    default JsonElement serialize() {
        return this.getAdapter().serialize((T) this,null,null);
    }

    /**
     * The type adapter interface, this is used to (de)serialize request.
     * @param <T> The {@link RequestType} this handler handles.
     */
    interface TypeAdapter<T> extends JsonDeserializer<T>, JsonSerializer<T> {

    }
}