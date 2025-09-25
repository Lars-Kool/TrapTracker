package org.micromanager.plugins.traptracker.graph;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.plugins.traptracker.Settings;

public class Graph extends JPanel implements PropertyChangeListener {

   private static final String TITLE = "Number of traps filled";
   private static final String START = "Start";
   private static final String STOP = "Stop";
   private static final float MINMAX = 100;
   private static final int COUNT = 100;
   private static final int DELAY = 1000;
   private static final Random random = new Random();
   private final Timer timer;

   private final Settings settings_;
   private final float[] data = new float[COUNT];
   private final float[] triggerLimit = new float[COUNT];

   private final XYSeries trapsSeries;
   private final XYSeries triggerSeries;
   private final XYSeriesCollection dataSets;
   private JFreeChart chart;
   private ChartPanel chartPanel;

   public Graph(Settings settings) {
      settings_ = settings;
      settings_.addPropertyChangeListener(this);
      Arrays.fill(triggerLimit, (float) settings_.triggerValue);

      trapsSeries = new XYSeries("Filled traps");
      triggerSeries = new XYSeries("Trigger value");
      for (int i = 0; i < COUNT; i++) {
         trapsSeries.add(i - COUNT + 1, 0);
         triggerSeries.add(i - COUNT + 1, 0);
      }
      dataSets = new XYSeriesCollection();
      dataSets.addSeries(trapsSeries);
      dataSets.addSeries(triggerSeries);
      chart = createChart(dataSets);

      chartPanel = new ChartPanel(chart) {
         @Override
         public Dimension getPreferredSize() {
            return new Dimension(640, 480);
         }
      };
      this.add(chartPanel, BorderLayout.CENTER);

      timer = new Timer(DELAY, new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateData(settings_.trapsFilled.get(),
                  settings.triggerValue);
         }
      });
   }

   private void updateData(double trapsFilled, double triggerValue) {
      double x = (double) trapsSeries.getX(COUNT - 2) + 1;
      trapsSeries.remove(0);
      trapsSeries.add(x, trapsFilled);
      triggerSeries.remove(0);
      triggerSeries.add(x, triggerValue);
   }

   private JFreeChart createChart(final XYDataset dataset) {
      final JFreeChart result = ChartFactory.createXYLineChart(
            TITLE,
            "Time (seconds)",
            "Number of traps filled",
            dataset
      );
      final XYPlot plot = result.getXYPlot();
      ValueAxis domain = plot.getDomainAxis();
      domain.setAutoRange(true);
      ValueAxis range = plot.getRangeAxis();
      range.setRange(-5, settings_.nTrapsValue + 5);
      return result;
   }

   public void start() {
      if (!timer.isRunning()) {
         timer.start();
      }
   }

   public void stop() {
      if (timer.isRunning()) {
         timer.stop();
      }
   }

   @Override
   public void propertyChange(PropertyChangeEvent evt) {
      switch (evt.getPropertyName()) {
         case "isRunning":
            if (settings_.isRunning == 0.0) {
               stop();
            }
            break;
         default:
            redraw();
      }
   }

   private void redraw() {
      this.removeAll();
      chart = createChart(dataSets);
      chartPanel = new ChartPanel(chart) {
         @Override
         public Dimension getPreferredSize() {
            return new Dimension(640, 480);
         }
      };

      this.add(chartPanel, BorderLayout.CENTER);
      this.validate();
      this.repaint();
   }
}