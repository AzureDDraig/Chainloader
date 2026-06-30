package net.chainloader.loader.core.gui;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Separate-process Swing bootloader GUI.
 * Reads commands from standard input to update progress, log messages, and handle errors.
 * This completely isolates AWT/Swing graphics contexts from Minecraft's GLFW context.
 */
public class EarlyLoadingScreenProcess {
    private static JFrame window;
    private static JProgressBar progressBar;
    private static JLabel statusLabel;
    private static JTabbedPane tabbedPane;
    private static boolean keepOpen = true;

    public static void main(String[] args) {
        // Parse arguments
        for (String arg : args) {
            if ("--keep-open".equals(arg)) {
                keepOpen = true;
            }
        }

        // Initialize Swing GUI
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("TabbedPane.background", new Color(30, 30, 35));
            UIManager.put("TabbedPane.foreground", new Color(180, 180, 200));
            UIManager.put("TabbedPane.selected", new Color(138, 43, 226));
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("TabbedPane.contentAreaColor", new Color(30, 30, 35));
            UIManager.put("TabbedPane.shadow", new Color(50, 50, 55));
            UIManager.put("TabbedPane.highlight", new Color(75, 75, 85));
            UIManager.put("TabbedPane.focus", new Color(138, 43, 226));
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(EarlyLoadingScreenProcess::createAndShowGUI);

        // Read commands from standard input
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("progress:")) {
                    String[] parts = line.split(":", 3);
                    if (parts.length >= 3) {
                        int percent = Integer.parseInt(parts[1]);
                        String msg = parts[2];
                        updateProgress(percent, msg);
                    }
                } else if (line.startsWith("log:")) {
                    String[] parts = line.split(":", 3);
                    if (parts.length >= 3) {
                        String category = parts[1];
                        String msg = parts[2];
                        log(category, msg);
                    }
                } else if (line.startsWith("close")) {
                    if (keepOpen) {
                        log("General", "Initialization completed. Handing over control to Minecraft engine.");
                        updateProgress(100, "Minecraft launched successfully.");
                    } else {
                        System.exit(0);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void createAndShowGUI() {
        window = new JFrame("ChainLoader Bootloader");
        window.setSize(640, 400);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLocationRelativeTo(null);
        window.setUndecorated(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 20, 25));
        panel.setBorder(BorderFactory.createLineBorder(new Color(138, 43, 226), 2));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        headerPanel.setBackground(new Color(30, 30, 35));
        JLabel titleLabel = new JLabel("ChainLoader Bootloader");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);

        // Close button if we are in keep-open mode
        if (keepOpen) {
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
            rightPanel.setBackground(new Color(30, 30, 35));
            JButton closeBtn = new JButton("X");
            closeBtn.setFocusPainted(false);
            closeBtn.setBorderPainted(false);
            closeBtn.setBackground(new Color(60, 20, 20));
            closeBtn.setForeground(Color.WHITE);
            closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            closeBtn.addActionListener(e -> System.exit(0));
            rightPanel.add(closeBtn);
            headerPanel.setLayout(new BorderLayout());
            headerPanel.add(titleLabel, BorderLayout.WEST);
            headerPanel.add(rightPanel, BorderLayout.EAST);
        }

        // Center tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(30, 30, 35));
        tabbedPane.setForeground(new Color(180, 180, 200));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(20, 20, 25));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        // Footer progress bar
        JPanel footerPanel = new JPanel(new BorderLayout(5, 5));
        footerPanel.setBackground(new Color(20, 20, 25));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        statusLabel = new JLabel("Booting ChainLoader Engine...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(200, 200, 220));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setBackground(new Color(40, 40, 45));
        progressBar.setForeground(new Color(138, 43, 226));
        progressBar.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));

        footerPanel.add(statusLabel, BorderLayout.NORTH);
        footerPanel.add(progressBar, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);

        window.setContentPane(panel);
        window.setVisible(true);
    }

    private static void updateProgress(int percent, String statusMessage) {
        SwingUtilities.invokeLater(() -> {
            if (progressBar != null) {
                progressBar.setValue(percent);
            }
            if (statusLabel != null) {
                statusLabel.setText(statusMessage);
            }
        });
    }

    private static void log(String category, String message) {
        SwingUtilities.invokeLater(() -> {
            if (tabbedPane == null) return;
            int tabCount = tabbedPane.getTabCount();
            int tabIndex = -1;
            for (int i = 0; i < tabCount; i++) {
                if (tabbedPane.getTitleAt(i).equalsIgnoreCase(category)) {
                    tabIndex = i;
                    break;
                }
            }

            JTextArea targetArea;
            if (tabIndex == -1) {
                targetArea = new JTextArea();
                targetArea.setEditable(false);
                targetArea.setBackground(new Color(15, 15, 20));
                targetArea.setForeground(new Color(180, 220, 180));
                targetArea.setFont(new Font("Consolas", Font.PLAIN, 11));
                targetArea.setMargin(new Insets(5, 5, 5, 5));
                JScrollPane scrollPane = new JScrollPane(targetArea);
                scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 55)));

                tabbedPane.addTab(category, scrollPane);
            } else {
                JScrollPane scrollPane = (JScrollPane) tabbedPane.getComponentAt(tabIndex);
                targetArea = (JTextArea) scrollPane.getViewport().getView();
            }

            if (targetArea != null) {
                targetArea.append(message + "\n");
                targetArea.setCaretPosition(targetArea.getDocument().getLength());
            }
        });
    }
}
