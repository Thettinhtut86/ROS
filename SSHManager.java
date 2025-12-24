import com.jcraft.jsch.*;
import models.Host;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class SSHManager {

    private final HostSessionManager hostSessionManager;
    private final CacheManager cacheManager;

    public SSHManager(HostSessionManager hostSessionManager, CacheManager cacheManager) {
        this.hostSessionManager = hostSessionManager;
        this.cacheManager = cacheManager;
    }

    // -------------------- EXECUTE SINGLE COMMAND --------------------
    public String execCommand(Host host, String command, Session session) throws Exception {
        if (session == null || !session.isConnected())
            throw new IllegalStateException("SSH session not connected for host: " + host.getHost());

        ChannelExec channel = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(outputStream);
            channel.setOutputStream(outputStream);
            channel.connect();

            while (!channel.isClosed())
                Thread.sleep(50);

            return outputStream.toString("UTF-8").trim();
        } finally {
            if (channel != null && channel.isConnected())
                channel.disconnect();
        }
    }

    // -------------------- VALIDATE COMMANDS --------------------
    public Map<String, String> validateCommands(Host host, List<String> commands) {
        System.out.println("handleValidate called");
        Map<String, String> results = new LinkedHashMap<>();
        Session tmpSession = null;

        try {
            tmpSession = hostSessionManager.createNewSSHSession(host);
            final Session session = tmpSession; // effectively final copy

            if (session == null || !session.isConnected()) {
                for (String cmd : commands)
                    results.put(cmd, "<span class='text-danger'>SSH session not connected</span>");
                return results;
            }

            for (String cmd : commands) {
                try {
                    String[] parts = cmd.split("\\s+"); // also fixed regex
                    String baseCmd = parts[0];

                    // ---------- check if command exists ----------
                    String cmdPath = cacheManager.getOrLoadCommand(baseCmd, () -> {
                        try {
                            return cleanOutput(execCommand(host, "command -v " + baseCmd + " 2>/dev/null", session));
                        } catch (Exception e) {
                            return "";
                        }
                    });

                    if (cmdPath.isEmpty()) {
                        results.put(cmd, "<span class='text-danger'>Invalid command</span>");
                        continue;
                    }

                    boolean optionValid = true;
                    boolean filesExist = true;

                    // ---------- validate options ----------
                    for (String part : parts) {
                        if (part.startsWith("-")) {
                            String optionKey = baseCmd + "|" + part;
                            optionValid = cacheManager.getOrLoadOption(optionKey, () -> {
                                try {
                                    String testOutput = cleanOutput(
                                            execCommand(host, baseCmd + " " + part + " --help 2>&1", session));
                                    return !(testOutput.contains("Unrecognized option") ||
                                            testOutput.contains("invalid option"));
                                } catch (Exception e) {
                                    return false;
                                }
                            });
                            if (!optionValid)
                                break;
                        }
                    }

                    // ---------- validate paths ----------
                    for (String part : parts) {
                        if (part.startsWith("/") && !part.equals(baseCmd) && !part.startsWith("-")) {
                            filesExist = cacheManager.getOrLoadPath(part, () -> {
                                try {
                                    String pathCheck = "[ -e " + part + " ] && echo exists || echo missing";
                                    String testOutput = execCommand(host, pathCheck, session);
                                    return "exists".equals(testOutput.trim());
                                } catch (Exception e) {
                                    return false;
                                }
                            });
                            if (!filesExist)
                                break;
                        }
                    }

                    // ---------- set result ----------
                    if (!optionValid)
                        results.put(cmd, "<span class='text-warning'>Invalid option</span>");
                    else if (!filesExist)
                        results.put(cmd, "<span class='text-warning'>Path not found</span>");
                    else
                        results.put(cmd, "<span class='text-success'>Valid</span>");

                } catch (Exception e) {
                    results.put(cmd, "<span class='text-danger'>Error: " + e.getMessage() + "</span>");
                }
            }

        } catch (Exception e) {
            for (String cmd : commands)
                results.put(cmd, "<span class='text-danger'>Host connection failed</span>");
        } finally {
            if (tmpSession != null && tmpSession.isConnected()) {
                tmpSession.disconnect();
            }
        }

        return results;
    }

    // -------------------- CLEAN OUTPUT --------------------
    private String cleanOutput(String rawOutput) {
        return Arrays.stream(rawOutput.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() &&
                        !line.startsWith("Last login:") &&
                        !line.toLowerCase().contains("register this system"))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b)
                .trim();
    }
}
