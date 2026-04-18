package com.playmonumenta.chatlogger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayGeneric;
import com.playmonumenta.networkrelay.NetworkRelayMessageEventGeneric;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.logging.log4j.LogManager;

public class ChatLoggerApp {
    private static final String NETWORK_CHAT_MESSAGE = "com.playmonumenta.networkchat.Message";
    private static final org.apache.logging.log4j.Logger CHAT_LOG = LogManager.getLogger("NetworkChatLog");
    private static final Logger LOGGER = Logger.getLogger("ChatLogger");

    public static void main(String[] args) throws InterruptedException {
        String configPath = System.getenv("CHAT_LOGGER_CONFIG");
        if (configPath == null || configPath.isEmpty()) {
            configPath = "config/network_relay.yml";
        }
        File configFile = new File(configPath);

        Map<Integer, List<Consumer<Object>>> listeners = new TreeMap<>(Comparator.reverseOrder());
        BiConsumer<Integer, Consumer<Object>> register = (priority, consumer) ->
            listeners.computeIfAbsent(priority, k -> new ArrayList<>()).add(consumer);
        Consumer<Object> dispatch = event ->
            listeners.values().forEach(list -> list.forEach(c -> {
                try {
                    c.accept(event);
                } catch (Exception e) {
                    LOGGER.warning("Error in event listener: " + e.getMessage());
                }
            }));

        register.accept(0, event -> {
            if (event instanceof NetworkRelayMessageEventGeneric e) {
                if (NETWORK_CHAT_MESSAGE.equals(e.getChannel())) {
                    handleMessage(e.getSource(), e.getData());
                }
            }
        });

        NetworkRelayGeneric relay = new NetworkRelayGeneric(
            LOGGER,
            configFile,
            ChatLoggerApp.class,
            "config.yml",
            "logger",
            register,
            dispatch
        );

        relay.setServerFinishedStarting();
        LOGGER.info("Chat logger started, listening for messages...");

        Thread mainThread = Thread.currentThread();
        AtomicBoolean shuttingDown = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shuttingDown.compareAndSet(false, true)) {
                LOGGER.info("Shutting down...");
                relay.onDisable();
            }
            mainThread.interrupt();
        }));

        try {
            mainThread.join();
        } catch (InterruptedException e) {
            // Interrupted by shutdown hook — normal exit path
        }
    }

    private static void handleMessage(String source, JsonObject data) {
        try {
            JsonElement senderNameEl = data.get("senderName");
            String senderName = senderNameEl != null ? senderNameEl.getAsString() : "?";

            JsonElement messageEl = data.get("message");
            String messageText;
            if (messageEl != null) {
                Component component = GsonComponentSerializer.gson().deserializeFromTree(messageEl);
                messageText = PlainTextComponentSerializer.plainText().serialize(component);
            } else {
                messageText = "[no message]";
            }

            String logLine = senderName + ": " + messageText;
            if (source != null && !source.isEmpty()) {
                logLine = "[" + source + "] " + logLine;
            }
            CHAT_LOG.info(logLine);
        } catch (Exception e) {
            LOGGER.warning("Failed to handle chat message: " + e.getMessage());
        }
    }
}
