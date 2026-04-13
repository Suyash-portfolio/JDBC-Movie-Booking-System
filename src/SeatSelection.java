import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.sql.*;

/**
 * SeatSelection — displays a 5×5 seat grid and handles booking.
 *
 * Fixes applied:
 *  1. con = DBConnection.getConnection() — if this returns null every subsequent
 *     DB call throws NullPointerException.  Added a null-check that disables
 *     the Confirm button and shows a warning when the DB is unavailable.
 *  2. PreparedStatement / ResultSet were never closed in isBooked() and book().
 *     Fixed with try-with-resources.
 *  3. book() held a long-lived Connection opened in the constructor.  If the
 *     app is idle for a while MySQL times out the connection (default 8 hours)
 *     and every subsequent call fails silently.  Fixed by using
 *     DBConnection.isAlive() before each operation and reconnecting if needed.
 *  4. btn.removeMouseListener(btn.getMouseListeners()[0]) — after a successful
 *     booking this was called to prevent re-interaction.  If no mouse listener
 *     existed (edge case) it throws ArrayIndexOutOfBoundsException.  Fixed:
 *     simply disable the button (already done one line later) which naturally
 *     blocks all interactions; the removeMouseListener call is removed.
 *  5. buildSeatsPanel() performed one DB query per seat (25 queries) before
 *     the frame was visible, blocking the EDT.  Fixed: seat status is loaded on
 *     a background SwingWorker so the frame appears immediately, then seats
 *     update to their booked/available state asynchronously.
 *  6. DISPOSE_ON_CLOSE instead of EXIT_ON_CLOSE.
 *  7. In the screen arc paintComponent, g2 was obtained via a raw cast
 *     (Graphics2D) g — modifying the shared Graphics context is unsafe.
 *     Fixed to use g.create() / g2.dispose().
 *  8. Legend dot panel called super.paintComponent(g) via a raw cast reference;
 *     same issue fixed with g.create().
 */
public class SeatSelection extends JFrame {

    private Connection con;
    private final String movie, language, time;

    // ── Color palette ──────────────────────────────────────────────────────────
    static final Color BG_DARK       = new Color(20, 20, 25);
    static final Color BG_CARD       = new Color(35, 35, 42);
    static final Color ACCENT_BLUE   = new Color(0, 120, 215);
    static final Color ACCENT_BRIGHT = new Color(0, 170, 255);
    static final Color SEAT_AVAIL    = new Color(39, 174, 96);
    static final Color SEAT_BOOKED   = new Color(192, 57, 43);
    static final Color SEAT_HOVER    = new Color(52, 152, 219);
    static final Color SEAT_SELECTED = new Color(243, 156, 18);
    static final Color TEXT_WHITE    = new Color(240, 240, 245);
    static final Color TEXT_GRAY     = new Color(140, 140, 155);
    static final Color SCREEN_COLOR  = new Color(0, 180, 255);

    // ── State ──────────────────────────────────────────────────────────────────
    private JButton  selectedSeatBtn = null;
    private int      selectedSeatNo  = -1;

    private JLabel   summaryMovie, summaryLang, summaryTime, summarySeat, summaryPrice;
    private JButton  confirmBtn;

    private final JButton[]  seatButtons  = new JButton[25];
    private final boolean[]  bookedStatus = new boolean[25];

    public SeatSelection(String movie, String language, String time) {
        this.movie    = movie;
        this.language = language;
        this.time     = time;

        setTitle("🎬 Seat Selection — " + movie);
        setSize(820, 760);
        setLocationRelativeTo(null);
        // FIX 6: DISPOSE_ON_CLOSE
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // FIX 1: null-check connection early
        con = DBConnection.getConnection();

        // ── Root Panel ─────────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);

        root.add(buildHeader(),    BorderLayout.NORTH);

        JPanel centerArea = new JPanel(new BorderLayout(0, 10));
        centerArea.setBackground(BG_DARK);
        centerArea.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        centerArea.add(buildScreenPanel(), BorderLayout.NORTH);
        centerArea.add(buildSeatsPanel(),  BorderLayout.CENTER);
        centerArea.add(buildLegendPanel(), BorderLayout.SOUTH);

        root.add(centerArea,       BorderLayout.CENTER);
        root.add(buildSidebar(),   BorderLayout.EAST);

        add(root);
        setVisible(true);

        // FIX 5: load seat statuses on a background thread so the EDT stays free
        if (con != null) {
            loadSeatStatusAsync();
        } else {
            // FIX 1: if no DB, disable confirm and warn
            confirmBtn.setEnabled(false);
            confirmBtn.setText("DB Unavailable");
            showStyledMessage("⚠️ Database Error",
                "Could not connect to the database.\nSeat booking is unavailable.", false);
        }
    }

    // ── Background seat loader ─────────────────────────────────────────────────
    /**
     * FIX 5: Loads booked-seat data off the EDT, then updates each seat
     * button on the EDT when the result is ready.
     */
    private void loadSeatStatusAsync() {
        new SwingWorker<boolean[], Void>() {
            @Override
            protected boolean[] doInBackground() {
                boolean[] statuses = new boolean[25];
                for (int i = 0; i < 25; i++) {
                    statuses[i] = isBooked(movie, time, i + 1);
                }
                return statuses;
            }

            @Override
            protected void done() {
                try {
                    boolean[] statuses = get();
                    for (int i = 0; i < 25; i++) {
                        bookedStatus[i] = statuses[i];
                        if (statuses[i]) {
                            // Mark seat as booked visually
                            JButton btn = seatButtons[i];
                            btn.setBackground(SEAT_BOOKED);
                            btn.setForeground(new Color(255, 180, 170));
                            btn.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(160, 40, 30), 1, true),
                                BorderFactory.createEmptyBorder(4, 4, 4, 4)
                            ));
                            btn.setEnabled(false);
                            btn.setCursor(Cursor.getDefaultCursor());
                            btn.setToolTipText("Seat " + btn.getText() + " — Booked");
                            // Remove hover/click listeners from already-booked seats
                            for (MouseListener ml : btn.getMouseListeners()) {
                                btn.removeMouseListener(ml);
                            }
                            for (ActionListener al : btn.getActionListeners()) {
                                btn.removeActionListener(al);
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HEADER
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 15, 20));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 120, 215, 80)),
            BorderFactory.createEmptyBorder(18, 25, 18, 25)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setBackground(new Color(15, 15, 20));

        JLabel icon = new JLabel("🎟");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setBackground(new Color(15, 15, 20));

        JLabel titleLabel = new JLabel("Select Your Seat");
        titleLabel.setForeground(TEXT_WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JLabel breadcrumb = new JLabel(movie + "  ›  " + language + "  ›  " + time);
        breadcrumb.setForeground(ACCENT_BRIGHT);
        breadcrumb.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        titleBlock.add(titleLabel);
        titleBlock.add(Box.createVerticalStrut(3));
        titleBlock.add(breadcrumb);

        left.add(icon);
        left.add(titleBlock);

        JLabel badge = new JLabel("25 Total Seats");
        badge.setForeground(TEXT_GRAY);
        badge.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        badge.setBackground(BG_CARD);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 70), 1, true),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));

        header.add(left,  BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCREEN PANEL
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildScreenPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_DARK);
        wrapper.setBorder(BorderFactory.createEmptyBorder(18, 10, 6, 10));

        // FIX 7: use g.create() so we don't pollute the shared Graphics context
        JPanel screen = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create(); // FIX 7
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                for (int i = 5; i >= 1; i--) {
                    float alpha = 0.06f * i;
                    g2.setColor(new Color(0, 170, 255, (int)(alpha * 255)));
                    g2.setStroke(new BasicStroke(i * 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Arc2D.Double(20, 5, w - 40, h * 2.2, 200, 140, Arc2D.OPEN));
                }

                g2.setColor(SCREEN_COLOR);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Arc2D.Double(20, 5, w - 40, h * 2.2, 200, 140, Arc2D.OPEN));

                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.setColor(new Color(0, 200, 255, 200));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "S C R E E N";
                g2.drawString(txt, (w - fm.stringWidth(txt)) / 2, h - 4);
                g2.dispose(); // FIX 7
            }
        };

        screen.setBackground(BG_DARK);
        screen.setPreferredSize(new Dimension(0, 55));
        wrapper.add(screen, BorderLayout.CENTER);
        return wrapper;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SEATS GRID
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildSeatsPanel() {
        JPanel outerPanel = new JPanel(new BorderLayout(8, 0));
        outerPanel.setBackground(BG_DARK);

        // Row labels
        JPanel rowLabels = new JPanel(new GridLayout(5, 1, 0, 10));
        rowLabels.setBackground(BG_DARK);
        rowLabels.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        char[] rowNames = {'A', 'B', 'C', 'D', 'E'};
        for (char r : rowNames) {
            JLabel lbl = new JLabel(String.valueOf(r), JLabel.CENTER);
            lbl.setForeground(TEXT_GRAY);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lbl.setPreferredSize(new Dimension(20, 48));
            rowLabels.add(lbl);
        }

        // Seats grid
        JPanel seatsGrid = new JPanel(new GridBagLayout());
        seatsGrid.setBackground(BG_DARK);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill   = GridBagConstraints.BOTH;

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int seatNo = row * 5 + col + 1;

                // FIX 5: render all seats as available initially; async loader
                //         will update booked seats once DB results arrive
                JButton seat = buildSeatButton(seatNo, false);
                seatButtons[seatNo - 1] = seat;

                gc.gridx  = (col < 2) ? col : col + 1; // aisle gap after col 2
                gc.gridy  = row;
                gc.weightx = 1;
                gc.weighty = 1;
                gc.ipadx  = 10;
                gc.ipady  = 14;
                seatsGrid.add(seat, gc);
            }

            // Aisle spacer (added once, spans all rows)
            if (row == 0) {
                JLabel aisle = new JLabel("AISLE", JLabel.CENTER);
                aisle.setForeground(new Color(60, 60, 70));
                aisle.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                aisle.setUI(new javax.swing.plaf.basic.BasicLabelUI() {
                    @Override
                    public void paint(Graphics g, JComponent c) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.rotate(Math.PI / 2, c.getWidth() / 2.0, c.getHeight() / 2.0);
                        super.paint(g2, c);
                        g2.dispose();
                    }
                });

                GridBagConstraints ac = new GridBagConstraints();
                ac.gridx = 2; ac.gridy = 0;
                ac.gridheight = 5;
                ac.weightx = 0.3;
                ac.fill = GridBagConstraints.BOTH;
                seatsGrid.add(aisle, ac);
            }
        }

        // Column number labels at bottom
        JPanel colLabels = new JPanel(new GridBagLayout());
        colLabels.setBackground(BG_DARK);
        GridBagConstraints cl = new GridBagConstraints();
        cl.gridy   = 0;
        cl.insets  = new Insets(4, 5, 0, 5);
        cl.fill    = GridBagConstraints.HORIZONTAL;
        cl.weightx = 1;
        for (int col = 0; col < 5; col++) {
            JLabel num = new JLabel(String.valueOf(col + 1), JLabel.CENTER);
            num.setForeground(TEXT_GRAY);
            num.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            cl.gridx = (col < 2) ? col : col + 1;
            colLabels.add(num, cl);
        }

        outerPanel.add(rowLabels, BorderLayout.WEST);
        outerPanel.add(seatsGrid, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_DARK);
        wrapper.add(outerPanel, BorderLayout.CENTER);
        wrapper.add(colLabels,  BorderLayout.SOUTH);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        return wrapper;
    }

    JButton buildSeatButton(int seatNo, boolean booked) {
        char   rowChar = (char) ('A' + (seatNo - 1) / 5);
        int    colNum  = (seatNo - 1) % 5 + 1;
        String label   = rowChar + "" + colNum;

        JButton seat = new JButton(label);
        seat.setFont(new Font("Segoe UI", Font.BOLD, 12));
        seat.setFocusPainted(false);
        seat.setBorderPainted(true);
        seat.setCursor(booked ? Cursor.getDefaultCursor() : new Cursor(Cursor.HAND_CURSOR));
        seat.setToolTipText(booked ? "Seat " + label + " — Booked" : "Seat " + label + " — Available");

        if (booked) {
            applyBookedStyle(seat);
        } else {
            seat.setBackground(SEAT_AVAIL);
            seat.setForeground(new Color(200, 255, 215));
            seat.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 140, 70), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
            ));

            seat.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    if (seat != selectedSeatBtn) seat.setBackground(SEAT_HOVER);
                }
                public void mouseExited(MouseEvent e) {
                    if (seat != selectedSeatBtn) seat.setBackground(SEAT_AVAIL);
                }
            });

            seat.addActionListener(e -> selectSeat(seatNo, seat, label));
        }

        return seat;
    }

    /** Applies the red "booked" visual style to an existing button. */
    private void applyBookedStyle(JButton btn) {
        btn.setBackground(SEAT_BOOKED);
        btn.setForeground(new Color(255, 180, 170));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 40, 30), 1, true),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        btn.setEnabled(false);
    }

    void selectSeat(int seatNo, JButton seat, String label) {
        // Deselect previous
        if (selectedSeatBtn != null && selectedSeatBtn != seat) {
            selectedSeatBtn.setBackground(SEAT_AVAIL);
            selectedSeatBtn.setForeground(new Color(200, 255, 215));
            selectedSeatBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 140, 70), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
            ));
        }

        selectedSeatBtn = seat;
        selectedSeatNo  = seatNo;

        seat.setBackground(SEAT_SELECTED);
        seat.setForeground(new Color(50, 30, 0));
        seat.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 130, 10), 2, true),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        updateSummary(label);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LEGEND
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildLegendPanel() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
        legend.setBackground(new Color(15, 15, 20));
        legend.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 45, 55)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        legend.add(legendItem(SEAT_AVAIL,    new Color(30, 140, 70),  "Available"));
        legend.add(legendItem(SEAT_BOOKED,   new Color(160, 40, 30),  "Booked"));
        legend.add(legendItem(SEAT_SELECTED, new Color(220, 130, 10), "Selected"));
        legend.add(legendItem(SEAT_HOVER,    ACCENT_BLUE,             "Hover"));
        return legend;
    }

    JPanel legendItem(Color fill, Color border, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        item.setBackground(new Color(15, 15, 20));

        // FIX 8: use g.create() inside custom painting
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create(); // FIX 8
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, 18, 18, 5, 5);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, 17, 17, 5, 5);
                g2.dispose(); // FIX 8
            }
        };
        dot.setPreferredSize(new Dimension(18, 18));
        dot.setBackground(new Color(15, 15, 20));

        JLabel lbl = new JLabel(text);
        lbl.setForeground(TEXT_GRAY);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        item.add(dot);
        item.add(lbl);
        return item;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ══════════════════════════════════════════════════════════════════════════
    JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(15, 15, 20));
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0, 120, 215, 60)),
            BorderFactory.createEmptyBorder(25, 18, 25, 18)
        ));

        JLabel heading = new JLabel("Booking Summary");
        heading.setForeground(TEXT_WHITE);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 15));
        heading.setAlignmentX(LEFT_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(45, 45, 55));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        sidebar.add(heading);
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(sep);
        sidebar.add(Box.createVerticalStrut(18));

        summaryMovie = summaryRow(sidebar, "🎬 Movie",    movie);
        summaryLang  = summaryRow(sidebar, "🌐 Language", language);
        summaryTime  = summaryRow(sidebar, "🕐 Show",     time);
        summarySeat  = summaryRow(sidebar, "💺 Seat",     "—");
        summaryPrice = summaryRow(sidebar, "💵 Price",    "—");

        sidebar.add(Box.createVerticalStrut(10));

        JSeparator sep2 = new JSeparator();
        sep2.setForeground(new Color(45, 45, 55));
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sidebar.add(sep2);
        sidebar.add(Box.createVerticalGlue());

        JLabel hint = new JLabel("<html><center>Click a green seat<br>to select it</center></html>");
        hint.setForeground(TEXT_GRAY);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setAlignmentX(CENTER_ALIGNMENT);
        sidebar.add(hint);
        sidebar.add(Box.createVerticalStrut(16));

        // Confirm button
        confirmBtn = new JButton("Confirm Booking");
        confirmBtn.setAlignmentX(CENTER_ALIGNMENT);
        confirmBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        confirmBtn.setBackground(ACCENT_BLUE);
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        confirmBtn.setFocusPainted(false);
        confirmBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_BRIGHT, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        confirmBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        confirmBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (confirmBtn.isEnabled()) confirmBtn.setBackground(ACCENT_BRIGHT);
            }
            public void mouseExited(MouseEvent e) {
                if (confirmBtn.isEnabled()) confirmBtn.setBackground(ACCENT_BLUE);
            }
        });

        confirmBtn.addActionListener(e -> handleConfirm());
        sidebar.add(confirmBtn);

        return sidebar;
    }

    JLabel summaryRow(JPanel parent, String key, String value) {
        JPanel row = new JPanel(new BorderLayout(0, 3));
        row.setBackground(new Color(15, 15, 20));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel keyLbl = new JLabel(key);
        keyLbl.setForeground(TEXT_GRAY);
        keyLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JLabel valLbl = new JLabel(value);
        valLbl.setForeground(TEXT_WHITE);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        row.add(keyLbl, BorderLayout.NORTH);
        row.add(valLbl, BorderLayout.CENTER);

        parent.add(row);
        parent.add(Box.createVerticalStrut(14));
        return valLbl;
    }

    void updateSummary(String seatLabel) {
        summarySeat.setText("Seat " + seatLabel);
        summaryPrice.setText("₹ 250");
        summaryPrice.setForeground(new Color(100, 220, 120));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONFIRM FLOW
    // ══════════════════════════════════════════════════════════════════════════
    void handleConfirm() {
        if (selectedSeatNo == -1) {
            showStyledMessage("⚠️ No Seat Selected",
                "Please click on a green seat before confirming.", false);
            return;
        }

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.setBackground(BG_CARD);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        JLabel namePrompt = new JLabel("Enter passenger name:");
        namePrompt.setForeground(TEXT_WHITE);
        namePrompt.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JTextField nameField = new JTextField(18);
        nameField.setBackground(new Color(28, 28, 35));
        nameField.setForeground(TEXT_WHITE);
        nameField.setCaretColor(TEXT_WHITE);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_BLUE, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        inputPanel.add(namePrompt);
        inputPanel.add(Box.createVerticalStrut(10));
        inputPanel.add(nameField);

        UIManager.put("OptionPane.background",        BG_CARD);
        UIManager.put("Panel.background",             BG_CARD);
        UIManager.put("OptionPane.messageForeground", TEXT_WHITE);

        int result = JOptionPane.showConfirmDialog(
            this, inputPanel, "Customer Details",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showStyledMessage("⚠️ Name Required", "Please enter a valid passenger name.", false);
            return;
        }

        book(name, selectedSeatNo, selectedSeatBtn);
    }

    void book(String name, int seatNo, JButton btn) {
        // FIX 3: ensure connection is still alive; reconnect if needed
        if (!DBConnection.isAlive(con)) {
            con = DBConnection.getConnection();
            if (con == null) {
                showStyledMessage("❌ DB Error",
                    "Lost connection to database. Please restart the app.", false);
                return;
            }
        }

        // FIX 2: try-with-resources for PreparedStatement
        try (PreparedStatement pst = con.prepareStatement(
                "INSERT INTO bookings (customer_name, movie_name, show_time, seat_number, language, booking_date) " +
                "VALUES (?, ?, ?, ?, ?, CURDATE())")) {

            pst.setString(1, name);
            pst.setString(2, movie);
            pst.setString(3, time);
            pst.setInt   (4, seatNo);
            pst.setString(5, language);
            pst.executeUpdate();

            // Update button to booked state
            applyBookedStyle(btn);
            // FIX 4: just disable; no need for fragile removeMouseListener[0]
            btn.setEnabled(false);

            selectedSeatBtn = null;
            selectedSeatNo  = -1;
            summarySeat.setText("—");
            summaryPrice.setText("—");
            summaryPrice.setForeground(TEXT_WHITE);

            showStyledMessage("✅ Booking Confirmed!",
                "Seat booked for " + name + ".\nEnjoy the movie!", true);

        } catch (SQLException ex) {
            showStyledMessage("❌ Booking Failed",
                "This seat may already be taken. Please choose another.", false);
            ex.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DB HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    boolean isBooked(String movie, String time, int seatNo) {
        // FIX 2: try-with-resources for PreparedStatement and ResultSet
        if (!DBConnection.isAlive(con)) return false;
        try (PreparedStatement pst = con.prepareStatement(
                "SELECT COUNT(*) FROM bookings WHERE movie_name=? AND show_time=? AND seat_number=?")) {
            pst.setString(1, movie);
            pst.setString(2, time);
            pst.setInt   (3, seatNo);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STYLED DIALOG
    // ══════════════════════════════════════════════════════════════════════════
    void showStyledMessage(String title, String message, boolean success) {
        JPanel msgPanel = new JPanel(new BorderLayout(0, 10));
        msgPanel.setBackground(BG_CARD);
        msgPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel icon = new JLabel(success ? "✅" : "⚠️", JLabel.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 34));

        JLabel msg = new JLabel(
            "<html><center>" + message.replace("\n", "<br>") + "</center></html>", JLabel.CENTER);
        msg.setForeground(TEXT_WHITE);
        msg.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        msgPanel.add(icon, BorderLayout.NORTH);
        msgPanel.add(msg,  BorderLayout.CENTER);

        UIManager.put("OptionPane.background",        BG_CARD);
        UIManager.put("Panel.background",             BG_CARD);
        UIManager.put("OptionPane.messageForeground", TEXT_WHITE);

        JOptionPane.showMessageDialog(this, msgPanel, title, JOptionPane.PLAIN_MESSAGE);
    }
}