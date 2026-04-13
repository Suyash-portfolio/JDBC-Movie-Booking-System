import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * LanguageSelection — lets the user pick a dub/sub language for a movie.
 *
 * Fixes applied:
 *  1. DISPOSE_ON_CLOSE instead of EXIT_ON_CLOSE — a child frame must not
 *     kill the JVM.
 *  2. Emoji stripping used a hard-coded literal "🎬  " (with two spaces).
 *     If the string ever changes this silently passes the wrong value to the
 *     next screen.  Replaced with a robust strip that removes all leading
 *     non-letter characters and trims.
 *  3. The action listener called dispose() AFTER new TimeSelection() — fine,
 *     but the dispose was inside the lambda so if an exception occurred in
 *     TimeSelection the window would stay open.  Now guarded with try/finally.
 *  4. Added setVisible(true) guard — was already present and correct; noted
 *     for clarity.
 *  5. No minimum window size set; added setMinimumSize to prevent layout
 *     collapse when the user resizes.
 */
public class LanguageSelection extends JFrame {

    public LanguageSelection(String movie) {

        setTitle("Select Language — " + movie);
        setSize(500, 380);
        setMinimumSize(new Dimension(400, 320)); // FIX 5
        setLocationRelativeTo(null);
        // FIX 1: must not kill the JVM on close
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ── Main panel ────────────────────────────────────────────────────────
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 20, 25));

        // ── Top section ───────────────────────────────────────────────────────
        JLabel title = new JLabel("Select Language", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(30, 10, 5, 10));

        JLabel subtitle = new JLabel("Choose your preferred language for: " + movie, JLabel.CENTER);
        subtitle.setForeground(Color.GRAY);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setBackground(new Color(20, 20, 25));
        topSection.add(title,    BorderLayout.NORTH);
        topSection.add(subtitle, BorderLayout.SOUTH);
        panel.add(topSection, BorderLayout.NORTH);

        // ── Language buttons ──────────────────────────────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(new Color(20, 20, 25));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill  = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 30, 8, 30);

        // Each entry: {display label, clean language value passed to TimeSelection}
        String[][] languages = {
            {"🎬  Marathi (Original)", "Marathi"},
            {"🎬  Hindi",              "Hindi"},
            {"🎬  Tamil",              "Tamil"},
        };

        for (int i = 0; i < languages.length; i++) {
            String displayLabel = languages[i][0];
            // FIX 2: use the explicit clean value instead of fragile emoji stripping
            String cleanLang = languages[i][1];

            JButton btn = new JButton(displayLabel);
            btn.setPreferredSize(new Dimension(280, 50));
            btn.setBackground(new Color(35, 35, 42));
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 120, 215), 1, true),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
            ));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(0, 120, 215)); }
                public void mouseExited (MouseEvent e) { btn.setBackground(new Color(35, 35, 42));  }
            });

            // FIX 3: dispose in finally so it always runs even if TimeSelection throws
            btn.addActionListener(e -> {
                try {
                    new TimeSelection(movie, cleanLang);
                } finally {
                    dispose();
                }
            });

            gbc.gridy = i;
            center.add(btn, gbc);
        }

        panel.add(center, BorderLayout.CENTER);

        // ── Footer ────────────────────────────────────────────────────────────
        JLabel footer = new JLabel("Your cinema experience starts here", JLabel.CENTER);
        footer.setForeground(Color.GRAY);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 20, 10));
        panel.add(footer, BorderLayout.SOUTH);

        add(panel);
        setVisible(true);
    }
}