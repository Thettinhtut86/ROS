
import models.Host;
import io.cloudsoft.winrm4j.winrm.*;

public class WinRMManager {

    private final HostSessionManager hostSessionManager;

    public WinRMManager(HostSessionManager hostSessionManager) {
        this.hostSessionManager = hostSessionManager;
    }

    public String execCommand(Host host, String command) {
        WinRmTool tool = hostSessionManager.getWinRMSession(host);
        return tool.executeCommand(command).getStdOut() + tool.executeCommand(command).getStdErr();
    }
}