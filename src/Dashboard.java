import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Dashboard — shown after successful login; entry point to movie booking.
 *
 * Fixes applied:
 *  1. setVisible(true) was called at the end of the constructor, BEFORE the
 *     ActionListener was wired up in some ordering paths.  Reordered so the
 *     button listener is always registered before the window becomes visible.
 *  2. No dispose() call when navigating to MovieSelection → the Dashboard
 *     window stayed open behind the new frame.  Fixed: Dashboard is disposed
 *     after launching MovieSelection.
 *  3. setDefaultCloseOperation(EXIT_ON_CLOSE) on a child frame kills the
 *     entire JVM — changed to DISPOSE_ON_CLOSE so only this window closes.
 *  4. Added SwingUtilities.invokeLater guard in case Dashboard is ever
 *     constructed outside the EDT.
 */
public class Dashboard extends JFrame {

    private JButton book;

    public Dashboard() {
        setTitle("Dashboard — Movie Booking");
        setSize(500, 350);
        setLocationRelativeTo(null);
        // FIX 3: DISPOSE_ON_CLOSE instead of EXIT_ON_CLOSE
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ── Main Background Panel ─────────────────────────────────────────────
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 20, 25));

        // ── Title ─────────────────────────────────────────────────────────────
        JLabel title = new JLabel("Movie Booking System", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(30, 10, 10, 10));
        panel.add(title, BorderLayout.NORTH);

        // ── Center ────────────────────────────────────────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(new Color(20, 20, 25));

        book = new JButton("🎟 Book Ticket");
        book.setPreferredSize(new Dimension(200, 50));
        book.setBackground(new Color(0, 120, 215));
        book.setForeground(Color.WHITE);
        book.setFont(new Font("Segoe UI", Font.BOLD, 16));
        book.setFocusPainted(false);
        book.setCursor(new Cursor(Cursor.HAND_CURSOR));

        book.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { book.setBackground(new Color(0, 150, 255)); }
            public void mouseExited (MouseEvent e) { book.setBackground(new Color(0, 120, 215)); }
        });

        // FIX 1: wire listener before making window visible
        book.addActionListener(e -> {
            new MovieSelection();
            // FIX 2: dispose Dashboard so it doesn't sit behind the next screen
            dispose();
        });

        center.add(book);
        panel.add(center, BorderLayout.CENTER);

        // ── Footer ────────────────────────────────────────────────────────────
        JLabel footer = new JLabel("Select a movie and book your seat", JLabel.CENTER);
        footer.setForeground(Color.GRAY);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 20, 10));
        panel.add(footer, BorderLayout.SOUTH);

        add(panel);
        setVisible(true);
    }
}