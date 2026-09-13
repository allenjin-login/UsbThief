package com.superredrock.usbthief.gui.components;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Vector USB-plug icon (Design System v2).
 *
 * Stroke-based line art that scales cleanly and renders identically on every
 * platform - unlike color emoji, whose availability and look vary by OS font.
 */
public class UsbIcon implements Icon {

    private final int size;
    private final Color color;

    public UsbIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            double u = size / 48.0;
            g2.translate(x, y);
            g2.scale(u, u);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // plug body
            g2.draw(new RoundRectangle2D.Double(15, 12, 18, 14, 5, 5));
            // two contact pins
            g2.draw(new Line2D.Double(20, 12, 20, 7));
            g2.draw(new Line2D.Double(28, 12, 28, 7));
            // cable descending with a gentle curve
            Path2D cable = new Path2D.Double();
            cable.moveTo(24, 26);
            cable.curveTo(24, 32, 24, 34, 24, 38);
            g2.draw(cable);
            // terminal nub
            g2.fill(new Ellipse2D.Double(21.6, 38, 4.8, 4.8));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
