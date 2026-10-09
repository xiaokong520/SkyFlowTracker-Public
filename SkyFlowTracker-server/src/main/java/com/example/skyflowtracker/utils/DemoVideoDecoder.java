package com.example.skyflowtracker.utils;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class DemoVideoDecoder {
    private final InputStream videoStream;
    private final Consumer<byte[]> onFrame;
    private final Runnable onComplete;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private static final int MAX_WIDTH = 960;
    private static final float JPEG_QUALITY = 0.75f;

    public DemoVideoDecoder(InputStream videoStream, Consumer<byte[]> onFrame, Runnable onComplete) {
        this.videoStream = videoStream;
        this.onFrame = onFrame;
        this.onComplete = onComplete;
    }

    public void start() {
        if (running.get()) return;
        running.set(true);
        thread = new Thread(this::decode, "demo-video-decoder");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void decode() {
        FFmpegFrameGrabber grabber = null;
        Java2DFrameConverter converter = new Java2DFrameConverter();
        try {
            grabber = new FFmpegFrameGrabber(videoStream);
            grabber.start();

            double frameRate = grabber.getFrameRate();
            if (frameRate <= 0) frameRate = 25.0;
            long intervalMs = (long) (1000.0 / frameRate);

            log.info("演示视频解码开始: {}x{}, fps={}", grabber.getImageWidth(), grabber.getImageHeight(), frameRate);

            Frame frame;
            while (running.get() && (frame = grabber.grabImage()) != null) {
                BufferedImage img = converter.convert(frame);
                if (img == null) continue;

                byte[] jpeg = encodeToJpeg(img);
                if (jpeg != null && running.get()) {
                    onFrame.accept(jpeg);
                }
                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            log.info("演示视频解码被中断");
        } catch (Exception e) {
            log.error("演示视频解码失败: {}", e.getMessage(), e);
        } finally {
            if (grabber != null) {
                try { grabber.stop(); } catch (Exception ignored) {}
                try { grabber.release(); } catch (Exception ignored) {}
            }
            running.set(false);
            onComplete.run();
        }
    }

    private byte[] encodeToJpeg(BufferedImage img) {
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            if (w > MAX_WIDTH) {
                double scale = (double) MAX_WIDTH / w;
                int newW = MAX_WIDTH;
                int newH = (int) (h * scale);
                BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, newW, newH, null);
                g.dispose();
                img = scaled;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            ImageOutputStream ios = ImageIO.createImageOutputStream(out);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose();
            ios.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("JPEG编码失败: {}", e.getMessage());
            return null;
        }
    }
}