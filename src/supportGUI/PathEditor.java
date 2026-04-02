package supportGUI;

import characteristics.Parameters;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Editeur visuel de chemins pour les NoeMainBots.
 * Clic gauche = ajouter un waypoint, clic droit = supprimer le dernier.
 * Les chemins sont sauvegardés dans paths/main_paths.txt
 */
public class PathEditor extends JFrame {

  private static final int ARENA_W = 3000;
  private static final int ARENA_H = 2000;
  private static final double SCALE = 0.35;
  private static final int PANEL_W = (int)(ARENA_W * SCALE);
  private static final int PANEL_H = (int)(ARENA_H * SCALE);

  private static final String PATH_FILE = "paths/main_paths.txt";

  private static final Color[] BOT_COLORS = {
      new Color(0x2196F3), // M1 bleu
      new Color(0xF44336), // M2 rouge
      new Color(0x4CAF50), // M3 vert
  };
  private static final String[] BOT_NAMES = {"M1", "M2", "M3"};

  // Waypoints par bot : index 0=M1, 1=M2, 2=M3
  private final List<List<Point2D.Double>> paths = new ArrayList<>();
  private int selectedBot = 0; // bot actuellement edite
  private boolean priority = false; // chemin prioritaire (pas d'interruption combat)

  private final ArenaPanel arenaPanel;
  private final JLabel statusLabel;
  private JCheckBox priorityCb;

  public PathEditor() {
    super("Path Editor - NoeMainBot");
    setDefaultCloseOperation(EXIT_ON_CLOSE);

    for (int i = 0; i < 3; i++) paths.add(new ArrayList<>());

    arenaPanel = new ArenaPanel();
    statusLabel = new JLabel("  Bot: M1 | Clic gauche: ajouter | Clic droit: supprimer");

    JPanel toolbar = createToolbar();

    setLayout(new BorderLayout());
    add(toolbar, BorderLayout.NORTH);
    add(arenaPanel, BorderLayout.CENTER);
    add(statusLabel, BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(null);
    setResizable(false);

    loadPaths();
  }

  private JPanel createToolbar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

    // Selection du bot
    ButtonGroup group = new ButtonGroup();
    for (int i = 0; i < 3; i++) {
      JToggleButton btn = new JToggleButton(BOT_NAMES[i]);
      btn.setForeground(BOT_COLORS[i].darker());
      btn.setFont(btn.getFont().deriveFont(Font.BOLD));
      final int idx = i;
      btn.addActionListener(e -> {
        selectedBot = idx;
        statusLabel.setText("  Bot: " + BOT_NAMES[idx]
            + " | Waypoints: " + paths.get(idx).size());
        arenaPanel.repaint();
      });
      group.add(btn);
      bar.add(btn);
      if (i == 0) btn.setSelected(true);
    }

    bar.add(Box.createHorizontalStrut(16));

    JButton clearBtn = new JButton("Effacer");
    clearBtn.addActionListener(e -> {
      paths.get(selectedBot).clear();
      updateStatus();
      arenaPanel.repaint();
    });
    bar.add(clearBtn);

    JButton clearAllBtn = new JButton("Tout effacer");
    clearAllBtn.addActionListener(e -> {
      for (List<Point2D.Double> p : paths) p.clear();
      updateStatus();
      arenaPanel.repaint();
    });
    bar.add(clearAllBtn);

    bar.add(Box.createHorizontalStrut(16));

    JButton saveBtn = new JButton("Sauvegarder");
    saveBtn.addActionListener(e -> savePaths());
    bar.add(saveBtn);

    JButton loadBtn = new JButton("Charger");
    loadBtn.addActionListener(e -> { loadPaths(); arenaPanel.repaint(); });
    bar.add(loadBtn);

    bar.add(Box.createHorizontalStrut(16));

    JCheckBox mirrorCb = new JCheckBox("Miroir A/B");
    mirrorCb.setToolTipText("Applique symetrie horizontale (x → 3000-x) pour Team A");
    mirrorCb.addActionListener(e -> {
      mirrorPaths();
      arenaPanel.repaint();
    });
    bar.add(mirrorCb);

    bar.add(Box.createHorizontalStrut(16));

    priorityCb = new JCheckBox("Prioritaire");
    priorityCb.setSelected(priority);
    priorityCb.setToolTipText("Le bot suit le chemin en entier avant toute autre action (ignore les ennemis)");
    priorityCb.addActionListener(e -> priority = priorityCb.isSelected());
    bar.add(priorityCb);

    return bar;
  }

  private void updateStatus() {
    statusLabel.setText("  Bot: " + BOT_NAMES[selectedBot]
        + " | Waypoints: " + paths.get(selectedBot).size());
  }

  // ---- Arena drawing panel ---- //

  private class ArenaPanel extends JPanel {

    ArenaPanel() {
      setPreferredSize(new Dimension(PANEL_W, PANEL_H));
      setBackground(new Color(0x1A1A2E));

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          double ax = e.getX() / SCALE;
          double ay = e.getY() / SCALE;
          // Clamp to arena
          ax = Math.max(0, Math.min(ARENA_W, ax));
          ay = Math.max(0, Math.min(ARENA_H, ay));

          if (SwingUtilities.isLeftMouseButton(e)) {
            paths.get(selectedBot).add(new Point2D.Double(ax, ay));
          } else if (SwingUtilities.isRightMouseButton(e)) {
            List<Point2D.Double> pts = paths.get(selectedBot);
            if (!pts.isEmpty()) pts.remove(pts.size() - 1);
          }
          updateStatus();
          repaint();
        }
      });

      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          int ax = (int)(e.getX() / SCALE);
          int ay = (int)(e.getY() / SCALE);
          statusLabel.setText("  Bot: " + BOT_NAMES[selectedBot]
              + " | Waypoints: " + paths.get(selectedBot).size()
              + " | Position: (" + ax + ", " + ay + ")");
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g0) {
      super.paintComponent(g0);
      Graphics2D g = (Graphics2D) g0;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g.scale(SCALE, SCALE);

      drawGrid(g);
      drawBotStartPositions(g);
      drawAllPaths(g);
    }

    private void drawGrid(Graphics2D g) {
      g.setColor(new Color(255, 255, 255, 25));
      g.setStroke(new BasicStroke(1));
      for (int x = 0; x <= ARENA_W; x += 200) g.drawLine(x, 0, x, ARENA_H);
      for (int y = 0; y <= ARENA_H; y += 200) g.drawLine(0, y, ARENA_W, y);

      // Bordure de l'arene
      g.setColor(new Color(255, 255, 255, 100));
      g.setStroke(new BasicStroke(4));
      g.drawRect(0, 0, ARENA_W, ARENA_H);

      // Ligne mediane
      g.setColor(new Color(255, 255, 255, 40));
      g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
          0, new float[]{20, 20}, 0));
      g.drawLine(ARENA_W / 2, 0, ARENA_W / 2, ARENA_H);

      // Labels axes
      g.setColor(new Color(255, 255, 255, 80));
      g.setFont(new Font("SansSerif", Font.PLAIN, 28));
      for (int x = 0; x <= ARENA_W; x += 500) {
        g.drawString(String.valueOf(x), x + 5, 30);
      }
      for (int y = 200; y <= ARENA_H; y += 500) {
        g.drawString(String.valueOf(y), 5, y + 25);
      }
    }

    private void drawBotStartPositions(Graphics2D g) {
      double radius = Parameters.teamBMainBotRadius;

      // Team A (gauche) - gris
      drawBotCircle(g, Parameters.teamAMainBot1InitX, Parameters.teamAMainBot1InitY,
          radius, new Color(150, 150, 150, 80), "A1");
      drawBotCircle(g, Parameters.teamAMainBot2InitX, Parameters.teamAMainBot2InitY,
          radius, new Color(150, 150, 150, 80), "A2");
      drawBotCircle(g, Parameters.teamAMainBot3InitX, Parameters.teamAMainBot3InitY,
          radius, new Color(150, 150, 150, 80), "A3");

      // Team B (droite) - colore
      drawBotCircle(g, Parameters.teamBMainBot1InitX, Parameters.teamBMainBot1InitY,
          radius, BOT_COLORS[0], BOT_NAMES[0]);
      drawBotCircle(g, Parameters.teamBMainBot2InitX, Parameters.teamBMainBot2InitY,
          radius, BOT_COLORS[1], BOT_NAMES[1]);
      drawBotCircle(g, Parameters.teamBMainBot3InitX, Parameters.teamBMainBot3InitY,
          radius, BOT_COLORS[2], BOT_NAMES[2]);

      // Secondary bots - petit cercle
      double sRadius = Parameters.teamBSecondaryBotRadius * 0.7;
      drawBotCircle(g, Parameters.teamBSecondaryBot1InitX, Parameters.teamBSecondaryBot1InitY,
          sRadius, new Color(255, 165, 0, 100), "S1");
      drawBotCircle(g, Parameters.teamBSecondaryBot2InitX, Parameters.teamBSecondaryBot2InitY,
          sRadius, new Color(255, 165, 0, 100), "S2");
    }

    private void drawBotCircle(Graphics2D g, double x, double y,
                                double radius, Color color, String label) {
      g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
      g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
      g.setColor(color);
      g.setStroke(new BasicStroke(2));
      g.draw(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));

      g.setFont(new Font("SansSerif", Font.BOLD, 24));
      FontMetrics fm = g.getFontMetrics();
      g.drawString(label,
          (int)(x - fm.stringWidth(label) / 2.0),
          (int)(y + fm.getAscent() / 2.0 - 2));
    }

    private void drawAllPaths(Graphics2D g) {
      for (int i = 0; i < 3; i++) {
        List<Point2D.Double> pts = paths.get(i);
        if (pts.isEmpty()) continue;

        boolean active = (i == selectedBot);
        Color c = BOT_COLORS[i];
        int alpha = active ? 220 : 80;

        // Ligne de depart (position init → premier waypoint)
        double startX = getStartX(i);
        double startY = getStartY(i);

        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha / 2));
        g.setStroke(new BasicStroke(active ? 3 : 1, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 0, new float[]{10, 10}, 0));
        g.draw(new Line2D.Double(startX, startY, pts.get(0).x, pts.get(0).y));

        // Segments du chemin
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
        g.setStroke(new BasicStroke(active ? 3 : 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int j = 0; j < pts.size() - 1; j++) {
          g.draw(new Line2D.Double(pts.get(j).x, pts.get(j).y,
              pts.get(j + 1).x, pts.get(j + 1).y));
        }

        // Fleches de direction
        for (int j = 0; j < pts.size() - 1; j++) {
          drawArrow(g, pts.get(j), pts.get(j + 1), c, alpha);
        }

        // Waypoints (cercles)
        for (int j = 0; j < pts.size(); j++) {
          Point2D.Double p = pts.get(j);
          int r = active ? 12 : 8;

          g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
          g.fill(new Ellipse2D.Double(p.x - r, p.y - r, r * 2, r * 2));
          g.setColor(Color.WHITE);
          g.setStroke(new BasicStroke(2));
          g.draw(new Ellipse2D.Double(p.x - r, p.y - r, r * 2, r * 2));

          // Numero du waypoint
          if (active) {
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            String num = String.valueOf(j + 1);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(num,
                (int)(p.x - fm.stringWidth(num) / 2.0),
                (int)(p.y + fm.getAscent() / 2.0 - 2));
          }
        }
      }
    }

    private void drawArrow(Graphics2D g, Point2D.Double from, Point2D.Double to,
                           Color c, int alpha) {
      double midX = (from.x + to.x) / 2;
      double midY = (from.y + to.y) / 2;
      double angle = Math.atan2(to.y - from.y, to.x - from.x);
      int size = 14;

      Path2D arrow = new Path2D.Double();
      arrow.moveTo(midX + size * Math.cos(angle),
          midY + size * Math.sin(angle));
      arrow.lineTo(midX + size * Math.cos(angle + 2.5),
          midY + size * Math.sin(angle + 2.5));
      arrow.lineTo(midX + size * Math.cos(angle - 2.5),
          midY + size * Math.sin(angle - 2.5));
      arrow.closePath();

      g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
      g.fill(arrow);
    }
  }

  // ---- Positions initiales Team B ---- //

  private double getStartX(int botIndex) {
    return switch (botIndex) {
      case 0 -> Parameters.teamBMainBot1InitX;
      case 1 -> Parameters.teamBMainBot2InitX;
      case 2 -> Parameters.teamBMainBot3InitX;
      default -> 0;
    };
  }

  private double getStartY(int botIndex) {
    return switch (botIndex) {
      case 0 -> Parameters.teamBMainBot1InitY;
      case 1 -> Parameters.teamBMainBot2InitY;
      case 2 -> Parameters.teamBMainBot3InitY;
      default -> 0;
    };
  }

  // ---- Miroir horizontal (Team B → Team A) ---- //

  private void mirrorPaths() {
    for (List<Point2D.Double> pts : paths) {
      for (Point2D.Double p : pts) {
        p.x = ARENA_W - p.x;
      }
    }
  }

  // ---- Sauvegarde / Chargement ---- //

  private void savePaths() {
    try (PrintWriter pw = new PrintWriter(new FileWriter(PATH_FILE))) {
      pw.println("PRIORITY:" + priority);
      for (int i = 0; i < 3; i++) {
        StringBuilder sb = new StringBuilder(BOT_NAMES[i]).append(":");
        List<Point2D.Double> pts = paths.get(i);
        for (int j = 0; j < pts.size(); j++) {
          if (j > 0) sb.append(";");
          sb.append(Math.round(pts.get(j).x)).append(",").append(Math.round(pts.get(j).y));
        }
        pw.println(sb);
      }
      statusLabel.setText("  Sauvegarde OK → " + PATH_FILE
          + (priority ? " [PRIORITAIRE]" : ""));
    } catch (IOException ex) {
      statusLabel.setText("  Erreur sauvegarde: " + ex.getMessage());
    }
  }

  private void loadPaths() {
    File f = new File(PATH_FILE);
    if (!f.exists()) return;
    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
      for (List<Point2D.Double> p : paths) p.clear();
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        // Lire le flag PRIORITY
        if (line.startsWith("PRIORITY:")) {
          priority = Boolean.parseBoolean(line.substring("PRIORITY:".length()));
          if (priorityCb != null) priorityCb.setSelected(priority);
          continue;
        }

        int colon = line.indexOf(':');
        if (colon < 0) continue;
        String name = line.substring(0, colon);
        String data = line.substring(colon + 1).trim();

        int idx = -1;
        for (int i = 0; i < BOT_NAMES.length; i++) {
          if (BOT_NAMES[i].equals(name)) { idx = i; break; }
        }
        if (idx < 0 || data.isEmpty()) continue;

        for (String pt : data.split(";")) {
          String[] xy = pt.split(",");
          if (xy.length == 2) {
            paths.get(idx).add(new Point2D.Double(
                Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
          }
        }
      }
      updateStatus();
    } catch (IOException ex) {
      statusLabel.setText("  Erreur chargement: " + ex.getMessage());
    }
  }

  // ---- Main ---- //

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new PathEditor().setVisible(true));
  }
}
