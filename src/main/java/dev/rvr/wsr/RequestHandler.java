package dev.rvr.wsr;

import com.google.gson.JsonObject;

/**
 * The request handler interface, this is used to handle requests from the server.
 * @param <T> The {@link RequestType} this handler handles.
 */
public interface RequestHandler<T extends RequestType> {
    /**
     * Called when a request is received from the server.
     * @param t The request received.
     */
    default void onRequest(T t) {};

    /**
     * Called when a request is received from the server and a response is expected.
     * @param t The request received.
     * @return The {@link JsonObject} to send to the server.
     */
    default JsonObject onRequestWithResponse(T t) {
        onRequest(t);
        return null;
    };

    /**
     * Get the {@link RequestType.TypeAdapter} of the request type this handler handles.
     * @return The {@link RequestType.TypeAdapter} of the request type this handler handles.
     */
    RequestType.TypeAdapter<T> getTypeAdapter();
}