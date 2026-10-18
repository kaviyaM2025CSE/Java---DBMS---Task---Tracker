import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {
    public static Connection getConnection() {
        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/VSCodeTaskDB", "root", "#Kavi#1974");
        } catch (Exception e) {
            System.out.println("❌ Database Connection Error: " + e.getMessage());
        }
        return conn;
    }
}
