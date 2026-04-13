import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class Login extends JFrame implements ActionListener {

    JTextField user;
    JPasswordField pass;
    JButton login;

    public Login() {

        setTitle("Login");
        setSize(400,300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Main Panel
        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new Color(245,245,245));

        // Title
        JLabel title = new JLabel("Movie Booking Login");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setBounds(90,20,250,30);
        panel.add(title);

        // Username
        JLabel u = new JLabel("Username:");
        u.setBounds(60,80,100,25);
        panel.add(u);

        user = new JTextField();
        user.setBounds(160,80,160,30);
        panel.add(user);

        // Password
        JLabel p = new JLabel("Password:");
        p.setBounds(60,130,100,25);
        panel.add(p);

        pass = new JPasswordField();
        pass.setBounds(160,130,160,30);
        panel.add(pass);

        // Button
        login = new JButton("Login");
        login.setBounds(130,190,120,35);
        login.setBackground(new Color(0,120,215));
        login.setForeground(Color.WHITE);
        login.setFocusPainted(false);
        panel.add(login);

        // Hover effect (simple)
        login.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                login.setBackground(new Color(0,150,255));
            }
            public void mouseExited(MouseEvent e) {
                login.setBackground(new Color(0,120,215));
            }
        });

        add(panel);

        login.addActionListener(this);

        setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        try {
            Connection con = DBConnection.getConnection();

            PreparedStatement pst = con.prepareStatement(
                "SELECT * FROM users WHERE username=? AND password=?"
            );

            pst.setString(1, user.getText());
            pst.setString(2, new String(pass.getPassword()));

            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                new Dashboard();
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,"Invalid Login");
            }

        } catch(Exception ex) {
            JOptionPane.showMessageDialog(this,"Database Error");
        }
    }

    public static void main(String[] args) {
        new Login();
    }
}