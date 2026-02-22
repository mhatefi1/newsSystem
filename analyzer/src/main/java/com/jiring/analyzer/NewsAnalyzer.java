package com.jiring.analyzer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NewsAnalyzer {

    private static final int PORT = 8657;

    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "up", "rise", "good", "success", "high"
    ));

    private final ConcurrentLinkedQueue<NewsItem> positiveNewsQueue = new ConcurrentLinkedQueue<>();

    private final ScheduledExecutorService reporterScheduler = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();

    public void start() {
        reporterScheduler.scheduleAtFixedRate(this::analyzeAndReport, 10, 10, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("News Analyzer listening on port " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New Mock News Feed connected: " + clientSocket.getRemoteSocketAddress());

                clientThreadPool.submit(() -> handleClientConnection(clientSocket));
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void handleClientConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                processNewsMessage(line);
            }
        } catch (Exception e) {
            System.out.println("A Mock News Feed disconnected.");
        }
    }

    private void processNewsMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length != 2) return;

        try {
            int priority = Integer.parseInt(parts[0]);
            String headline = parts[1];

            if (isOverallPositive(headline)) {
                positiveNewsQueue.add(new NewsItem(priority, headline));
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private boolean isOverallPositive(String headline) {
        String[] words = headline.split(" ");
        int positiveCount = 0;

        for (String word : words) {
            if (POSITIVE_WORDS.contains(word)) {
                positiveCount++;
            }
        }

        return positiveCount > (words.length / 2.0);
    }

    private void analyzeAndReport() {
        List<NewsItem> itemList = new ArrayList<>();
        NewsItem item;
        while ((item = positiveNewsQueue.poll()) != null) {
            itemList.add(item);
        }

        int count = itemList.size();
        System.out.println("\n=== Report ===");
        System.out.println("Total positive news items: " + count);

        if (count > 0) {
            List<String> topHeadlines = itemList.stream()
                    .sorted(Comparator.comparingInt(NewsItem::getPriority).reversed())
                    .map(NewsItem::getHeadline)
                    .distinct()
                    .limit(3)
                    .collect(Collectors.toList());

            System.out.println("Top 3 unique high-priority headlines:");
            topHeadlines.forEach(h -> System.out.println(" -> " + h));
        }
    }

    private static class NewsItem {
        private final int priority;
        private final String headline;

        public NewsItem(int priority, String headline) {
            this.priority = priority;
            this.headline = headline;
        }

        public int getPriority() {
            return priority;
        }

        public String getHeadline() {
            return headline;
        }
    }

    public static void main(String[] args) {
        new NewsAnalyzer().start();
    }
}