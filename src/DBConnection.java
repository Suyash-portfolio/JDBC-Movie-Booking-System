import java.sql.*;

/**
 * DBConnection — singleton-style utility for obtaining a MySQL connection.
 *
 * Fixes applied:
 *  1. Plain-text password left in source — moved to constants with a comment
 *     prompting externalisation (env var / config file).
 *  2. getConnection() returned null silently on failure; callers crashed with
 *     NullPointerException.  Now prints a descriptive message and still returns
 *     null so callers can check explicitly.
 *  3. Class.forName() is redundant with modern JDBC 4+ drivers (auto-loaded via
 *     ServiceLoader) but kept for maximum compatibility with older JDKs.
 *  4. Added isValid()/isClosed() helper so the rest of the app can cheaply test
 *     that a connection is still alive before using it.
 */
public class DBConnection {

    // ── Connection parameters ─────────────────────────────────────────────────
    // TODO: Replace these hard-coded values with System.getenv() or a
    //       properties file so credentials are never committed to source control.
    private static final String URL      = "jdbc:mysql://localhost:3306/movie_booking_system"
                                           + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER  = "root";
    private static final String DB_PASS  = "root"; // change to match your MySQL password

    /**
     * Returns a new Connection on success, or {@code null} if the connection
     * could not be established.  The caller is responsible for closing it.
     */
    public static Connection getConnection() {
        try {
            // Optional for JDBC 4+, but harmless and helps on older runtimes.
            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection con = DriverManager.getConnection(URL, DB_USER, DB_PASS);
            return con;

        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] MySQL JDBC driver not found on classpath.");
            System.err.println("  → Add mysql-connector-j-*.jar to your project libraries.");
            e.printStackTrace();
            return null;

        } catch (SQLException e) {
            System.err.println("[DBConnection] Failed to connect to database.");
            System.err.println("  URL  : " + URL);
            System.err.println("  User : " + DB_USER);
            System.err.println("  Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convenience method — returns true if {@code con} is non-null, open, and
     * responds within 2 seconds.  Use this before every DB operation in long-
     * lived frames (e.g. SeatSelection) to avoid stale-connection crashes.
     */
    public static boolean isAlive(Connection con) {
        try {
            return con != null && !con.isClosed() && con.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}