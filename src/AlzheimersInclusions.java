// Detect Alzheimer's inclusions.
// Adapted from JavaPixelManipulation.java.

//import java.awt.*;
//import java.awt.event.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.vecmath.Vector2d;
import java.util.ArrayList;

public class AlzheimersInclusions extends JPanel
{
   private static final long serialVersionUID = 1L;

   /**
    * The main routine simply opens a window that shows a panel.
    */
   public static void main(String[] args)
   {
      AlzheimersInclusions az = new AlzheimersInclusions();

      switch (args.length)
      {
      case 0:
         az.init(null);
         break;

      case 1:
         az.init(args[0]);
         break;

      case 4:
         az.ITERATIONS = Integer.parseInt(args[0]);
         az.PIXEL_NEIGHBORHOOD_RADIUS = Integer.parseInt(args[1]);
         az.PIXEL_MOVEMENT            = Float.parseFloat(args[2]);
         az.init(args[3]);
         break;

      default:
         System.err.println("Usage: java AlzheimersInclusions [<image file>]");
         System.exit(1);
      }
   }


   // Image loading.
   private JMenuItem saveImageMenuItem;
   private JMenuItem reloadImageMenuItem;
   private JMenuItem runMenuItem;
   private JMenuItem abortMenuItem;
   private File      currentFile = null;

   // Cell locating.
   public int    ITERATIONS = 1000;
   public int    PIXEL_NEIGHBORHOOD_RADIUS = 10;
   public double PIXEL_MOVEMENT            = 0.1;

   // Red color?
   private boolean isRed(int color)
   {
      if ((color & 0x00FF0000) > 0)
      {
         return(true);
      }
      else
      {
         return(false);
      }
   }


   // Green color?
   private boolean isGreen(int color)
   {
      if ((color & 0x0000FF00) > 0)
      {
         return(true);
      }
      else
      {
         return(false);
      }
   }


   // Blue (light blue)?
   private boolean isBlue(int color)
   {
      if ((color & 0x000000FF) >= 0x20)
      {
         return(true);
      }
      else
      {
         return(false);
      }
   }


   // Black (dark blue)?
   private boolean isBlack(int color)
   {
      color = color & 0x000000FF;
      if ((color >= 0) && (color < 0x20))
      {
         return(true);
      }
      else
      {
         return(false);
      }
   }


   // Pixel tracking.
   public class Pixel
   {
      Point    p0;
      Vector2d pt;
      Vector2d pd;
      int      rgb;

      public Pixel(int x, int y, int rgb)
      {
         p0       = new Point(x, y);
         pt       = new Vector2d((double)x + 0.5f, (double)y + 0.5f);
         pd       = new Vector2d((double)x + 0.5f, (double)y + 0.5f);
         this.rgb = rgb;
      }


      public void print()
      {
         System.out.println("p0: x=" + p0.x + ", y=" + p0.y);
         System.out.println("pt: x=" + pt.x + ", y=" + pt.y);
         System.out.println("pd: x=" + pd.x + ", y=" + pd.y);
         int color = rgb;
         System.out.print("color=" + String.format("0x%08X", color));
         if (isRed(color))
         {
            System.out.print(",red");
         }
         if (isBlue(color))
         {
            System.out.print(",blue");
         }
         if (isBlack(color))
         {
            System.out.print(",black");
         }
         System.out.println();
      }
   }
   public Pixel[][] Pixels = null;
   public ArrayList<Pixel>[][] PixelTracker = null;

   // Drawing.
   public static final int DISPLAY_DELAY = 50;
   private JFrame          window;
   private JScrollPane     scrollFrame;
   private BufferedImage   OSC;
   private Graphics2D      OSG;
   private BufferedImage   currentImage = null;
   private BufferedImage   drawImage    = null;
   private Graphics2D      drawGraphics;
   private int             imageWidth  = -1;
   private int             imageHeight = -1;

   // Synchronization.
   public static final int ABORT_DELAY = 10;
   private Object          lock;
   private boolean         running;
   private boolean         abort;

   /**
    * Constructor,
    */
   public AlzheimersInclusions()
   {
   }


   /**
    * Set the preferred size of the panel, creates the BufferedImage,
    * installs a mouse listener on the panel and optionally load an image file.
    */
   public void init(String filename)
   {
      setPreferredSize(new Dimension(640, 480));
      setAutoscrolls(true);
      OSC = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
      OSG = OSC.createGraphics();
      OSG.setColor(Color.WHITE);
      OSG.fillRect(0, 0, 640, 480);
      OSG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      addMouseListener(new MouseHandler());
      window      = new JFrame("Alzheimer's Inclusions");
      scrollFrame = new JScrollPane(this);
      scrollFrame.setPreferredSize(new Dimension(640, 480));
      window.add(scrollFrame);
      window.setJMenuBar(this.getMenuBar());
      window.pack();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      window.setLocation((screenSize.width - window.getWidth()) / 2,
                         (screenSize.height - window.getHeight()) / 2);
      window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      lock    = new Object();
      running = false;
      abort   = false;
      window.setVisible(true);

      // Load image file?
      if (filename != null)
      {
         currentFile = new File(filename);
         if (loadImageFile())
         {
            initPixels();
            reloadImageMenuItem.setEnabled(true);
            saveImageMenuItem.setEnabled(true);
            runMenuItem.setEnabled(true);
         }
      }

      // Start drawing.
      drawCells();
   }


   /**
    * Create the menus for the program, and provide listeners to implement the menu commands.
    */
   private JMenuBar getMenuBar()
   {
      JMenuBar       menuBar   = new JMenuBar();
      ActionListener flistener = new ActionListener()
      {
         public void actionPerformed(ActionEvent evt)
         {
            switch (evt.getActionCommand())
            {
            case "Quit":
               window.dispose();
               System.exit(0);
               break;

            case "Load Image...":
               if (selectImageFile())
               {
                  initPixels();
               }
               break;

            case "Reload Image":
               if (loadImageFile())
               {
                  initPixels();
               }
               break;

            case "Save Image":
               saveImageFile();
               break;
            }
         }
      };
      JMenu file = new JMenu("File");

      file.add(makeMenuItem("Load Image...", flistener));
      reloadImageMenuItem = makeMenuItem("Reload Image", flistener);
      file.add(reloadImageMenuItem);
      reloadImageMenuItem.setEnabled(false);
      saveImageMenuItem = makeMenuItem("Save Image", flistener);
      file.add(saveImageMenuItem);
      saveImageMenuItem.setEnabled(false);
      file.addSeparator();
      file.add(makeMenuItem("Quit", flistener));
      menuBar.add(file);
      ActionListener tlistener = new ActionListener()
      {
         public void actionPerformed(ActionEvent evt)
         {
            String action = evt.getActionCommand();

            if (action != null)
            {
               if (action.equals("Run"))
               {
                  // Start locating cells on separate thread.
                  new Thread(new Runnable()
                             {
                                @Override
                                public void run()
                                {
                                   locateCells();
                                }
                             }
                             ).start();
               }
               if (action.equals("Abort"))
               {
                  synchronized (lock)
                  {
                     abortRun();
                  }
               }
            }
         }
      };
      JMenu inclusions = new JMenu("Inclusions");
      runMenuItem = makeMenuItem("Run", tlistener);
      inclusions.add(runMenuItem);
      runMenuItem.setEnabled(false);
      abortMenuItem = makeMenuItem("Abort", tlistener);
      inclusions.add(abortMenuItem);
      abortMenuItem.setEnabled(false);
      menuBar.add(inclusions);
      return(menuBar);
   }


   /**
    * Initialize pixels.
    */
   @SuppressWarnings("unchecked")
   private void initPixels()
   {
      if (currentImage != null)
      {
         Pixels       = new Pixel[imageWidth][imageHeight];
         PixelTracker = new ArrayList[imageWidth][imageHeight];
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               Pixel pixel = new Pixel(x, y, currentImage.getRGB(x, y));
               Pixels[x][y] = pixel;
            }
         }

         // Remove noisy blue pixels.
         for (int i = 0; i < 10; i++)
         {
            for (int x = 0; x < imageWidth; x++)
            {
               for (int y = 0; y < imageHeight; y++)
               {
                  Pixel pixel = Pixels[x][y];
                  if (isBlue(pixel.rgb))
                  {
                     int blueNeighbors = 0;
                     for (int x2 = x - 1; x2 <= x + 1; x2++)
                     {
                        for (int y2 = y - 1; y2 <= y + 1; y2++)
                        {
                           if ((x2 >= 0) && (x2 < imageWidth) && (y2 >= 0) && (y2 < imageHeight))
                           {
                              if ((x != x2) || (y != y2))
                              {
                                 if (isBlue(Pixels[x2][y2].rgb))
                                 {
                                    blueNeighbors++;
                                 }
                              }
                           }
                        }
                     }
                     if (blueNeighbors < 4)
                     {
                        pixel.rgb = Color.BLACK.getRGB();
                        currentImage.setRGB(x, y, Color.BLACK.getRGB());
                        drawImage.setRGB(x, y, Color.BLACK.getRGB());
                     }
                  }
               }
            }
         }
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               Pixel pixel = Pixels[x][y];
               PixelTracker[x][y] = new ArrayList<Pixel>();
               if (isBlue(pixel.rgb))
               {
                  PixelTracker[x][y].add(pixel);
               }
            }
         }
      }
   }


   /**
    * Utility method used by getMenuBar to create menu items.
    */
   private JMenuItem makeMenuItem(String itemName, ActionListener listener)
   {
      JMenuItem item = new JMenuItem(itemName);

      item.addActionListener(listener);
      return(item);
   }


   /**
    * Select and load an image from a file selected by the user.
    */
   private boolean selectImageFile()
   {
      JFileChooser fileDialog;

      fileDialog = new JFileChooser();
      fileDialog.setSelectedFile(null);
      int option = fileDialog.showOpenDialog(this);
      if (option != JFileChooser.APPROVE_OPTION)
      {
         return(false);
      }
      File saveFile = currentFile;
      currentFile = fileDialog.getSelectedFile();
      if (loadImageFile())
      {
         reloadImageMenuItem.setEnabled(true);
         saveImageMenuItem.setEnabled(true);
         runMenuItem.setEnabled(true);
         return(true);
      }
      else
      {
         currentFile = saveFile;
         return(false);
      }
   }


   /**
    * Load an image from a file.  The image is scaled to
    * exactly fill the panel, possibly changing the aspect ratio.
    */
   private boolean loadImageFile()
   {
      if (currentFile == null)
      {
         JOptionPane.showMessageDialog(this, "Please load a file");
         return(false);
      }
      FileInputStream stream = null;
      try
      {
         stream = new FileInputStream(currentFile);
      }
      catch (Exception e)
      {
         JOptionPane.showMessageDialog(this, "An error occurred while trying to open the file:\n" + e.getMessage());
         return(false);
      }
      synchronized (lock)
      {
         try
         {
            abortRun();
            BufferedImage image = ImageIO.read(stream);
            if (image == null)
            {
               JOptionPane.showMessageDialog(this, "File does not contain a recognized image format");
               return(false);
            }
            int w = image.getWidth();
            int h = image.getHeight();
            if ((w <= 0) || (h <= 0))
            {
               JOptionPane.showMessageDialog(this, "File does not contain a recognized image format");
               return(false);
            }
            Graphics g = OSC.createGraphics();
            g.drawImage(image, 0, 0, OSC.getWidth(), OSC.getHeight(), null);
            g.dispose();
            repaint();
            currentImage = image;
            imageWidth   = w;
            imageHeight  = h;
            drawImage    = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            drawImage.createGraphics();
            drawGraphics = (Graphics2D)drawImage.getGraphics();
            drawGraphics.drawImage(currentImage, 0, 0, imageWidth, imageHeight,
                                   0, 0, imageWidth, imageHeight, Color.WHITE, null);
            resizeContent(imageWidth, imageHeight);
            OSG.drawImage(drawImage, 0, 0, imageWidth, imageHeight, null);
         }
         catch (Exception e)
         {
            imageWidth   = imageHeight = -1;
            currentImage = drawImage = null;
            reloadImageMenuItem.setEnabled(false);
            saveImageMenuItem.setEnabled(false);
            runMenuItem.setEnabled(false);
            abortMenuItem.setEnabled(false);
            JOptionPane.showMessageDialog(this, "An error occurred while trying to create image:\n" + e.getMessage());
            return(false);
         }
      }
      return(true);
   }


   /**
    * Save an image to a file given by the user.
    */
   private void saveImageFile()
   {
      if (currentFile == null)
      {
         JOptionPane.showMessageDialog(this, "Please load a file");
         return;
      }
      JFileChooser fileDialog;

      fileDialog = new JFileChooser();
      fileDialog.setSelectedFile(null);
      int option = fileDialog.showOpenDialog(this);
      if (option != JFileChooser.APPROVE_OPTION)
      {
         return;
      }
      File outputFile = fileDialog.getSelectedFile();
      try
      {
         ImageIO.write(currentImage, "png", outputFile);
      }
      catch (IOException e) {
         JOptionPane.showMessageDialog(this, "An error occurred while trying to write the image:\n" + e.getMessage());
         return;
      }
   }


   /**
    *  Defines the mouse listener object that responds to user mouse actions on
    *  the panel.
    */
   private class MouseHandler extends MouseAdapter
   {
      public void mousePressed(MouseEvent evt)
      {
         if (imageHeight != -1)
         {
            try
            {
               int x = evt.getX();
               int y = evt.getY();
               if (imageHeight != -1)
               {
                  if ((x < 0) || (x >= imageWidth))
                  {
                     return;
                  }
                  if ((x < 0) || (x >= imageWidth))
                  {
                     return;
                  }
               }
               int color = currentImage.getRGB(x, y);
               System.out.print("point=" + x + "," + y + " color=" + String.format("0x%08X", color));
               if (isRed(color))
               {
                  System.out.print(",red");
               }
               if (isGreen(color))
               {
                  System.out.print(",green");
               }
               if (isBlue(color))
               {
                  System.out.print(",blue");
               }
               if (isBlack(color))
               {
                  System.out.print(",black");
               }
               System.out.println();
            }
            catch (Exception e) {}
         }
      }
   }

   /**
    * Locate cells.
    */
   private void locateCells()
   {
      synchronized (lock)
      {
         if (drawImage == null)
         {
            JOptionPane.showMessageDialog(this, "Error: image not loaded");
            return;
         }
         running = true;
      }
      runMenuItem.setEnabled(false);
      abortMenuItem.setEnabled(true);
      updateImage();
      for (int i = 0; i < ITERATIONS; i++)
      {
         System.out.println("iteration=" + (i + 1) + "/" + ITERATIONS);
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               if (abort)
               {
                  running = false;
                  runMenuItem.setEnabled(true);
                  abortMenuItem.setEnabled(false);
                  JOptionPane.showMessageDialog(this, "Aborting");
                  return;
               }
               Pixel pixel = Pixels[x][y];
               if (isBlue(pixel.rgb))
               {
                  Vector2d f = new Vector2d();
                  for (int x2 = (int)pixel.pt.x - PIXEL_NEIGHBORHOOD_RADIUS; x2 <= (int)pixel.pt.x + PIXEL_NEIGHBORHOOD_RADIUS; x2++)
                  {
                     for (int y2 = (int)pixel.pt.y - PIXEL_NEIGHBORHOOD_RADIUS; y2 <= (int)pixel.pt.y + PIXEL_NEIGHBORHOOD_RADIUS; y2++)
                     {
                        if ((x2 >= 0) && (x2 < imageWidth) && (y2 >= 0) && (y2 < imageHeight))
                        {
                           for (Pixel pixel2 : PixelTracker[x2][y2])
                           {
                              if (pixel2 == pixel) { continue; }
                              Vector2d v = new Vector2d(pixel2.pt);
                              v.sub(pixel.pt);
                              double d = v.length();
                              v.normalize();
                              if (d > 1.0)
                              {
                                 v.scale(1.0 / (d * d));
                              }
                              f.add(v);
                           }
                        }
                     }
                  }
                  pixel.pd.set(pixel.pt);
                  double d = f.length();
                  if (d > 0.0)
                  {
                     f.normalize();
                     f.scale(PIXEL_MOVEMENT);
                     pixel.pd.add(f);
                  }
               }
            }
         }
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               PixelTracker[x][y].clear();
               Pixel pixel = Pixels[x][y];
               if (isBlue(pixel.rgb))
               {
                  pixel.pt.x = pixel.pd.x;
                  pixel.pt.y = pixel.pd.y;
                  if ((int)pixel.pt.x < 0)
                  {
                     pixel.pt.x = 0.0;
                  }
                  if ((int)pixel.pt.x >= imageWidth)
                  {
                     pixel.pt.x = (double)(imageWidth - 1);
                  }
                  if ((int)pixel.pt.y < 0)
                  {
                     pixel.pt.y = 0.0;
                  }
                  if ((int)pixel.pt.y >= imageHeight)
                  {
                     pixel.pt.x = (double)(imageHeight - 1);
                  }
               }
            }
         }
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               Pixel pixel = Pixels[x][y];
               if (isBlue(pixel.rgb))
               {
                  PixelTracker[(int)pixel.pt.x][(int)pixel.pt.y].add(pixel);
               }
            }
         }
         updateImage();
      }
      finalizeImage();
      printCellCoordinates();
      running = false;
      runMenuItem.setEnabled(true);
      abortMenuItem.setEnabled(false);
      JOptionPane.showMessageDialog(this, "Done");
   }


   /**
    * Update image.
    */
   private void updateImage()
   {
      drawGraphics.drawImage(currentImage, 0, 0, imageWidth, imageHeight,
                             0, 0, imageWidth, imageHeight, Color.WHITE, null);
      for (int x = 0; x < imageWidth; x++)
      {
         for (int y = 0; y < imageHeight; y++)
         {
            Pixel pixel = Pixels[x][y];
            if (isBlue(pixel.rgb))
            {
               drawImage.setRGB((int)pixel.pt.x, (int)pixel.pt.y, Color.GREEN.getRGB());
            }
         }
      }
   }


   /**
    * Finalize image.
    */
   private void finalizeImage()
   {
      for (int x = 0; x < imageWidth; x++)
      {
         for (int y = 0; y < imageHeight; y++)
         {
            Pixel pixel = Pixels[x][y];
            if (isBlue(pixel.rgb))
            {
               currentImage.setRGB((int)pixel.pt.x, (int)pixel.pt.y, Color.GREEN.getRGB());
            }
         }
      }
   }


   /**
    * Print cell coordinates.
    */
   private void printCellCoordinates()
   {
      if (currentImage != null)
      {
         boolean[][] markers = new boolean[imageWidth][imageHeight];
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               if (isGreen(currentImage.getRGB(x, y)))
               {
                  markers[x][y] = true;
               }
               else
               {
                  markers[x][y] = false;
               }
            }
         }
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               if (markers[x][y])
               {
                  for (int x2 = x - 1; x2 <= x + 1; x2++)
                  {
                     for (int y2 = y - 1; y2 <= y + 1; y2++)
                     {
                        if ((x2 >= 0) && (x2 < imageWidth) && (y2 >= 0) && (y2 < imageHeight))
                        {
                           if ((x != x2) || (y != y2))
                           {
                              if (markers[x2][y2])
                              {
                                 markers[x][y] = false;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         System.out.println("Cell coordinates:");
         for (int x = 0; x < imageWidth; x++)
         {
            for (int y = 0; y < imageHeight; y++)
            {
               if (markers[x][y])
               {
                  System.out.println(x + "," + y);
               }
            }
         }
      }
   }


   /**
    * Abort run.
    */
   private void abortRun()
   {
      while (running)
      {
         abort = true;
         try
         {
            Thread.sleep(ABORT_DELAY);
         }
         catch (InterruptedException e) {}
      }
      abort = false;
   }


   /**
    * Draw cells.
    */
   public void drawCells()
   {
      while (true)
      {
         synchronized (lock)
         {
            if (drawImage != null)
            {
               OSG.drawImage(drawImage, 0, 0, imageWidth, imageHeight, null);
               repaint();
            }
         }

         try
         {
            Thread.sleep(DISPLAY_DELAY);
         }
         catch (InterruptedException e) {}
      }
   }


   /**
    * Resize content.
    */
   private void resizeContent(int width, int height)
   {
      window.setSize(new Dimension(width, height));
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

      scrollFrame.setPreferredSize(new Dimension(
                                      Math.min(width, (int)((float)screenSize.width * 0.9f)),
                                      Math.min(height, (int)((float)screenSize.height * 0.9f))));
      setPreferredSize(new Dimension(width, height));
      OSC = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      OSG = OSC.createGraphics();
      window.pack();
   }


   /**
    * The paintComponent() copies the BufferedImage to the screen.
    */
   protected void paintComponent(Graphics g)
   {
      g.drawImage(OSC, 0, 0, null);
   }
}
