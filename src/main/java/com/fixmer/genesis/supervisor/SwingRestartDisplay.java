package com.fixmer.genesis.supervisor;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Borderless, non-activating handover surface: no taskbar entry or fixed-size launcher dialog. */
final class SwingRestartDisplay implements RestartPresentation.Display {
    private final boolean reducedMotion;
    private final Path game;
    private final RestartCover.Publisher cover;
    private final AtomicBoolean closed=new AtomicBoolean();
    private JWindow window;
    private Timer timer;
    private boolean active,hidden;
    private long begin,readyAt,lastPulse;
    private String stage="Подготовка перезапуска";
    private RestartSurface surface;
    private BufferedImage raster;
    private PixelText font;
    SwingRestartDisplay(Path game,boolean reducedMotion){
        if(GraphicsEnvironment.isHeadless())throw new HeadlessException();this.game=game;this.reducedMotion=reducedMotion;cover=new RestartCover.Publisher(RestartSurface.path(game));
    }
    @Override public void begin(String phase){run(()->{
        active=true;hidden=false;surface=RestartSurface.load(RestartSurface.path(game));begin=surface==null?System.currentTimeMillis():surface.epoch();readyAt=0;stage=phase;
        if(window==null){
            window=new JWindow();window.setFocusableWindowState(false);window.setAutoRequestFocus(false);window.setBackground(Color.BLACK);
            window.addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){if(SwingUtilities.isRightMouseButton(e)||e.getX()>=window.getWidth()-24&&e.getY()<24)hide();}});
            window.setContentPane(new JPanel(){@Override protected void paintComponent(Graphics graphics){
                if(raster==null)return;Graphics2D image=raster.createGraphics();long elapsed=Math.max(0,System.currentTimeMillis()-begin);
                try{RestartCorePainter.paint(image,raster.getWidth(),raster.getHeight(),elapsed/1000.0,stage,elapsed,reducedMotion,font);}finally{image.dispose();}
                Graphics2D g=(Graphics2D)graphics.create();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(raster,0,0,getWidth(),getHeight(),null);
                    // A small pointer-only escape hatch remains available if a loader stops responding.
                    g.setColor(new Color(140,145,160));g.drawLine(getWidth()-15,7,getWidth()-8,14);g.drawLine(getWidth()-8,7,getWidth()-15,14);
                }finally{g.dispose();}
            }});
            window.getContentPane().addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){if(SwingUtilities.isRightMouseButton(e)||e.getX()>=window.getWidth()-24&&e.getY()<24)hide();}});
            timer=new Timer(reducedMotion?250:33,e->{if(readyAt!=0&&System.nanoTime()>=readyAt||System.currentTimeMillis()-begin>300000){active=false;hide();}else {pulse();window.repaint();}});
        }
        GraphicsConfiguration target=selectMonitor(surface);
        Rectangle bounds=surface==null||surface.fullscreen()?target.getBounds():windowBounds(surface,target);
        window.setBounds(bounds);
        Dimension rasterSize=rasterSize(surface,bounds);
        raster=new BufferedImage(rasterSize.width,rasterSize.height,BufferedImage.TYPE_INT_RGB);font=PixelText.load(game);
        try{window.setAlwaysOnTop(true);}catch(RuntimeException ignored) { }
        window.setVisible(true);cover.show(surface);lastPulse=0;pulse();timer.start();
    });}
    /** Bound the backing bitmap to 16 MiB; ordinary GUI sizes retain their exact dimensions. */
    static Dimension rasterSize(RestartSurface surface,Rectangle bounds){
        int width=surface==null?Math.max(320,bounds.width/3):surface.guiWidth();
        int height=surface==null?Math.max(180,bounds.height/3):surface.guiHeight();
        final int maxPixels=4*1024*1024;
        long pixels=(long)width*height;
        if(pixels>maxPixels){
            double scale=Math.sqrt(maxPixels/(double)pixels);
            width=Math.max(1,(int)Math.floor(width*scale));height=Math.max(1,(int)Math.floor(height*scale));
            height=Math.min(height,Math.max(1,maxPixels/width));
        }
        return new Dimension(width,height);
    }
    static GraphicsConfiguration selectMonitor(RestartSurface surface){
        GraphicsConfiguration best=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        if(surface==null)return best;long area=-1;
        for(GraphicsDevice device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()){
            GraphicsConfiguration gc=device.getDefaultConfiguration();Rectangle r=windowBounds(surface,gc).intersection(gc.getBounds());long a=r.isEmpty()?0:(long)r.width*r.height;
            if(a>area){area=a;best=gc;}
        }
        return best;
    }
    static Rectangle windowBounds(RestartSurface s,GraphicsConfiguration gc){
        double sx=gc.getDefaultTransform().getScaleX(),sy=gc.getDefaultTransform().getScaleY();Rectangle monitor=gc.getBounds();
        return new Rectangle(monitor.x+(int)Math.round((s.x()-monitor.x*sx)/sx),monitor.y+(int)Math.round((s.y()-monitor.y*sy)/sy),Math.max(64,(int)Math.round(s.width()/sx)),Math.max(64,(int)Math.round(s.height()/sy)));
    }
    private void pulse(){
        long now=System.currentTimeMillis();if(!active||hidden||surface==null||now-lastPulse<400)return;lastPulse=now;
        try{if(window.isVisible()){window.setAlwaysOnTop(true);window.toFront();}}catch(RuntimeException ignored){}
    }
    private void hide(){hidden=true;if(window!=null){window.setVisible(false);try{window.setAlwaysOnTop(false);}catch(RuntimeException ignored){}}if(timer!=null)timer.stop();cover.hide();}
    @Override public void phase(String phase){run(()->{if(active){stage=phase;if(!hidden)window.repaint();}});}
    @Override public void ready(){run(()->{if(active){stage="Готово";readyAt=System.nanoTime();if(hidden){active=false;if(timer!=null)timer.stop();}}});}
    @Override public void close(){if(closed.compareAndSet(false,true)){cover.close();SwingUtilities.invokeLater(()->{active=false;hide();if(window!=null){window.dispose();window=null;}raster=null;});}}
    private void run(Runnable action){if(closed.get())return;SwingUtilities.invokeLater(()->{if(!closed.get())try{action.run();}catch(RuntimeException|LinkageError e){close();}});}
}
