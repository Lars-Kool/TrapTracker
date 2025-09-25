package org.micromanager.plugins.traptracker.functions;

import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import org.micromanager.Studio;

public class ImageAnalysis {

   private final Studio studio_;
   private final String homeDir =
         new JFileChooser().getFileSystemView().getDefaultDirectory().toString();
   private final double cutoffCircularity = 23;
   private String path;
   private int imageCounter = 0;

   ImageAnalysis(Studio studio) {
      this.studio_ = studio;

      try {
         checkFileSystem();
      } catch (Exception e) {
         studio_.getLogManager().logError(e, "Could not create folder.");
      }
   }

   // Takes image and image analysis parameters and gives number of particles detected
   public int getParticleCount(byte[] img,
                               int width,
                               int height,
                               int depth,
                               int sigma,
                               int threshold,
                               int minSize) {
      int[] pixels = imageToPixels(img, width, height, depth);
      pretreatImage(pixels, width, height, depth, sigma, threshold);
      return countParticles(pixels, width, height, minSize);
   }

   private int[] imageToPixels(byte[] img, int width, int height, int depth) {
      int[] imgOut = new int[width * height];
      for (int i = 0; i < imgOut.length; i++) {
         imgOut[i] = 0;
         for (int d = 0; d < depth; d++) {
            imgOut[i] <<= 8;
            imgOut[i] |= (img[depth * i + d] & 0xff);
         }
      }
      return imgOut;
   }

   public void pretreatImage(int[] pixels,
                             int width,
                             int height,
                             int depth,
                             int sigma,
                             int threshold
   ) {
      saveImg(pixels, width, height, 1, "Raw_image");

      // Detect black particles, so invert image
      invert(pixels, depth);
      saveImg(pixels, width, height, 1, "After_invert");

      // medianBlur blurs along x, and transposes the output
      // Blurring twice will do 2D-blur, and keep image upright
      meanBlur(pixels, width, height, sigma);
      meanBlur(pixels, height, width, sigma);
      saveImg(pixels, width, height, 1, "After_blur");

      threshold(pixels, threshold);
      saveImg(pixels, width, height, 1, "After_threshold");

      // Particles have bright spot in center, fill it
      fillHoles(pixels, width, height);
      saveImg(pixels, width, height, 1, "After_filling_holes");
   }

   public void invert(int[] pixels, int depth) {
      for (int i = 0; i < pixels.length; i++) {
         pixels[i] = (1 << (8 * depth)) - pixels[i] - 1;
      }
   }

   public void fillHoles(int[] pixels, int width, int height) {
      // Set all background pixels to temporary value
      for (int i = 0; i < pixels.length; i++) {
         if (pixels[i] == 0) {
            pixels[i] = 2;
         }
      }

      // Add all edge background pixels to a queue (these are know to be outside
      // a particle.
      Queue<Integer> queue = new LinkedList<>();
      // Vertical edges
      for (int y = 0; y < height; y++) {
         int i = y * width;
         if (pixels[i] == 2) {
            pixels[i] = 0;
            queue.add(i);
         }
         i = y * width + width - 1;
         if (pixels[i] == 2) {
            pixels[i] = 0;
            queue.add(i);
         }
      }

      // Horizontal edges
      for (int x = 0; x < width; x++) {
         int i = x;
         if (pixels[i] == 2) {
            pixels[i] = 0;
            queue.add(i);
         }
         i = (height - 1) * width + x;
         if (pixels[i] == 2) {
            pixels[i] = 0;
            queue.add(i);
         }
      }

      // Walk the queue. Every pixel neighboring a known outside pixel, is also outside
      while (!queue.isEmpty()) {
         int i = queue.remove();
         // Left
         if (i % width != 0 && pixels[i - 1] == 2) {
            pixels[i - 1] = 0;
            queue.add(i - 1);
         }
         // Above
         if (i / width != 0 && pixels[i - width] == 2) {
            pixels[i - width] = 0;
            queue.add(i - width);
         }
         // Right
         if (i % width != width - 1 && pixels[i + 1] == 2) {
            pixels[i + 1] = 0;
            queue.add(i + 1);
         }
         // Below
         if (i / width != height - 1 && pixels[i + width] == 2) {
            pixels[i + width] = 0;
            queue.add(i + width);
         }
      }

      // All pixels not reset to 0 must be inside. Set them to 1
      for (int i = 0; i < pixels.length; i++) {
         if (pixels[i] == 2) {
            pixels[i] = 255;
         }
      }
   }

   public int countParticles(int[] pixels, int width, int height, int minSize) {
      int particleCount = 0;
      Queue<Integer> queue = new LinkedList<>();
      for (int i = 0; i < pixels.length; i++) {
         // Skip background or already visited pixels
         if (pixels[i] < 1) {
            continue;
         }

         pixels[i] = -1; // Set visited pixel to -1 to distinguish from foreground and background
         queue.add(i);
         int area = 0;
         int perimeter = 0;
         while (!queue.isEmpty()) {
            area++;
            Integer idx = queue.remove();
            // Left
            if (idx % width == 0 || pixels[idx - 1] == 0) {
               perimeter++;
            } else if (pixels[idx - 1] > 0) {
               queue.add(idx - 1);
               pixels[idx - 1] = -1;
            }
            // Right
            if (idx % width == width - 1 || pixels[idx + 1] == 0) {
               perimeter++;
            } else if (pixels[idx + 1] > 0) {
               queue.add(idx + 1);
               pixels[idx + 1] = -1;
            }
            // Top
            if (idx / width == 0 || pixels[idx - width] == 0) {
               perimeter++;
            } else if (pixels[idx - width] > 0) {
               queue.add(idx - width);
               pixels[idx - width] = -1;
            }
            // Bottom
            if (idx / width == height - 1 || pixels[idx + width] == 0) {
               perimeter++;
            } else if (pixels[idx + width] > 0) {
               queue.add(idx + width);
               pixels[idx + width] = -1;
            }
         }
         // Circularity check. For a circle Perimeter^2/Area = 4 * Pi ~ 12.56.
         // All other shapes have a higher P/A ratio. Algorithm is suboptimal, so
         // Circles have a P/A ratio of ~20, cutoff is set at 23
         // if ((double) (perimeter * perimeter) / area < cutoffCircularity && area > minSize) {
         //    particleCount++;
         // }
         if (area > minSize) {
            particleCount++;
         }
      }
      return particleCount;
   }

   public void threshold(int[] pixels, int threshold) {
      for (int i = 0; i < pixels.length; i++) {
         pixels[i] = (pixels[i] > threshold) ? 255 : 0;
      }
   }

   // Blur along x-direction, output is transposed, such that after two passes
   // the image is 2D-blurred.
   public void meanBlur(int[] pixels, int width, int height, int sigma) {
      if (sigma <= 1) {
         System.out.println("Kernel too small. No blurring was performed.");
         return;
      }

      int[] temp = new int[pixels.length];
      int partialSum = 0;
      int size = 2 * sigma + 1;
      for (int y = 0; y < height; y++) {
         // Initialize window
         partialSum = 0;
         for (int i = -sigma; i < sigma + 1; i++) {
            partialSum += (i < 0) ? pixels[y * width] : pixels[y * width + i];
         }
         // Bulk
         for (int x = 0; x < width; x++) {
            temp[x * height + y] = partialSum / size;
            partialSum += (x + sigma + 1 >= width)
                  ? pixels[y * width] : pixels[y * width + x + sigma + 1];
            partialSum -= (x - sigma < 0) ? pixels[y * width] : pixels[y * width + x - sigma];
         }
      }
      System.arraycopy(temp, 0, pixels, 0, width * height);
   }

   private void checkFileSystem() throws IOException {
      path = homeDir + "\\Lars";
      if (!Files.isDirectory(Paths.get(path))) {
         Files.createDirectory(Paths.get(path));
      }

      path += "\\" + new SimpleDateFormat("yyyyMMdd").format(new Date());
      if (!Files.isDirectory(Paths.get(path))) {
         Files.createDirectory(Paths.get(path));
      }

      int id = new File(path).listFiles().length;
      path += "\\" + id;
      Files.createDirectory(Paths.get(path));
   }

   private void saveImg(int[] pixels, int width, int height, int depth, String name) {
      try {
         BufferedImage b = new BufferedImage(width, height, TYPE_BYTE_GRAY);
         for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
               int val = pixels[y * width + x];
               b.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
         }
         ImageIO.write(b, "jpg", new File(path + "\\" + imageCounter + "_" + name + ".jpg"));
         imageCounter++;
      } catch (Exception e) {
         studio_.getLogManager().logMessage("Could not write image.");
         studio_.getLogManager().logError(e);
      }
   }
}
