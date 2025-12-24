import models.Host;
import com.jcraft.jsch.*;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.*;


import java.util.*;
import java.util.concurrent.*;

public class HostSessionManager {

    private final Map<String, Session> sshSessions = new ConcurrentHashMap<>();
    private final Map<String, WinRmTool> winrmSessions = new ConcurrentHashMap<>();
    private final Map<String, WinRmClientContext> winrmContexts = new ConcurrentHashMap<>();

    // -------------------- SSH --------------------
    public Session createNewSSHSession(Host host) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(host.getUser(), host.getHost(), 22);
        session.setPassword(host.getPass());
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(10000);
        return session;
    }

    public void closeSSHSession(String host) {
        Session session = sshSessions.remove(host);
        if (session != null && session.isConnected())
            session.disconnect();
    }

    // -------------------- WinRM --------------------
    public WinRmTool getWinRMSession(Host host) {
        if (winrmSessions.containsKey(host.getHost()))
            return winrmSessions.get(host.getHost());

        WinRmClientContext context = WinRmClientContext.newInstance();
        WinRmTool tool = WinRmTool.Builder.builder(host.getHost(), host.getUser(), host.getPass())
                .port(5985)
                .useHttps(false)
                .disableCertificateChecks(true)
                .context(context)
                .build();
        winrmSessions.put(host.getHost(), tool);
        winrmContexts.put(host.getHost(), context);
        return tool;
    }

    public void closeWinRMSession(String host) {
        WinRmTool tool = winrmSessions.remove(host);
        WinRmClientContext context = winrmContexts.remove(host);
        if (context != null)
            context.shutdown();
    }

    public void closeAll() {
        sshSessions.keySet().forEach(this::closeSSHSession);
        winrmSessions.keySet().forEach(this::closeWinRMSession);
    }
}
