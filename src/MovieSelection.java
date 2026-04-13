import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.net.URL;

/**
 * MovieSelection — displays a cinematic grid of available movies.
 *
 * Fixes applied:
 *  1. setDefaultCloseOperation(EXIT_ON_CLOSE) — kills the whole JVM when this
 *     frame closes.  Changed to DISPOSE_ON_CLOSE; the Login frame (which uses
 *     EXIT_ON_CLOSE) already controls the JVM lifetime.
 *  2. Language routing: movies with hasLang=false were passing "N/A" as the
 *     language to TimeSelection, which then stored "N/A" in the DB.  For
 *     movies that have a single language declared in MOVIES data, we now pass
 *     that language directly.
 *  3. PosterPanel.paintComponent() queried image dimensions every repaint
 *     with getWidth(null)/getHeight(null) — returns -1 until MediaTracker
 *     loads the image, causing a divide-by-zero guard path.  Fixed: cache
 *     dimensions once in the constructor via ImageObserver/MediaTracker.
 *  4. bookBtn (BOOK NOW overlay) was added to posterPane but its mouse click
 *     had no listener — clicking the button did nothing.  Fixed: added a
 *     MouseListener to bookBtn that fires the same navigation logic as the
 *     card click.
 *  5. mouseExited mis-fire: when the cursor moved from the card to a child
 *     component (e.g. posterImg), mouseExited fired and hid the BOOK NOW
 *     button.  The existing fix (convert point to card coords) was already
 *     correct — confirmed and kept.
 *  6. buildInfoStrip() info panel had an empty paintComponent override that
 *     suppressed the parent fill, making the card background bleed through
 *     incorrectly on some LAFs.  Removed the empty override so the opaque
 *     flag works as intended.
 *  7. Inner class BookBtn had no preferred/minimum size set, so on some JDKs
 *     it rendered at 0×0.  The bounds are set by ComponentListener in the
 *     parent; added a non-zero preferred size as a fallback.
 */
public class MovieSelection extends JFrame {

    // ── Cinematic dark palette ─────────────────────────────────────────────────
    private static final Color BG         = new Color(10, 10, 15);
    private static final Color CARD_BG    = new Color(19, 19, 28);
    private static final Color CARD_HOVER = new Color(26, 26, 40);
    private static final Color GOLD       = new Color(201, 168, 76);
    private static final Color GOLD_LIGHT = new Color(230, 201, 122);
    private static final Color TEXT_COL   = new Color(240, 238, 232);
    private static final Color MUTED      = new Color(122, 120, 130);
    private static final Color ACCENT     = new Color(230, 60, 60);
    private static final Color BORDER_C   = new Color(255, 255, 255, 18);

    // ── Movie data ─────────────────────────────────────────────────────────────
    // Columns: {name, imgPath, duration, cert, language, genre, rating, badge, hasLang}
    // language = the single language used when hasLang=false (no language-selection screen)
    private static final String[][] MOVIES = {
        {"Dhurandhar 2",         "/img/dhurandhar.jpeg",  "2h 18m", "UA", "Hindi",        "Action · Thriller",  "8.4", "NOW PLAYING", "false"},
        {"Raja Shivaji",         "/img/raja shivaji.jpg", "2h 45m", "UA", "Marathi/Hindi", "Historical · Epic",  "9.1", "NEW",         "true"},
        {"Aata Thambayach Naay", "/img/atn.jpg",          "2h 05m", "U",  "Marathi",       "Drama · Family",     "7.8", "NOW PLAYING", "false"},
        {"Kantara",              "/img/kantara.jpg",      "2h 30m", "A",  "Kannada",       "Mythology · Action", "9.3", "FAN FAVE",    "false"},
    };

    public MovieSelection() {
        setTitle("Select Movie");
        setSize(1200, 660);
        setMinimumSize(new Dimension(960, 560));
        setLocationRelativeTo(null);
        // FIX 1: DISPOSE_ON_CLOSE — don't kill the JVM from a child frame
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // ── Root panel ─────────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setOpaque(false);
        setContentPane(root);

        root.add(buildPageHeader(), BorderLayout.NORTH);

        // ── 4 cards in one row ─────────────────────────────────────────────────
        JPanel grid = new JPanel(new GridLayout(1, 4, 16, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(14, 26, 26, 26));

        for (String[] movie : MOVIES) {
            grid.add(buildCard(movie));
        }

        root.add(grid, BorderLayout.CENTER);
        setVisible(true);
    }

    // ── Page header ───────────────────────────────────────────────────────────
    private JPanel buildPageHeader() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(255, 255, 255, 18));
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(26, 28, 14, 28));

        JLabel eyebrow = new JLabel("NOW SHOWING");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 10));
        eyebrow.setForeground(GOLD);
        eyebrow.setBorder(new EmptyBorder(0, 0, 5, 0));

        JLabel title = new JLabel("What are you watching tonight?");
        title.setFont(new Font("Serif", Font.BOLD, 28));
        title.setForeground(TEXT_COL);

        JLabel sub = new JLabel("Select a film to choose your seats and showtime");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(MUTED);
        sub.setBorder(new EmptyBorder(4, 0, 0, 0));

        panel.add(eyebrow);
        panel.add(title);
        panel.add(sub);
        return panel;
    }

    // ── Build one movie card ───────────────────────────────────────────────────
    private JPanel buildCard(String[] data) {
        String  name    = data[0];
        String  path    = data[1];
        String  dur     = data[2];
        String  cert    = data[3];
        String  lang    = data[4]; // FIX 2: use declared language, not "N/A"
        String  genre   = data[5];
        String  rating  = data[6];
        String  badge   = data[7];
        boolean hasLang = Boolean.parseBoolean(data[8]);

        RoundedCard card = new RoundedCard(12, CARD_BG, CARD_HOVER, BORDER_C, GOLD);
        card.setLayout(new BorderLayout(0, 0));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLayeredPane posterPane = new JLayeredPane();
        posterPane.setPreferredSize(new Dimension(0, 290));
        posterPane.setOpaque(false);

        PosterPanel posterImg = new PosterPanel(loadImage(path), name);
        posterPane.add(posterImg, JLayeredPane.DEFAULT_LAYER);

        GradientPanel grad = new GradientPanel();
        posterPane.add(grad, Integer.valueOf(100));

        JLabel badgeLbl  = makeBadge(badge);
        JLabel ratingLbl = makeRatingChip(rating);
        posterPane.add(badgeLbl,  Integer.valueOf(200));
        posterPane.add(ratingLbl, Integer.valueOf(200));

        // FIX 7: give BookBtn a non-zero preferred size so it's never invisible
        BookBtn bookBtn = new BookBtn();
        bookBtn.setPreferredSize(new Dimension(116, 34));
        bookBtn.setVisible(false);
        posterPane.add(bookBtn, Integer.valueOf(300));

        posterPane.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = posterPane.getWidth();
                int h = posterPane.getHeight();
                posterImg.setBounds(0, 0, w, h);
                grad.setBounds(0, 0, w, h);
                badgeLbl.setBounds(10, 10, 118, 21);
                ratingLbl.setBounds(w - 68, 10, 58, 25);
                int bw = 116, bh = 34;
                bookBtn.setBounds((w - bw) / 2, h - bh - 12, bw, bh);
            }
        });

        card.add(posterPane, BorderLayout.CENTER);
        card.add(buildInfoStrip(name, dur, cert, lang, genre, hasLang), BorderLayout.SOUTH);

        // ── Shared navigation logic ───────────────────────────────────────────
        Runnable navigate = () -> {
            if (hasLang) {
                new LanguageSelection(name);
            } else {
                // FIX 2: pass the movie's own declared language, not "N/A"
                new TimeSelection(name, lang);
            }
            dispose();
        };

        // ── Mouse events on card ──────────────────────────────────────────────
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setHovered(true);
                posterImg.setZoomed(true);
                bookBtn.setVisible(true);
            }
            @Override public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), card);
                if (!card.contains(p)) {
                    card.setHovered(false);
                    posterImg.setZoomed(false);
                    bookBtn.setVisible(false);
                }
            }
            @Override public void mouseClicked(MouseEvent e) {
                navigate.run();
            }
        };

        card.addMouseListener(ma);
        posterPane.addMouseListener(ma);

        // FIX 4: BookBtn click was never wired — add its own listener
        bookBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                navigate.run();
            }
        });

        return card;
    }

    // ── Info strip ────────────────────────────────────────────────────────────
    private JPanel buildInfoStrip(String name, String dur, String cert,
                                   String lang, String genre, boolean hasLang) {
        // FIX 6: removed empty paintComponent override; opaque=false lets the
        //         card's painted background show through correctly
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(11, 13, 13, 13));

        JLabel titleLbl = new JLabel(name);
        titleLbl.setFont(new Font("Serif", Font.BOLD, 14));
        titleLbl.setForeground(TEXT_COL);
        titleLbl.setAlignmentX(0f);

        JLabel metaLbl = new JLabel(dur + "  ·  " + cert + "  ·  " + lang);
        metaLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        metaLbl.setForeground(MUTED);
        metaLbl.setAlignmentX(0f);
        metaLbl.setBorder(new EmptyBorder(3, 0, 6, 0));

        JLabel genreTag = new JLabel(genre) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(201, 168, 76, 28));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                g2.setColor(new Color(201, 168, 76, 65));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
                g2.setColor(GOLD);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                return new Dimension(fm.stringWidth(getText()) + 20, 22);
            }
        };
        genreTag.setFont(new Font("SansSerif", Font.BOLD, 10));
        genreTag.setOpaque(false);
        genreTag.setAlignmentX(0f);

        info.add(titleLbl);
        info.add(metaLbl);
        info.add(genreTag);

        if (hasLang) {
            JLabel hint = new JLabel("  Multiple languages available");
            hint.setFont(new Font("SansSerif", Font.PLAIN, 10));
            hint.setForeground(MUTED);
            hint.setAlignmentX(0f);
            hint.setBorder(new EmptyBorder(5, 0, 0, 0));
            info.add(hint);
        }
        return info;
    }

    // ── Badge label ───────────────────────────────────────────────────────────
    private JLabel makeBadge(String text) {
        boolean red = text.contains("NOW");
        Color bg = red ? ACCENT : GOLD;
        Color fg = red ? Color.WHITE : new Color(10, 10, 15);

        JLabel lbl = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(fg);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        lbl.setFont(new Font("SansSerif", Font.BOLD, 9));
        lbl.setOpaque(false);
        return lbl;
    }

    // ── Rating chip ───────────────────────────────────────────────────────────
    private JLabel makeRatingChip(String rating) {
        JLabel lbl = new JLabel("★ " + rating) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 170));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(201, 168, 76, 110));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(GOLD_LIGHT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setOpaque(false);
        return lbl;
    }

    // ── Image loader ──────────────────────────────────────────────────────────
    private ImageIcon loadImage(String path) {
        try {
            URL url = getClass().getResource(path);
            if (url == null) return null;
            ImageIcon icon = new ImageIcon(url);
            // FIX 3: force the image to fully load now so getWidth/getHeight
            //        never return -1 during painting
            icon.getImage().flush();
            MediaTracker mt = new MediaTracker(this);
            mt.addImage(icon.getImage(), 0);
            mt.waitForID(0);
            return icon;
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INNER CLASSES
    // ─────────────────────────────────────────────────────────────────────────

    /** Rounded card with hover glow and clipped children */
    static class RoundedCard extends JPanel {
        private final int r;
        private final Color normalBg, hoverBg, normalBorder, hoverBorder;
        private boolean hovered;

        RoundedCard(int r, Color normalBg, Color hoverBg, Color normalBorder, Color hoverBorder) {
            this.r = r;
            this.normalBg = normalBg; this.hoverBg = hoverBg;
            this.normalBorder = normalBorder; this.hoverBorder = hoverBorder;
            setOpaque(false);
        }

        void setHovered(boolean h) { hovered = h; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? hoverBg : normalBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
            if (hovered) {
                GradientPaint glow = new GradientPaint(
                    getWidth() / 2f, getHeight() - 80, new Color(201, 168, 76, 40),
                    getWidth() / 2f, getHeight(),      new Color(201, 168, 76, 0));
                g2.setPaint(glow);
                g2.fillRoundRect(0, getHeight() - 80, getWidth(), 80, r, r);
            }
            g2.dispose();
        }

        @Override protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), r, r));
            super.paintChildren(g2);
            g2.dispose();
        }

        @Override protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? hoverBorder : normalBorder);
            g2.setStroke(new BasicStroke(hovered ? 1.5f : 1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            g2.dispose();
        }
    }

    /** Draws the movie poster image with object-fit:cover + optional zoom */
    static class PosterPanel extends JPanel {
        private final Image  image;
        private final String fallbackName;
        // FIX 3: cache image dimensions so paint never sees -1
        private final int    imgW, imgH;
        private boolean zoomed = false;

        PosterPanel(ImageIcon icon, String fallbackName) {
            this.fallbackName = fallbackName;
            if (icon != null) {
                this.image = icon.getImage();
                this.imgW  = icon.getIconWidth();
                this.imgH  = icon.getIconHeight();
            } else {
                this.image = null;
                this.imgW  = 0;
                this.imgH  = 0;
            }
            setOpaque(true);
            setBackground(new Color(22, 22, 35));
        }

        void setZoomed(boolean z) { zoomed = z; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (image != null && imgW > 0 && imgH > 0) {
                int lw = getWidth(), lh = getHeight();
                float scale = Math.max((float) lw / imgW, (float) lh / imgH);
                if (zoomed) scale *= 1.045f;
                int dw = Math.round(imgW * scale);
                int dh = Math.round(imgH * scale);
                int dx = (lw - dw) / 2;
                g2.drawImage(image, dx, 0, dw, dh, null);
            } else {
                g2.setColor(new Color(32, 32, 48));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 22));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(fallbackName,
                    (getWidth() - fm.stringWidth(fallbackName)) / 2,
                    getHeight() / 2);
            }
            g2.dispose();
        }
    }

    /** Bottom gradient overlay */
    static class GradientPanel extends JPanel {
        GradientPanel() { setOpaque(false); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            GradientPaint grad = new GradientPaint(
                0, getHeight() * 0.42f, new Color(10, 10, 15, 0),
                0, getHeight(),         new Color(10, 10, 15, 200));
            g2.setPaint(grad);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** Gold "BOOK NOW" pill button */
    static class BookBtn extends JPanel {
        BookBtn() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(201, 168, 76));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(new Color(10, 10, 15));
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            String label = "BOOK NOW";
            g2.drawString(label,
                (getWidth() - fm.stringWidth(label)) / 2,
                (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ── Entry point (for standalone testing only) ─────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new MovieSelection();
        });
    }
}