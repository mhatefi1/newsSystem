package com.jiring.feed;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MockNewsFeed {

    private static final String SERVER_HOST = System.getenv().getOrDefault("ANALYZER_HOST", "127.0.0.1");
    private static final int SERVER_PORT = 8657;

    private static final String[] WORDS = {
            "up", "down", "rise", "fall", "good", "bad", "success", "failure", "high", "low"
    };

    private final int[] priorityWeights;

    private Socket socket;
    private PrintWriter out;
    private final ScheduledExecutorService scheduler;

    public MockNewsFeed() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // 0 appears 10 times, 1 appears 9 times... 9 appears 1 time.
        // Total elements = 10 + 9 + 8 + ... + 1 = 55
        this.priorityWeights = new int[55];
        int index = 0;
        for (int i = 0; i <= 9; i++) {
            int count = 10 - i;
            for (int j = 0; j < count; j++) {
                priorityWeights[index++] = i;
            }
        }
    }

    public void start() {
        long frequencyMs = 2000; // Default frequency
        String frequencyProp = System.getProperty("feed.frequency");
        if (frequencyProp != null && !frequencyProp.trim().isEmpty()) {
            try {
                frequencyMs = Long.parseLong(frequencyProp.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid feed.frequency format. Falling back to 2000ms.");
            }
        }

        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to News Analyzer at " + SERVER_HOST + ":" + SERVER_PORT);
            scheduler.scheduleAtFixedRate(this::sendNewsItem, 0, frequencyMs, TimeUnit.MILLISECONDS);
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        } catch (IOException e) {
            System.err.println("Failed to connect to the News Analyzer. Make sure the server is running.");
            e.printStackTrace();
        }
    }

    private void sendNewsItem() {
        String headline = generateHeadline();
        int priority = generatePriority();

        // Format: PRIORITY|HEADLINE
        String message = priority + "|" + headline;
        out.println(message);
        //System.out.println("Sent: " + message);
    }

    private String generateHeadline() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int length = random.nextInt(3, 6);

        List<String> selectedWords = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            int wordIndex = random.nextInt(WORDS.length);
            selectedWords.add(WORDS[wordIndex]);
        }

        return String.join(" ", selectedWords);
    }

    private int generatePriority() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int randomIndex = random.nextInt(priorityWeights.length);
        return priorityWeights[randomIndex];
    }

    private void stop() {
        System.out.println("\nShutting down Mock News Feed...");
        scheduler.shutdown();
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        MockNewsFeed feed = new MockNewsFeed();
        feed.start();
    }
}