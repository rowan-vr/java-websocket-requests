package dev.rvr.wsr;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovisionaries.ws.client.*;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A client class for the WebSocketRequest system.
 */
public class RequestClient extends WebSocketAdapter {
    /**
     * The WebSocket instance.
     */
    private WebSocket socket;
    /**
     * A map of request types to handlers.
     */
    private final HashMap<String, RequestHandler> handlers = new HashMap<>();
    /**
     * A map of request references to pending requests.
     */
    private final HashMap<String, Map.Entry<RequestType, CompletableFuture>> pendingRequests = new HashMap<>();
    /**
     * The number of times the client has failed to connect.
     */
    private int connectErrors = 0;
    /**
     * Whether the client is currently connecting.
     */
    private boolean connecting = false;

    /**
     * A function to build the socket before connecting, this can be used to set additional headers.
     */
    @Setter
    private Function<WebSocket,WebSocket> socketBuilder = Function.identity();
    /**
     * A callback for when the client could not connect to the server.
     * The first parameter is the exception that was thrown, the amount of seconds before the client will try to reconnect.
     */
    @Setter
    private BiConsumer<WebSocketException, Integer> connectErrorCallback = (e, i) -> {};
    /**
     * A callback for when the server sends a request for an unknown type.
     */
    @Setter
    private Consumer<String> unknownTypeCallback = (s) -> {};

    /**
     * A callback for when a registered handler throws an exception.
     */
    @Setter
    private BiConsumer<String,Throwable> handlerExceptionCallback = (s,t) -> {};

    /**
     * A supplier that is called when the client is disconnected, if it returns true the client will try to reconnect.
     */
    @Setter
    private Supplier<Boolean> shouldReconnect = () -> true;

    /**
     * The URL of the WebSocket server.
     */
    private final String websocketUrl;

    /**
     * Create a new RequestClient instance.
     * @param websocketUrl The URL of the WebSocket server.
     */
    public RequestClient(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    /**
     * Connect to the WebSocket server of this instance.
     */
    public void connect() {
        connecting = true;
        try {
            if (socket != null && socket.isOpen()) socket.disconnect("Reconnecting");
            socket = new WebSocketFactory().createSocket(websocketUrl)
                    .addListener(this);
            socket = socketBuilder.apply(socket).connectAsynchronously();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Disconnect from the WebSocket server of this instance.
     */
    public void disconnect() {
        if (socket != null && socket.isOpen()) socket.disconnect("Disconnecting");
        this.connecting = false;
    }

    /**
     * Register a handler for a request type.
     * @param type The type of the request.
     * @param handler The handler for the request.
     */
    public void registerHandler(String type, RequestHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * Send a request to the server.
     * @param type The request to send, this a class that extends {@link RequestType}.
     */
    public void sendRequest(RequestType type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.getType());
        obj.add("data", type.serialize());
        socket.sendText(obj.toString());
    }

    /**
     * Send a request to the server and wait for a response.
     * @param request The request to send, this a class that extends {@link RequestType}.
     * @param responseType The type of the response, this a class that extends {@link RequestType}. This is only used to get the {@link dev.rvr.wsr.RequestType.TypeAdapter} of the request type and hence can be an nulled instance.
     * @return A {@link CompletableFuture} that will be completed when the server responds.
     */
    public <T extends RequestType> CompletableFuture<T> sendRequest(RequestType request, RequestType<T> responseType) {
        String ref = UUID.randomUUID().toString();
        CompletableFuture<T> future = new CompletableFuture<>();
        pendingRequests.put(ref, new AbstractMap.SimpleEntry<>(responseType, future));
        JsonObject obj = new JsonObject();
        obj.addProperty("type", request.getType());
        obj.addProperty("ref", ref);
        obj.add("data", request.serialize());
        socket.sendText(obj.toString());
        return future;
    }

    @Override
    public final void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
        connectErrors = 0;
    }

    @Override
    public final void onConnectError(WebSocket websocket, WebSocketException exception) {
        if (!connecting) return;
        connectErrorCallback.accept(exception,Math.min(60, ++connectErrors * 5));
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                connect();
            }
        }, Math.min(60, ++connectErrors * 5)*1000L);
    }

    @Override
    public final void onTextMessage(WebSocket websocket, String text) {
        JsonObject el = JsonParser.parseString(text).getAsJsonObject();
        String type = el.get("type").getAsString();
        if (type.equals("RESPONSE") && el.has("ref")) {
            String ref = el.get("ref").getAsString();
            var entry = pendingRequests.remove(ref);
            var future = entry.getValue();

            if (el.has("success") && !el.get("success").getAsBoolean()) {
                future.completeExceptionally(new WebSocketResponseException(el.get("error").getAsString(),el.get("errno").getAsInt()));
                return;
            }

            var requestType = entry.getKey();
            future.complete(requestType.getAdapter().deserialize(el.get("data"), null, null));
        } else {
            RequestHandler handler = handlers.get(type);
            if (handler == null) {
                unknownTypeCallback.accept(type);
                return;
            }
            try {
                var data = handler.getTypeAdapter().deserialize(el.get("data"), null, null);
                if (el.has("ref")) {
                    String ref = el.get("ref").getAsString();
                    JsonElement response = handler.onRequestWithResponse((RequestType) data);
                    JsonObject obj = new JsonObject();
                    obj.addProperty("type", "RESPONSE");
                    obj.addProperty("ref", ref);
                    obj.add("data", response);
                    socket.sendText(obj.toString());
                } else {
                    handler.onRequest((RequestType) data);
                }
            } catch (Exception e) {
                handlerExceptionCallback.accept(type, e);
            }
        }
    }

    @Override
    public final void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) {
        if (!connecting || !shouldReconnect.get()) return;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                connect();
            }
        }, 1000L);
    }
}
