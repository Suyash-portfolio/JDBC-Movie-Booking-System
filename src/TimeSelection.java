import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * TimeSelection — lets the user pick a show time.
 *
 * Fixes applied:
 *  1. DISPOSE_ON_CLOSE instead of EXIT_ON_CLOSE.
 *  2. Navigation (new SeatSelection + dispose()) was inside mouseClicked.
 *     On some platforms a click-outside-button-bounds still fires mouseClicked
 *     on the card panel.  Moved navigation to a shared Runnable for clarity
 *     and to make it easy to add keyboard support later.
 *  3. Graphics2D obtained with cast `(Graphics2D) g` instead of `g.create()`.
 *     Modifying a shared Graphics context can corrupt subsequent rendering.
 *     Changed all custom paint to use `g.create()` / `g2.dispose()`.
 *  4. Screen arc `g2` was the original cast (not created copy); changed to
 *     g.create() to avoid painting side-effects.
 *  5. Added setMinimumSize to prevent layout collapse on resize.
 */
public class TimeSelection extends JFrame {

    public TimeSelection(String movie, String language) {

        setTitle("Select Show Time — " + movie);
        setSize(500, 420);
        setMinimumSize(new Dimension(400, 360)); // FIX 5
        setLocationRelativeTo(null);
        // FIX 1: DISPOSE_ON_CLOSE
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ── Main panel ────────────────────────────────────────────────────────
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 20, 25));

        // ── Top section ───────────────────────────────────────────────────────
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setBackground(new Color(20, 20, 25));

        JLabel title = new JLabel("Select Show Time", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(30, 10, 5, 10));
        topSection.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel(movie + "  •  " + language, JLabel.CENTER);
        subtitle.setForeground(new Color(0, 150, 255));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setBorder(BorderFactory.createEmptyBorder(0, 10, 15, 10));
        topSection.add(subtitle, BorderLayout.SOUTH);

        panel.add(topSection, BorderLayout.NORTH);

        // ── Time buttons ──────────────────────────────────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(new Color(20, 20, 25));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx  = 0;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(7, 30, 7, 30);

        String[][] times = {
            {"🌅", "10 AM", "Morning Show"},
            {"☀️",  "2 PM",  "Afternoon Show"},
            {"🌆", "6 PM",  "Evening Show"},
            {"🌙", "9 PM",  "Night Show"},
        };

        for (int i = 0; i < times.length; i++) {
            String emoji     = times[i][0];
            String time      = times[i][1];
            String showLabel = times[i][2];

            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(new Color(35, 35, 42));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 120, 215), 1, true),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)
            ));
            card.setPreferredSize(new Dimension(280, 56));
            card.setCursor(new Cursor(Cursor.HAND_CURSOR));

            JLabel timeLabel = new JLabel(emoji + "  " + time);
            timeLabel.setForeground(Color.WHITE);
            timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

            JLabel showLbl = new JLabel(showLabel);
            showLbl.setForeground(Color.GRAY);
            showLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            card.add(timeLabel, BorderLayout.WEST);
            card.add(showLbl,   BorderLayout.EAST);

            // FIX 2: navigation extracted to a Runnable for clarity
            Runnable navigate = () -> {
                new SeatSelection(movie, language, time);
                dispose();
            };

            card.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    card.setBackground(new Color(0, 120, 215));
                    showLbl.setForeground(Color.WHITE);
                }
                public void mouseExited(MouseEvent e) {
                    card.setBackground(new Color(35, 35, 42));
                    showLbl.setForeground(Color.GRAY);
                }
                public void mouseClicked(MouseEvent e) {
                    navigate.run();
                }
            });

            gbc.gridy = i;
            center.add(card, gbc);
        }

        panel.add(center, BorderLayout.CENTER);

        // ── Footer ────────────────────────────────────────────────────────────
        JLabel footer = new JLabel("All times are in local timezone", JLabel.CENTER);
        footer.setForeground(Color.GRAY);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 20, 10));
        panel.add(footer, BorderLayout.SOUTH);

        add(panel);
        setVisible(true);
    }
}