package models;

public class Host {
    String host;
    String type;
    String user;
    String pass;

    public Host(String host, String user, String pass, String type) {
        this.host = host;
        this.user = user;
        this.pass = pass;
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }
    
    public boolean isLinux() {
        return type != null && type.trim().equalsIgnoreCase("linux");
    }

    public boolean isWindows() {
        return type != null && type.trim().equalsIgnoreCase("winrm");
    }
}
