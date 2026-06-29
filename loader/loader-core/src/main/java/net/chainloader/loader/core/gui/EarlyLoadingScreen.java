package net.chainloader.loader.core.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Mockup of the early-startup graphics loading screen (similar to NeoForge's EarlyGUI).
 * This displays loading phases, progress bars, and log messages during classloading,
 * remapping, and mixin patching before Minecraft's native GLFW window is initialized.
 */
public class EarlyLoadingScreen {
    private JFrame window;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    private JTabbedPane tabbedPane;
    private static EarlyLoadingScreen instance;

    public static synchronized EarlyLoadingScreen getInstance() {
        if (instance == null) {
            instance = new EarlyLoadingScreen();
        }
        return instance;
    }

    private EarlyLoadingScreen() {
        // Check for headless environment first
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[EarlyLoadingScreen] Headless environment detected. Falling back to stdout logging.");
            return;
        }

        // Initialize Swing GUI in a thread-safe manner
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            // Style properties for JTabbedPane to match dark theme
            UIManager.put("TabbedPane.background", new Color(30, 30, 35));
            UIManager.put("TabbedPane.foreground", new Color(180, 180, 200));
            UIManager.put("TabbedPane.selected", new Color(138, 43, 226));
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("TabbedPane.contentAreaColor", new Color(30, 30, 35));
            UIManager.put("TabbedPane.shadow", new Color(50, 50, 55));
            UIManager.put("TabbedPane.highlight", new Color(75, 75, 85));
            UIManager.put("TabbedPane.focus", new Color(138, 43, 226));
        } catch (Exception ignored) {}

        window = new JFrame("ChainLoader Bootloader");
        window.setSize(640, 400);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setLocationRelativeTo(null);
        window.setUndecorated(true); // Borderless style like modern NeoForge loading screen

        // Cleanup fields when window is manually closed
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                window = null;
                progressBar = null;
                statusLabel = null;
                logArea = null;
                tabbedPane = null;
            }
        });

        // Core Container with Premium Dark Theme
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBackground(new Color(30, 30, 35));
        panel.setBorder(BorderFactory.createLineBorder(new Color(75, 75, 85), 2));

        // Header Panel (Logo & Title) with BorderLayout to host Close Button on the right
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(20, 20, 25));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        titlePanel.setBackground(new Color(20, 20, 25));
        JLabel titleLabel = new JLabel("CHAINLOADER");
        titleLabel.setFont(new Font("Consolas", Font.BOLD, 24));
        titleLabel.setForeground(new Color(138, 43, 226)); // Purple accent branding
        JLabel versionLabel = new JLabel("v1.0.0-beta - Early GUI Loading");
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        versionLabel.setForeground(Color.GRAY);
        titlePanel.add(titleLabel);
        titlePanel.add(versionLabel);

        // Beautiful Premium Dark-Themed Close Button
        JButton closeButton = new JButton("X");
        closeButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        closeButton.setForeground(new Color(180, 180, 180));
        closeButton.setBackground(new Color(20, 20, 25));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        closeButton.setFocusPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setOpaque(true);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setBackground(new Color(200, 50, 50));
                closeButton.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setBackground(new Color(20, 20, 25));
                closeButton.setForeground(new Color(180, 180, 180));
            }
        });

        closeButton.addActionListener(e -> {
            if (window != null) {
                window.dispose();
            }
        });

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(closeButton, BorderLayout.EAST);

        // Window dragging logic
        MouseAdapter dragAdapter = new MouseAdapter() {
            private Point mouseDownScreenCoords = null;
            private Point windowLocAtDragStart = null;

            @Override
            public void mousePressed(MouseEvent e) {
                mouseDownScreenCoords = e.getLocationOnScreen();
                if (window != null) {
                    windowLocAtDragStart = window.getLocation();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mouseDownScreenCoords = null;
                windowLocAtDragStart = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point currScreenCoords = e.getLocationOnScreen();
                if (mouseDownScreenCoords != null && windowLocAtDragStart != null && window != null) {
                    int deltaX = currScreenCoords.x - mouseDownScreenCoords.x;
                    int deltaY = currScreenCoords.y - mouseDownScreenCoords.y;
                    window.setLocation(windowLocAtDragStart.x + deltaX, windowLocAtDragStart.y + deltaY);
                }
            }
        };
        titlePanel.addMouseListener(dragAdapter);
        titlePanel.addMouseMotionListener(dragAdapter);
        headerPanel.addMouseListener(dragAdapter);
        headerPanel.addMouseMotionListener(dragAdapter);

        // Center Panel (Logs & Stats)
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBackground(new Color(30, 30, 35));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(30, 30, 35));
        tabbedPane.setForeground(new Color(138, 43, 226));
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabbedPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 55)));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(15, 15, 20));
        logArea.setForeground(new Color(180, 220, 180)); // Terminal green logs
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setMargin(new Insets(5, 5, 5, 5));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 55)));
        
        tabbedPane.addTab("General", scrollPane);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        // Footer Panel (Progress & Status)
        JPanel footerPanel = new JPanel(new BorderLayout(5, 5));
        footerPanel.setBackground(new Color(30, 30, 35));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));

        statusLabel = new JLabel("Initializing bootloader classloader...");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(138, 43, 226)); // Purple progress bar
        progressBar.setBackground(new Color(50, 50, 55));
        progressBar.setBorder(BorderFactory.createLineBorder(new Color(75, 75, 85)));

        footerPanel.add(statusLabel, BorderLayout.NORTH);
        footerPanel.add(progressBar, BorderLayout.SOUTH);

        // Assemble Window
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);
        window.setContentPane(panel);
    }

    /**
     * Shows the loading screen window on a background Swing thread.
     */
    public void show() {
        if (window == null) {
            System.out.println("[EarlyLoadingScreen] Booting ChainLoader Engine...");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            window.setVisible(true);
            log("Booting ChainLoader Engine...");
        });
    }

    /**
     * Updates the progress bar and status text dynamically.
     */
    public void updateProgress(int percent, String statusMessage) {
        if (progressBar == null) {
            System.out.println("[EarlyLoadingScreen] Progress: " + percent + "% - " + statusMessage);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JProgressBar bar = progressBar;
            JLabel label = statusLabel;
            if (bar != null) {
                bar.setValue(percent);
            }
            if (label != null) {
                label.setText(statusMessage);
            }
            log("[STATUS] " + statusMessage + " (" + percent + "%)");
        });
    }

    /**
     * Appends a log line to the log display area.
     */
    public void log(String message) {
        log("General", message);
    }

    /**
     * Appends a log line to a specific category log area.
     */
    public void log(final String category, final String message) {
        final String finalCategory = (category == null || category.trim().isEmpty()) ? "General" : category;
        if (window == null) {
            System.out.println("[EarlyLoadingScreen Log][" + finalCategory + "] " + message);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (tabbedPane == null) {
                System.out.println("[EarlyLoadingScreen Log][" + finalCategory + "] " + message);
                return;
            }
            int tabCount = tabbedPane.getTabCount();
            int tabIndex = -1;
            for (int i = 0; i < tabCount; i++) {
                if (tabbedPane.getTitleAt(i).equalsIgnoreCase(finalCategory)) {
                    tabIndex = i;
                    break;
                }
            }

            JTextArea targetArea;
            if (tabIndex == -1) {
                targetArea = new JTextArea();
                targetArea.setEditable(false);
                targetArea.setBackground(new Color(15, 15, 20));
                targetArea.setForeground(new Color(180, 220, 180)); // Terminal green logs
                targetArea.setFont(new Font("Consolas", Font.PLAIN, 11));
                targetArea.setMargin(new Insets(5, 5, 5, 5));
                JScrollPane scrollPane = new JScrollPane(targetArea);
                scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 55)));

                tabbedPane.addTab(finalCategory, scrollPane);
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

    /**
     * Closes the loading screen when the game client initializes.
     */
    public void close() {
        if (window == null) {
            System.out.println("[EarlyLoadingScreen] Disposing bootloader GUI context.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            System.out.println("[EarlyLoadingScreen] close() called. Keeping window open as requested.");
            log("General", "Initialization completed. Handing over control to Minecraft engine.");
            log("General", "You can close this window manually when ready using the 'X' button in the top-right.");
        });
    }

    /**
     * Prints initialization errors with full stack trace to the mod's tab and logs/early-loader-errors.log.
     */
    public synchronized void logError(String modId, String message, Throwable t) {
        String displayMsg = (message != null ? message : "");
        if (t != null) {
            if (!displayMsg.isEmpty()) {
                displayMsg += "\n";
            }
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            displayMsg += sw.toString();
        }
        
        // Log to the mod's specific tab on the GUI
        log(modId, "[ERROR] " + displayMsg);

        // Write to logs/early-loader-errors.log
        try {
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            java.io.File logFile = new java.io.File(logDir, "early-loader-errors.log");
            try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
                 java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                pw.println("=========================================");
                pw.println("Timestamp: " + java.time.LocalDateTime.now());
                pw.println("Mod ID: " + modId);
                pw.println("Message: " + message);
                if (t != null) {
                    t.printStackTrace(pw);
                }
                pw.println("=========================================");
                pw.println();
            }
        } catch (Exception e) {
            System.err.println("[EarlyLoadingScreen] Failed to write to early-loader-errors.log: " + e.getMessage());
        }
    }
}
