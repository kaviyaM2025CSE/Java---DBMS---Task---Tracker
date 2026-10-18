import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TaskWebApp {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/get-tasks", new GetTasksHandler());
        server.createContext("/add-task.html", new StaticFileHandler());
        server.createContext("/add-task", new AddTaskHandler());
        server.createContext("/delete-task", new DeleteTaskHandler());
        server.createContext("/toggle-status", new ToggleStatusHandler());
        server.setExecutor(null);
        System.out.println("🚀 VS Code Web Application started at http://localhost:8000/");
        server.start();
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            String filePath = "web" + path;
            File file = new File(filePath);
            
            if (file.exists()) {
                byte[] content = Files.readAllBytes(Paths.get(filePath));
                exchange.sendResponseHeaders(200, content.length);
                OutputStream os = exchange.getResponseBody();
                os.write(content);
                os.close();
            } else {
                String error = "404 Not Found";
                exchange.sendResponseHeaders(404, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    static class GetTasksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder htmlRows = new StringBuilder();
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, title, status, priority FROM tasks")) {
                
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String title = rs.getString(2);
                    String status = rs.getString(3);
                    String priority = rs.getString(4);

                    // 💡 PENDING-ah irundha '✔ Mark Completed' button katanum. COMPLETED-ah irundha '↺ Mark Pending' button katanum.
                    boolean isCompleted = status.equalsIgnoreCase("COMPLETED");
                    String toggleBtnText = isCompleted ? "↺ Mark Pending" : "✔ Mark Completed";
                    String toggleBtnColor = isCompleted ? "#f39c12" : "#27ae60";

                    htmlRows.append("<tr>")
                            .append("<td>").append(id).append("</td>")
                            .append("<td>").append(title).append("</td>")
                            .append("<td><b>").append(status).append("</b></td>")
                            .append("<td>").append(priority).append("</td>")
                            .append("<td>")
                            .append("<a href='/toggle-status?id=").append(id)
                            .append("&current=").append(status)
                            .append("' class='action-link' style='color: ").append(toggleBtnColor).append("; text-decoration: none; font-weight: bold; margin-right: 10px;'>").append(toggleBtnText).append("</a> ")
                            .append("<a href='/delete-task?id=").append(id)
                            .append("' class='action-link' style='color: #e74c3c; text-decoration: none; font-weight: bold;'>🗑️ Delete</a>")
                            .append("</td>")
                            .append("</tr>");
                }
            } catch (Exception e) {
                htmlRows.append("<tr><td colspan='5'>Error loading data from database: ").append(e.getMessage()).append("</td></tr>");
            }

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlRows.toString().getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(htmlRows.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }

    static class AddTaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String formData = "";
                try (Scanner scanner = new Scanner(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                    if (scanner.hasNext()) {
                        formData = scanner.useDelimiter("\\A").next();
                    }
                } catch (Exception e) {
                    System.out.println("❌ Error reading form data: " + e.getMessage());
                }
                
                Map<String, String> inputs = parseFormData(formData);
                String rawTitle = inputs.getOrDefault("title", "New Task");
                String title = URLDecoder.decode(rawTitle, StandardCharsets.UTF_8);
                String priority = inputs.getOrDefault("priority", "MEDIUM");

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tasks (title, priority) VALUES (?, ?)")) {
                    pstmt.setString(1, title);
                    pstmt.setString(2, priority);
                    pstmt.executeUpdate();
                    System.out.println("🎉 Task inserted successfully into Database!");
                } catch (Exception e) {
                    System.out.println("❌ Database Query Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            exchange.getResponseHeaders().set("Location", "/index.html");
            exchange.sendResponseHeaders(302, -1);
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> result = new HashMap<>();
            if (formData != null && !formData.isEmpty()) {
                for (String pair : formData.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv.length > 1) {
                        result.put(kv[0], kv[1]);
                    } else if (kv.length == 1) {
                        result.put(kv[0], "");
                    }
                }
            }
            return result;
        }
    }

    static class DeleteTaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("id=")) {
                try {
                    int taskId = Integer.parseInt(query.split("=")[1]);
                    
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
                        pstmt.setInt(1, taskId);
                        pstmt.executeUpdate();
                        System.out.println("🗑️ Task deleted successfully from Database!");
                    }
                } catch (Exception e) {
                    System.out.println("❌ Error deleting task: " + e.getMessage());
                }
            }
            exchange.getResponseHeaders().set("Location", "/index.html");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    static class ToggleStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                Map<String, String> params = new HashMap<>();
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv.length > 1) params.put(kv[0], kv[1]);
                }
                
                if (params.containsKey("id") && params.containsKey("current")) {
                    try {
                        int taskId = Integer.parseInt(params.get("id"));
                        String currentStatus = params.get("current");
                        
                        // 💡 PENDING-ah irundha COMPLETED-ku maathum. COMPLETED-ah irundha PENDING-ku maathum.
                        String newStatus = currentStatus.equalsIgnoreCase("PENDING") ? "COMPLETED" : "PENDING";

                        try (Connection conn = DBConnection.getConnection();
                             PreparedStatement pstmt = conn.prepareStatement("UPDATE tasks SET status = ? WHERE id = ?")) {
                            pstmt.setString(1, newStatus);
                            pstmt.setInt(2, taskId);
                            pstmt.executeUpdate();
                            System.out.println("🔄 Task status successfully updated to " + newStatus);
                        }
                    } catch (Exception e) {
                        System.out.println("❌ Error updating status: " + e.getMessage());
                    }
                }
            }
            exchange.getResponseHeaders().set("Location", "/index.html");
            exchange.sendResponseHeaders(302, -1);
        }
    }
}