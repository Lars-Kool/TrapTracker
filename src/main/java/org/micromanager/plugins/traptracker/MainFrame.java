package org.micromanager.plugins.traptracker;

import java.awt.Toolkit;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.plugins.traptracker.functions.DeviceControl;
import org.micromanager.plugins.traptracker.graph.Graph;

public class MainFrame extends JFrame {
   private final Studio studio_;
   JButton toggleTrap;
   JButton toggleFlow;
   JButton toggleAutomation;
   private final SettingsPanel settingsPanel;
   private final DeviceControl deviceControl;
   private final Settings settings_;
   private final Graph graph;

   public MainFrame(Studio studio) {
      super("Particle Trapping GUI");
      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));
      super.setResizable(false);
      super.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

      studio_ = studio;
      settings_ = new Settings(studio_);
      deviceControl = new DeviceControl(studio_, settings_);

      graph = new Graph(settings_);
      this.add(graph, "span 5");

      settingsPanel = new SettingsPanel(studio_, settings_);
      this.add(settingsPanel, "wrap");

      configureButtons();

      this.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      this.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      this.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs.
      studio_.events().registerForEvents(this);
   }

   private void configureButtons() {
      toggleTrap = new JButton("Close Trap");
      toggleTrap.addActionListener(e -> {
         if (toggleTrap.getText().equals("Close Trap")) {
            try {
               deviceControl.closeTrap();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
            toggleTrap.setText("Open Trap");
         } else {
            try {
               deviceControl.openTrap();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
            toggleTrap.setText("Close Trap");
         }
      });
      this.add(toggleTrap);

      toggleFlow = new JButton("Start Flow");
      toggleFlow.addActionListener(e -> {
         if (toggleFlow.getText().equals("Start Flow")) {
            try {
               deviceControl.startFlow();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
            toggleFlow.setText("Stop Flow");
         } else {
            try {
               deviceControl.stopFlow();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
            toggleFlow.setText("Start Flow");
         }
      });
      this.add(toggleFlow);

      toggleAutomation = new JButton("Start Automation");
      toggleAutomation.addActionListener(e -> {
         if (toggleAutomation.getText().equals("Start Automation")) {
            try {
               deviceControl.startAutomation();
               graph.start();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
            toggleAutomation.setText("Stop Automation");
         } else {
            try {
               deviceControl.stopAutomation();
               graph.stop();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }
            toggleAutomation.setText("Start Automation");
         }
      });
      this.add(toggleAutomation);
   }
}
