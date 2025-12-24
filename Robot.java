import com.sun.net.httpserver.HttpServer;
import com.jcraft.jsch.Session;
import com.sun.net.httpserver.HttpExchange;
import models.Command;
import models.Host;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.*;
import java.util.concurrent.*;

public class Robot {

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final HostSessionManager hostSessionManager = new HostSessionManager();
    private static final CacheManager cacheManager = new CacheManager();
    private static final SSHManager sshManager = new SSHManager(hostSessionManager, cacheManager);
    private static final WinRMManager winrmManager = new WinRMManager(hostSessionManager);

    // Store path of last uploaded file
    private static String lastUploadedFilePath = null;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);

        server.createContext("/", Robot::handleRoot);
        server.createContext("/validate", Robot::handleValidate);
        server.createContext("/execute", exchange -> {
            try {
                handleExecute(exchange);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        server.createContext("/static", Robot::handleStatic);

        server.setExecutor(executor);
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    // ---------------- ROOT ----------------
    private static void handleRoot(HttpExchange exchange) throws IOException {
        sendHtml(exchange, Helper.loadHtml("upload.html"));
    }

    // ---------------- VALIDATE ----------------
    private static void handleValidate(HttpExchange exchange) throws IOException {
            try {
        System.out.println("handleValidate called");

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(exchange, Helper.loadHtml("upload.html"));
            return;
        }

        File uploadedFile = Helper.FileUploadHelper.saveUploadedFile(exchange, "commands.xlsx");
        lastUploadedFilePath = uploadedFile.getAbsolutePath();

        List<Host> hosts;
        List<Command> commands;

        try {
            hosts = ExcelParser.parseHosts(lastUploadedFilePath);
            commands = ExcelParser.parseCommands(lastUploadedFilePath);
        } catch (Exception e) {
            sendHtml(exchange, "<p class='text-danger'>Excel parsing failed: " + e.getMessage() + "</p>");
            return;
        }

        Map<String, Map<String, String>> validationResults = new ConcurrentHashMap<>();
        List<Host> validHosts = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Host host : hosts) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Map<String, String> cmdStatus = new LinkedHashMap<>();
                boolean allValid = true;
                Session session = null;

                try {
                    if (host.isLinux()) {
                        session = hostSessionManager.createNewSSHSession(host);

                        for (Command cmd : commands) {
                            String status;
                            try {
                                status = sshManager.execCommand(host, "command -v " + cmd.getCommand() + " 2>/dev/null",
                                        session);
                                if (status.isEmpty()) {
                                    cmdStatus.put(cmd.getDescription(),
                                            "<span class='text-danger'>Invalid command</span>");
                                    allValid = false;
                                } else {
                                    // Option/path validation logic
                                    status = sshManager
                                            .validateCommands(host, Collections.singletonList(cmd.getCommand()))
                                            .get(cmd.getCommand());
                                    if (!status.contains("Valid"))
                                        allValid = false;
                                    cmdStatus.put(cmd.getDescription(), status);
                                }
                            } catch (Exception e) {
                                cmdStatus.put(cmd.getDescription(),
                                        "<span class='text-danger'>Error: " + e.getMessage() + "</span>");
                                allValid = false;
                            }
                        }
                    } else if (host.isWindows()) {
                        for (Command cmd : commands)
                            cmdStatus.put(cmd.getDescription(),
                                    "<span class='text-warning'>Windows validation not implemented</span>");
                    }

                    validationResults.put(host.getHost(), cmdStatus);
                    if (allValid)
                        validHosts.add(host);

                } catch (Exception e) {
                    for (Command cmd : commands)
                        cmdStatus.put(cmd.getDescription(), "<span class='text-danger'>Host connection failed</span>");
                    validationResults.put(host.getHost(), cmdStatus);
                } finally {
                    if (session != null && session.isConnected())
                        session.disconnect();
                }
            }, executor);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // Build HTML page with checkboxes for valid hosts
        String html = Helper.HtmlBuilder.buildValidationPageVertical(hosts, commands, validationResults, validHosts);
        sendHtml(exchange, html);
        } catch (Exception e) {
        e.printStackTrace(); // log server-side
        sendHtml(exchange, "<p class='text-danger'>Internal server error: " + e.getMessage() + "</p>");
    }
    }

    private static void handleExecute(HttpExchange exchange) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(exchange, "<p class='text-danger'>Invalid request</p>");
            return;
        }

        if (lastUploadedFilePath == null) {
            sendHtml(exchange, "<p class='text-danger'>No uploaded file found</p>");
            return;
        }

        List<String> selectedHosts = Helper.parseSelectedHosts(exchange);
        if (selectedHosts.isEmpty()) {
            sendHtml(exchange, "<p class='text-danger'>No host selected</p>");
            return;
        }

        List<Host> hosts = ExcelParser.parseHosts(lastUploadedFilePath);
        List<Command> commands = ExcelParser.parseCommands(lastUploadedFilePath);

        Map<String, Map<String, String>> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Host host : hosts) {
            if (!selectedHosts.contains(host.getHost()))
                continue;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                Map<String, String> execResults = new LinkedHashMap<>(); // Create new map for each host

                try {
                    Session session = null;
                    if (host.isLinux()) {
                        session = hostSessionManager.createNewSSHSession(host);
                    }

                    for (Command cmd : commands) {
                        try {
                            String output;
                            if (host.isLinux()) {
                                output = sshManager.execCommand(host, cmd.getCommand(), session);
                            } else if (host.isWindows()) {
                                output = winrmManager.execCommand(host, cmd.getCommand());
                            } else {
                                output = "Unknown host type";
                            }
                            execResults.put(cmd.getDescription() + " : " + cmd.getCommand(), output);
                        } catch (Exception ex) {
                            execResults.put(cmd.getDescription() + " : " + cmd.getCommand(), "Error: " + ex.getMessage());
                        }
                    }

                    // Close session after all commands for this host
                    if (session != null && session.isConnected()) {
                        session.disconnect();
                    }

                } catch (Exception e) {
                    for (Command cmd : commands) {
                        execResults.put(cmd.getDescription(), "Error: " + e.getMessage());
                    }
                }

                results.put(host.getHost(), execResults); // Put the complete results for this host
            }, executor);

            futures.add(future);
        }

        // Wait for all hosts to finish
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Build results HTML and send directly (no file writing/redirect)
        String resultsHtml = Helper.HtmlBuilder.buildExecutionPage(results);
        sendHtml(exchange, resultsHtml);
    }

    // ---------------- STATIC FILES ----------------
    private static void handleStatic(HttpExchange exchange) throws IOException {
        String path = "static" + exchange.getRequestURI().getPath().replace("/static", "");
        File file = new File(path);
        if (!file.exists()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        String mime;
        if (path.endsWith(".css")) {
            mime = "text/css";
        } else if (path.endsWith(".js")) {
            mime = "application/javascript";
        } else if (path.endsWith(".html")) {
            mime = "text/html; charset=UTF-8";
        } else {
            mime = "application/octet-stream"; // fallback
        }
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------------- HTML RESPONSE ----------------
    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
