package org.micromanager.plugins.traptracker;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicInteger;
import mmcorej.DeviceType;
import org.micromanager.Studio;


public final class Settings {
   public enum ParameterTypes {
      flowActive,
      flowInactive,
      valveDisplacement,
      valveDisplacementRate,
      nTraps,
      trigger,
      trapsFilled,
      pixelSize,
      minSize,
      isRunning,
      None
   }

   Studio studio_;
   private final PropertyChangeSupport support;

   public double isRunning = 0.0;
   public String flowController = "";
   public DeviceType flowType = DeviceType.UnknownType;
   public double flowActiveValue = 0;
   public double flowInactiveValue = 0;

   public String valveController = "";
   public DeviceType valveType = DeviceType.UnknownType;
   public double valveOpenValue = 0; // Displacement volume for volumetric pump
   public double valveClosedValue = 0; // Rate for volumetric pump

   public int nTrapsValue = 0;
   public double triggerValue = 0;
   public AtomicInteger trapsFilled = new AtomicInteger(0);

   public String currGroup = "";
   public String trapConfig = "";
   public String releaseConfig = "";
   public double pixelSize = 1;

   public double overviewX = 0;
   public double overviewY = 0;

   public int imageWidth = 0;
   public int imageHeight = 0;
   public int imageDepth = 0;
   public int minSize = 0;

   public byte[] background;

   public Settings(Studio studio) {
      studio_ = studio;
      support = new PropertyChangeSupport(this);
   }

   public void addPropertyChangeListener(PropertyChangeListener pcl) {
      support.addPropertyChangeListener(pcl);
   }

   public void removePropertyChangeListener(PropertyChangeListener pcl) {
      support.removePropertyChangeListener(pcl);
   }

   public void setParameter(ParameterTypes parameterType, Object value) {
      switch (parameterType) {
         case flowActive:
            support.firePropertyChange("flowActiveValue", flowActiveValue, value);
            flowActiveValue = (double) value;
            break;
         case flowInactive:
            support.firePropertyChange("flowInactiveValue", flowInactiveValue, value);
            flowInactiveValue = (double) value;
            break;
         case valveDisplacement:
            support.firePropertyChange("valveDisplacementValue", valveOpenValue, value);
            valveOpenValue = (double) value;
            break;
         case valveDisplacementRate:
            support.firePropertyChange("valveDisplacementRateValue", valveClosedValue, value);
            valveClosedValue = (double) value;
            break;
         case nTraps:
            support.firePropertyChange("nTrapsValue", nTrapsValue, value);
            nTrapsValue = ((Double) value).intValue();
            break;
         case trigger:
            support.firePropertyChange("triggerValue", triggerValue, value);
            triggerValue = (double) value;
            break;
         case trapsFilled:
            support.firePropertyChange("trapsFilled", trapsFilled, value);
            trapsFilled.set(((Double) value).intValue());
            break;
         case pixelSize:
            support.firePropertyChange("pixelSize", pixelSize, value);
            pixelSize = (double) value;
            break;
         case minSize:
            support.firePropertyChange("minSize", minSize, value);
            minSize = ((Double) value).intValue();
            break;
         case isRunning:
            support.firePropertyChange("isRunning", isRunning, value);
            isRunning = (double) value;
            break;
         default:
            studio_.core().logMessage("Unknown parameter type encountered");
            break;
      }
   }
}
