import com.sun.net.httpserver.HttpExchange;

import models.*;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Helper {

    // ---------------- File Upload ----------------
    public static class FileUploadHelper {

        public static File saveUploadedFile(HttpExchange exchange, String filename) throws IOException {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                throw new IOException("Invalid upload");
            }

            String boundary = contentType.split("boundary=")[1];
            InputStream is = exchange.getRequestBody();
            byte[] body = toByteArray(is);

            String content = new String(body, "ISO-8859-1");
            String[] parts = content.split("--" + boundary);

            for (String part : parts) {
                if (part.contains("filename=\"")) {
                    int start = part.indexOf("\r\n\r\n") + 4;
                    int end = part.lastIndexOf("\r\n");
                    byte[] fileData = part.substring(start, end).getBytes("ISO-8859-1");

                    // Create resource directory if not exists
                    Path resourceDir = Paths.get("resource");
                    if (!Files.exists(resourceDir))
                        Files.createDirectories(resourceDir);

                    // Add timestamp to filename
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String savedFilename = resourceDir.resolve(timestamp + "_" + filename).toString();

                    File tempFile = new File(savedFilename);
                    Files.write(tempFile.toPath(), fileData);
                    return tempFile;
                }
            }

            throw new IOException("No file found in upload");
        }
    }

    // ---------------- Form Parser ----------------
    public static Map<String, List<String>> parseFormMulti(HttpExchange exchange) throws IOException {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

        if (contentType != null && contentType.contains("multipart/form-data")) {
            String boundary = contentType.split("boundary=")[1];
            byte[] bodyBytes = toByteArray(exchange.getRequestBody());
            String content = new String(bodyBytes, "ISO-8859-1");

            String[] parts = content.split("--" + boundary);
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.contains("Content-Disposition") && !part.contains("filename=\"")) {
                    String[] lines = part.split("\r\n");
                    String nameLine = null;
                    for (int j = 0; j < lines.length; j++) {
                        if (lines[j].startsWith("Content-Disposition")) {
                            nameLine = lines[j];
                            break;
                        }
                    }
                    if (nameLine != null) {
                        String name = nameLine.replaceAll(".*name=\"([^\"]+)\".*", "$1");
                        int start = part.indexOf("\r\n\r\n") + 4;
                        int end = part.lastIndexOf("\r\n");
                        if (start >= 4 && end > start) {
                            String value = part.substring(start, end);
                            value = URLDecoder.decode(value, "UTF-8");

                            List<String> list = map.get(name);
                            if (list == null) {
                                list = new ArrayList<String>();
                                map.put(name, list);
                            }
                            list.add(value);
                        }
                    }
                }
            }
        } else {
            String body = toString(exchange.getRequestBody(), "UTF-8");
            String[] pairs = body.split("&");
            for (int i = 0; i < pairs.length; i++) {
                String[] kv = pairs[i].split("=");
                if (kv.length == 2) {
                    String key = URLDecoder.decode(kv[0], "UTF-8");
                    String value = URLDecoder.decode(kv[1], "UTF-8");

                    List<String> list = map.get(key);
                    if (list == null) {
                        list = new ArrayList<String>();
                        map.put(key, list);
                    }
                    list.add(value);
                }
            }
        }

        return map;
    }

    // ---------------- HTML Builder ----------------
    public static class HtmlBuilder {

        public static String buildValidationPageVertical(
                List<Host> hosts,
                List<Command> commands,
                Map<String, Map<String, String>> validationResults,
                List<Host> validHosts) {

            StringBuilder sb = new StringBuilder();
            sb.append("<form id='executeForm' action='/execute' method='post'>");

            for (Host host : hosts) {
                boolean isValid = validHosts.contains(host);
                sb.append("<div class='card mb-3'>");
                sb.append("<div class='card-header'>");
                sb.append(isValid ? "<input type='checkbox' name='host' value='" + host.getHost() + "'> " : "");
                sb.append("<strong>").append(host.getHost()).append(" (").append(host.getType()).append(")</strong>");
                sb.append("</div>");
                sb.append("<div class='card-body'>");
                sb.append("<ul class='list-group'>");

                Map<String, String> cmdStatus = validationResults.get(host.getHost());
                for (Command cmd : commands) {
                    sb.append("<li class='list-group-item'>")
                            .append("<strong>").append(cmd.getCommand()).append(":</strong> ")
                            .append(cmdStatus.get(cmd.getDescription()))
                            .append("</li>");
                }

                sb.append("</ul>");
                sb.append("<div id='result_" + host.getHost() + "' class='mt-2'></div>"); // Execution result
                                                                                          // placeholder
                sb.append("</div></div>");
            }

            sb.append("<button type='submit' class='btn btn-success'>Run Commands</button></form>");
            return sb.toString();
        }

        public static String buildExecutionPage(Map<String, Map<String, String>> results) {
            StringBuilder sb = new StringBuilder();

            // Start with complete HTML structure
            sb.append("<!DOCTYPE html>");
            sb.append("<html lang='en'>");
            sb.append("<head>");
            sb.append("<meta charset='UTF-8'>");
            sb.append("<title>Execution Results</title>");
            sb.append("<link href='/static/css/bootstrap.min.css' rel='stylesheet'>");
            sb.append("<style>");
            sb.append("body { background-color: #f8f9fa; padding: 20px; }");
            sb.append(".container { max-width: 1200px; margin: 0 auto; }");
            sb.append(
                    "pre { white-space: pre-wrap; word-wrap: break-word; background: #f1f1f1; padding: 10px; border-radius: 6px; margin: 5px 0; }");
            sb.append(".card-header { font-weight: bold; }");
            sb.append(".list-group-item { border: 1px solid rgba(0,0,0,.125); }");
            sb.append("</style>");
            sb.append("</head>");
            sb.append("<body>");

            sb.append("<div class='container'>");
            sb.append("<div class='bg-white p-4 rounded shadow'>");
            sb.append("<h3 class='mb-4 text-center'>Execution Results</h3>");

            // Add the results content
            for (String host : results.keySet()) {
                sb.append("<div class='card mb-4'>");
                sb.append("<div class='card-header bg-primary text-white'>").append(host).append("</div>");
                sb.append("<div class='card-body'><ul class='list-group list-group-flush'>");

                Map<String, String> cmdResults = results.get(host);
                for (String cmdDesc : cmdResults.keySet()) {
                    String result = cmdResults.get(cmdDesc);
                    sb.append("<li class='list-group-item'>")
                            .append("<strong class='text-primary'>").append(cmdDesc).append(":</strong><br>")
                            .append("<pre class='mt-2'>").append(escapeHtml(result)).append("</pre>")
                            .append("</li>");
                }

                sb.append("</ul></div></div>");
            }

            // Add back button
            sb.append("<div class='text-center mt-4'>");
            sb.append("<a href='/' class='btn btn-primary btn-lg'>Upload Another File</a>");
            sb.append("</div>");

            sb.append("</div>"); // Close bg-white
            sb.append("</div>"); // Close container
            sb.append("</body>");
            sb.append("</html>");

            return sb.toString();
        }
        
        private static String escapeHtml(String text) {
            if (text == null)
                return "";
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }

    }
    
    public static List<String> parseSelectedHosts(HttpExchange exchange) throws IOException {
        Map<String, List<String>> formData = parseFormMulti(exchange);
        List<String> selectedHosts = formData.get("host");
        if (selectedHosts == null) {
            selectedHosts = new ArrayList<String>();
        }
        return selectedHosts;
    }

    // ---------------- Load HTML ----------------
    public static String loadHtml(String filename) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get("static/html/" + filename));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static String toString(InputStream in, String charset) throws IOException {
        byte[] bytes = toByteArray(in);
        return new String(bytes, charset);
    }
}


