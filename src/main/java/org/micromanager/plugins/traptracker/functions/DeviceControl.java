package org.micromanager.plugins.traptracker.functions;

import static java.lang.Math.abs;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import mmcorej.DeviceType;
import org.micromanager.PositionList;
import org.micromanager.PositionListManager;
import org.micromanager.SnapLiveManager;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.plugins.traptracker.Settings;

public class DeviceControl implements Runnable {

   private final Studio studio_;
   private final Settings settings_;
   private final PositionListManager positionListManager;
   private final AcquisitionManager acquisitionManager;
   private final SnapLiveManager snapLiveManager;
   private PositionList positionList;
   private final ImageAnalysis imgAnalysis;

   private final SequenceSettings.Builder builder;
   private final AtomicBoolean isClosed_ = new AtomicBoolean(false);
   private final AtomicBoolean isFlowing_ = new AtomicBoolean(false);

   private Thread automationThread;
   public AtomicBoolean running = new AtomicBoolean(false);
   private Datastore datastore;

   private final int threshold = (int) (255 * 0.85);
   private final int sigma = 3;


   public DeviceControl(Studio studio, Settings settings) {
      studio_ = studio;
      settings_ = settings;
      positionListManager = studio_.getPositionListManager();
      snapLiveManager = studio.getSnapLiveManager();
      acquisitionManager = studio.getAcquisitionManager();
      imgAnalysis = new ImageAnalysis(studio_);

      builder = new SequenceSettings.Builder();
      builder.usePositionList(true);
      builder.shouldDisplayImages(true);
      builder.numFrames(1);
   }

   public void openTrap() throws Exception {
      // Only open trap if it is closed
      if (!isClosed()) {
         return;
      }
      try {
         if (settings_.valveType == DeviceType.PressurePumpDevice) {
            studio_.core().setPumpPressureKPa(settings_.valveController,
                  settings_.valveOpenValue);
         } else if (settings_.valveType == DeviceType.VolumetricPumpDevice) {
            studio_.core().setPumpFlowrate(settings_.valveController, -settings_.valveClosedValue);
            studio_.core().pumpDispenseVolumeUl(settings_.valveController,
                  settings_.valveOpenValue);
         } else {
            studio_.core().logMessage("Cannot open trap, as no flow controller is set!");
         }
         isClosed_.set(false);
      } catch (Exception e) {
         throw new Exception("Error occurred while opening the trap", e);
      }
   }

   public void closeTrap() throws Exception {
      // Only close trap if it is open
      if (isClosed()) {
         return;
      }
      try {
         if (settings_.valveType == DeviceType.PressurePumpDevice) {
            studio_.core().setPumpPressureKPa(settings_.valveController,
                  settings_.valveClosedValue);
         } else if (settings_.valveType == DeviceType.VolumetricPumpDevice) {
            studio_.core().setPumpFlowrate(settings_.valveController, settings_.valveClosedValue);
            studio_.core().pumpDispenseVolumeUl(settings_.valveController,
                  settings_.valveOpenValue);
         } else {
            studio_.core().logMessage("Cannot close trap, as no flow controller is set!");
         }
         isClosed_.set(true);
      } catch (Exception e) {
         throw new Exception("Error occurred while closing the trap", e);
      }
   }

   public boolean isClosed() {
      return isClosed_.get();
   }

   public boolean isFlowing() {
      return isFlowing_.get();
   }

   public void startFlow() throws Exception {
      // Only start flow if it is stopped
      if (isFlowing()) {
         return;
      }

      try {
         if (settings_.flowType == DeviceType.PressurePumpDevice) {
            studio_.getLogManager().logMessage("Starting flow: setting pressure of "
                  + settings_.flowController
                  + " to: "
                  + settings_.flowActiveValue);
            studio_.core().setPumpPressureKPa(settings_.flowController,
                  settings_.flowActiveValue);
         } else if (settings_.flowType == DeviceType.VolumetricPumpDevice) {
            studio_.getLogManager().logMessage("Starting flow with flowrate: "
                  + settings_.flowActiveValue);
            studio_.core().setPumpFlowrate(settings_.flowController,
                  settings_.flowActiveValue);
            studio_.core().pumpStart(settings_.flowController);
         } else {
            studio_.core().logMessage("Cannot start flow, as no flow controller is set!");
         }
         isFlowing_.set(true);
      } catch (Exception e) {
         throw new Exception("Error occurred while starting the flow", e);
      }
   }

   public void stopFlow() throws Exception {
      // Only stop flow if it is flowing
      if (!isFlowing()) {
         return;
      }

      try {
         if (settings_.flowType == DeviceType.PressurePumpDevice) {
            studio_.getLogManager().logMessage("Stopping flow: setting pressure of "
                  + settings_.flowController
                  + " to: "
                  + settings_.flowInactiveValue);
            studio_.core().setPumpPressureKPa(settings_.flowController,
                  settings_.flowInactiveValue);
         } else if (settings_.flowType == DeviceType.VolumetricPumpDevice) {
            studio_.getLogManager().logMessage("Stopping flow");
            studio_.core().volumetricPumpStop(settings_.flowController);
         } else {
            studio_.core().logMessage("Cannot start flow, as no flow controller is set!");
         }
         isFlowing_.set(false);
      } catch (Exception e) {
         throw new Exception("Error occurred while stopping the flow", e);
      }
   }

   public void setPosition(double x, double y) throws Exception {
      try {
         studio_.core().setXYPosition(x, y);
      } catch (Exception e) {
         throw new Exception("Error occurred while moving the stage", e);
      }
   }

   public void startAutomation() throws InterruptedException {
      if (!snapLiveManager.isLiveModeOn()) {
         snapLiveManager.setLiveModeOn(true);
         studio_.core().logMessage("Camera was not live, so it was turned on automatically.");
         TimeUnit.SECONDS.sleep(1); // Wait to fill the circular buffer
      }

      if (!running.get()) {
         positionList = positionListManager.getPositionList();
         settings_.setParameter(Settings.ParameterTypes.isRunning, 1.0);
         automationThread = new Thread(this);
         automationThread.start();
      }
   }

   public void stopAutomation() {
      if (running.get()) {
         running.set(false);
         settings_.setParameter(Settings.ParameterTypes.isRunning, 0.0);
      }
   }

   @Override
   public void run() {
      runFullAnalysis();
      // trackTrapFilling();
      // debugImageAnalysis();
   }

   private void waitForParticles() throws Exception {
      int width = (int) studio_.core().getImageWidth();
      int height = (int) studio_.core().getImageHeight();
      int depth = (int) studio_.core().getImageBitDepth();
      do {
         // Count particles
         int trapsFilled = imgAnalysis.getParticleCount(
               (byte[]) studio_.core().getLastImage(),
               width,
               height,
               depth / 8,
               sigma,
               threshold,
               settings_.minSize
         );
         settings_.trapsFilled.set(trapsFilled);

         TimeUnit.SECONDS.sleep(1);
         // Tracking number of trapped particles over time
      } while (settings_.trapsFilled.get() < settings_.triggerValue && running.get());
   }

   private void debugImageAnalysis() {
      running.set(true);
      while (running.get()) {
         try {
            BufferedImage img;
            String path = "C:\\Users\\Lars\\Documents\\GitHub\\"
                  + "MM_fork\\micro-manager-IDS_peak\\plugins\\ParticleTrapping\\rsc\\img_1.jpg";
            img = ImageIO.read(new File(path));
            byte[] bytes = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            int trapsFilled = imgAnalysis.getParticleCount(
                  bytes,
                  img.getWidth(),
                  img.getHeight(),
                  1,
                  sigma,
                  threshold,
                  settings_.minSize
            ); // Count particles
            settings_.trapsFilled.set(trapsFilled);
            studio_.getLogManager().logMessage("Traps filled: " + trapsFilled);

            TimeUnit.SECONDS.sleep(1);
         } catch (Exception e) {
            studio_.getLogManager().logError(e, "Something went wrong during image analysis.");
            running.set(false);
         }
         running.set(false);
      }
   }

   // Debug function that can run on TP microscope
   private void trackTrapFilling() {
      running.set(true);
      try {
         // Main experimental loop
         while (running.get()) {
            // Close traps and start flow to start capturing particles
            // closeTrap();
            // startFlow();

            // Actual main function
            waitForParticles();

            if (!running.get()) {
               studio_.getLogManager().logMessage("Automation stopped by user.");
               break;
            }

            // Found enough particles
            // studio_.alerts().postUpdatableAlert("Particle Trapping Alert.",
            // "Threshold reached.");
            // TimeUnit.SECONDS.sleep(3);

            // // Once enough particles are detected, stop flow
            // stopFlow();
            // TimeUnit.SECONDS.sleep(1);

            // // Open traps start flow
            // openTrap();
            // startFlow();
            // if (settings_.valveType == DeviceType.VolumetricPumpDevice) {
            //    TimeUnit.SECONDS.sleep(
            //          (long) (1.5 * abs(settings_.valveOpenValue / settings_.valveClosedValue)));
            // } else {
            //    TimeUnit.SECONDS.sleep(5);
            // }
         }
      } catch (Exception e) {
         studio_.core().logMessage("Error occurred during the automated experiments.");
         studio_.getLogManager().logError(e);
         running.set(false);
         settings_.isRunning = 0.0;
      }
   }

   private void runFullAnalysis() {
      running.set(true);
      try {
         acquisitionManager.setAcquisitionSettings(builder.build());

         // Main experimental loop
         while (running.get()) {

            // Set position to center of channel and switch to overview
            // objective
            setPosition(settings_.overviewX, settings_.overviewY);
            studio_.core().setConfig(settings_.currGroup, settings_.trapConfig);

            // Close traps and start flow to start capturing particles
            closeTrap();
            startFlow();

            // Wait until the required number of particles are trapped
            waitForParticles();

            // Once enough particles are detected, stop flow, switch objective to zoom
            stopFlow();

            // Stop live recording (if not, you cannot take images
            snapLiveManager.setLiveModeOn(false);
            datastore = acquisitionManager.runAcquisition();
            snapLiveManager.setLiveModeOn(true);
            TimeUnit.SECONDS.sleep(1); // Fill circular buffer

            // Open traps start flow
            openTrap();
            startFlow();
            studio_.core().setConfig(settings_.currGroup, settings_.releaseConfig);
            TimeUnit.SECONDS.sleep(10); // Allow particles to flow out
         }
      } catch (Exception e) {
         studio_.core().logMessage("Error occurred during the automated experiments.");
         studio_.getLogManager().logError(e);
         running.set(false);
         settings_.isRunning = 0.0;
      }
   }

   private void previewAnalysis() {
      try {
         Image img = (Image) studio_.core().getLastImage();
      } catch (Exception e) {
         studio_.getLogManager().logError(e, "Could not snap Image.");
      }

   }
}
