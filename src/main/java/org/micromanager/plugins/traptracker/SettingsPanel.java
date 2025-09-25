package org.micromanager.plugins.traptracker;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.Arrays;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.NumberFormatter;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;


public class SettingsPanel extends JPanel {
   private final Studio studio_;
   private final Settings settings_;

   private final NumberFormatter formatter;

   private String[] fluidDevices;
   private DeviceType[] deviceTypes;
   private String[] groups;
   private String[] presets;

   private final JComboBox<String> flowSelector;
   private final JComboBox<String> valveSelector;
   private final JComboBox<String> groupSelector;
   private JComboBox<String> trapPresetSelector;
   private JComboBox<String> releasePresetSelector;

   private final JLabel groupLabel = new JLabel("Preset Group: ");
   private final JLabel trapPresetLabel = new JLabel("Trap Preset: ");
   private final JLabel releasePresetLabel = new JLabel("Release Preset: ");
   private final JLabel flowLabel = new JLabel("Flow device: ");
   private final JLabel valveLabel = new JLabel("Valve device: ");
   private final JLabel flowActiveValueLabel = new JLabel("Pressure during flow: ");
   private final JLabel flowInactiveValueLabel = new JLabel("Pressure while stationary: ");
   private final JLabel valveOpenValueLabel = new JLabel("Pressure valve open: ");
   private final JLabel valveClosedValueLabel = new JLabel("Pressure valve closed: ");
   private final JLabel nTrapsLabel = new JLabel("Number of traps: ");
   private final JLabel triggerLabel = new JLabel("Trigger value: ");
   private final JLabel pixelSizeLabel = new JLabel("Pixel size: ");
   private final JLabel minSizeLabel = new JLabel("Minimum particle size: ");

   private final JFormattedTextField flowActiveTextField;
   private final JFormattedTextField flowInactiveTextField;
   private final JFormattedTextField valveDisplacementTextField;
   private final JFormattedTextField valveDisplacementRateTextField;
   private final JFormattedTextField nTrapsTextField;
   private final JFormattedTextField triggerValueTextField;
   private final JFormattedTextField pixelSizeTextField;
   private final JFormattedTextField minSizeTextField;

   public SettingsPanel(Studio studio, Settings settings) {
      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      studio_ = studio;
      settings_ = settings;

      NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMaximumFractionDigits(2);
      numberFormat.setMinimumFractionDigits(0);
      formatter = new NumberFormatter(numberFormat);
      formatter.setValueClass(Float.class);

      try {
         groups = studio_.core().getAvailableConfigGroups().toArray();
      } catch (Exception e) {
         studio_.core().logMessage("An error occurred while getting the available Groups");
         studio_.core().logMessage(Arrays.toString(e.getStackTrace()));
      }
      groupSelector = new JComboBox<>(groups);
      this.addSelector(groupSelector, this::onGroupSelector);

      trapPresetSelector = new JComboBox<>();
      this.addSelector(trapPresetSelector, this::onTrapSelector);

      releasePresetSelector = new JComboBox<>();
      this.addSelector(releasePresetSelector, this::onReleaseSelector);


      getFluidDevices();
      if (fluidDevices.length == 0) {
         studio_.getLogManager().logMessage("No pressure controllers/syringe pumps found!");
      }
      flowSelector = new JComboBox<>(fluidDevices);
      flowSelector.addActionListener(e -> {
         onFlowSelector();
      });
      valveSelector = new JComboBox<>(fluidDevices);
      valveSelector.addActionListener(e -> {
         onValveSelector();
      });


      flowActiveTextField = textFieldFactory(Settings.ParameterTypes.flowActive);
      flowInactiveTextField = textFieldFactory(Settings.ParameterTypes.flowInactive);
      valveDisplacementTextField = textFieldFactory(Settings.ParameterTypes.valveDisplacement);
      valveDisplacementRateTextField =
            textFieldFactory(Settings.ParameterTypes.valveDisplacementRate);
      nTrapsTextField = textFieldFactory(Settings.ParameterTypes.nTraps);
      triggerValueTextField = textFieldFactory(Settings.ParameterTypes.trigger);
      pixelSizeTextField = textFieldFactory(Settings.ParameterTypes.pixelSize);
      minSizeTextField = textFieldFactory(Settings.ParameterTypes.minSize);

      redraw();
   }

   private void addSelector(JComboBox<String> selector, Method method) {
      selector.addActionListener(e -> {
         method.execute();
      });
   }

   private void onGroupSelector() {
      String temp = settings_.currGroup;
      try {
         settings_.currGroup = (String) groupSelector.getSelectedItem();
         studio_.core().setChannelGroup(settings_.currGroup);
      } catch (Exception e) {
         settings_.currGroup = temp;
         studio_.core().logMessage("Error: Could not change group, see StackTrace below:");
         studio_.core().logMessage(Arrays.toString(e.getStackTrace()));
         return;
      }
      presets = studio_.core().getAvailableConfigs(settings_.currGroup).toArray();
      trapPresetSelector = new JComboBox<>(presets);
      addSelector(trapPresetSelector, this::onTrapSelector);

      releasePresetSelector = new JComboBox<>(presets);
      addSelector(releasePresetSelector, this::onReleaseSelector);

      redraw();
   }

   private void onTrapSelector() {
      settings_.trapConfig = (String) trapPresetSelector.getSelectedItem();
   }

   private void onReleaseSelector() {
      settings_.releaseConfig = (String) releasePresetSelector.getSelectedItem();
   }

   private void onFlowSelector() {
      if (fluidDevices.length == 0) {
         return;
      }

      int idx = flowSelector.getSelectedIndex();
      settings_.flowController = fluidDevices[idx];
      settings_.flowType = deviceTypes[idx];

      if (deviceTypes[idx] == DeviceType.PressurePumpDevice) {
         flowActiveValueLabel.setText("Pressure during flow: ");
         flowInactiveValueLabel.setText("Pressure while stationary: ");
      } else {
         flowActiveValueLabel.setText("Flow rate during flow: ");
         flowInactiveValueLabel.setText("Flow rate while stationary: ");
      }
   }

   private void onValveSelector() {
      if (fluidDevices.length == 0) {
         return;
      }

      int idx = valveSelector.getSelectedIndex();
      settings_.valveController = fluidDevices[idx];
      settings_.valveType = deviceTypes[idx];

      if (deviceTypes[idx] == DeviceType.PressurePumpDevice) {
         valveOpenValueLabel.setText("Pressure valve open: ");
         valveClosedValueLabel.setText("Pressure valve closed: ");
      } else {
         valveOpenValueLabel.setText("Valve volume displacement: ");
         valveClosedValueLabel.setText("Valve displacement rate: ");
      }
   }

   private void redraw() {
      this.removeAll();
      if (groupSelector != null) {
         this.add(groupLabel);
         this.add(groupSelector, "wrap");
      }
      if (trapPresetSelector != null) {
         this.add(trapPresetLabel);
         this.add(trapPresetSelector, "wrap");
      }
      if (releasePresetSelector != null) {
         this.add(releasePresetLabel);
         this.add(releasePresetSelector, "wrap");
      }
      if (flowSelector != null) {
         this.add(flowLabel);
         this.add(flowSelector, "wrap");
      }
      if (valveSelector != null) {
         this.add(valveLabel);
         this.add(valveSelector, "wrap");
      }
      if (flowSelector != null) {
         this.add(flowActiveValueLabel);
         this.add(flowActiveTextField, "wrap");
         this.add(flowInactiveValueLabel);
         this.add(flowInactiveTextField, "wrap");
      }
      if (valveSelector != null) {
         this.add(valveOpenValueLabel);
         this.add(valveDisplacementTextField, "wrap");
         this.add(valveClosedValueLabel);
         this.add(valveDisplacementRateTextField, "wrap");
      }
      this.add(nTrapsLabel);
      this.add(nTrapsTextField, "wrap");
      this.add(triggerLabel);
      this.add(triggerValueTextField, "wrap");
      this.add(pixelSizeLabel);
      this.add(pixelSizeTextField, "wrap");
      this.add(minSizeLabel);
      this.add(minSizeTextField);
      this.revalidate();
      this.repaint();
   }

   private void getFluidDevices() {
      StrVector pressureDevices =
            studio_.core().getLoadedDevicesOfType(DeviceType.PressurePumpDevice);
      StrVector volumetricDevices =
            studio_.core().getLoadedDevicesOfType(DeviceType.VolumetricPumpDevice);
      int size = (int) (pressureDevices.size() + volumetricDevices.size());

      fluidDevices = new String[size];
      deviceTypes = new DeviceType[size];
      for (int i = 0; i < pressureDevices.size(); i++) {
         fluidDevices[i] = pressureDevices.get(i);
         deviceTypes[i] = DeviceType.PressurePumpDevice;
      }
      for (int i = 0; i < volumetricDevices.size(); i++) {
         fluidDevices[i + (int) pressureDevices.size()] = volumetricDevices.get(i);
         deviceTypes[i + (int) pressureDevices.size()] = DeviceType.VolumetricPumpDevice;
      }
   }

   private JFormattedTextField textFieldFactory(Settings.ParameterTypes parameter) {
      JFormattedTextField textField = new JFormattedTextField(formatter);
      textField.setBackground(new Color(240, 240, 240));
      textField.setForeground(new Color(60, 60, 60));
      textField.setValue(0.0);
      textField.setColumns(7);
      textField.addKeyListener(new KeyAdapter() {
         public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
               try {
                  double value = Double.parseDouble(textField.getText());
                  textField.setValue(value);
               } catch (Exception exception) {
                  studio_.getLogManager().logError(exception, "Error while setting value.");
               }
               settings_.setParameter(parameter,
                     Double.parseDouble(textField.getValue().toString()));
            }
         }
      });
      return textField;
   }
}
