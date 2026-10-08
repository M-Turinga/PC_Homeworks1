package Homework_3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class Mandelbrot {

    static final int WIDTH  = 1000;
    static final int HEIGHT = 700;
    static final int MAX_ITER = 500;
    static final int THREADS = 8;

    static final double X_MIN = -2.5;
    static final double X_MAX =  1.0;
    static final double Y_MIN = -1.2;
    static final double Y_MAX =  1.2;

    static final int INSIDE_COLOR = 0x000000;

    static int mandelbrotIterations(double cx, double cy) {
        double zx = 0.0, zy = 0.0;
        int iter = 0;
        while (zx * zx + zy * zy <= 4.0 && iter < MAX_ITER) {
            double tmp = zx * zx - zy * zy + cx;
            zy = 2.0 * zx * zy + cy;
            zx = tmp;
            iter++;
        }
        return iter;
    }

    static int colorFor(int iter) {
        if (iter == MAX_ITER) return INSIDE_COLOR;
        float hue = (iter % 256) / 255f;
        return java.awt.Color.HSBtoRGB(hue, 0.8f, iter / (float) MAX_ITER * 1.5f + 0.2f)
                & 0xFFFFFF;
    }


    static void render(BufferedImage image, int threads) throws InterruptedException {
        AtomicInteger nextRow = new AtomicInteger(0);
        Thread[] workers = new Thread[threads];

        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                int y;
                while ((y = nextRow.getAndIncrement()) < HEIGHT) {
                    double cy = Y_MIN + (Y_MAX - Y_MIN) * y / HEIGHT;
                    for (int x = 0; x < WIDTH; x++) {
                        double cx = X_MIN + (X_MAX - X_MIN) * x / WIDTH;
                        image.setRGB(x, y, colorFor(mandelbrotIterations(cx, cy)));
                    }
                }
            });
            workers[t].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }
    }


    static void checkKnownPoints() {
        // c = 0 — принадлежит: последовательность остаётся нулём
        if (mandelbrotIterations(0.0, 0.0) != MAX_ITER)
            throw new AssertionError("c=0 must be inside the set");
        // c = -1 — принадлежит: 0, -1, 0, -1, ... ограничена
        if (mandelbrotIterations(-1.0, 0.0) != MAX_ITER)
            throw new AssertionError("c=-1 must be inside the set");
        // c = 2 — вылетает: 0, 2, 6, 42, ...
        if (mandelbrotIterations(2.0, 0.0) >= MAX_ITER)
            throw new AssertionError("c=2 must escape");
        // c = 1 — вылетает: 0, 1, 2, 5, 26, ...
        if (mandelbrotIterations(1.0, 0.0) >= MAX_ITER)
            throw new AssertionError("c=1 must escape");
    }

    static void checkImageNotEmpty(BufferedImage image) {
        int nonBlack = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != INSIDE_COLOR) nonBlack++;
            }
        }
        if (nonBlack == 0)
            throw new AssertionError("Nothing rendered");
        if (nonBlack == WIDTH * HEIGHT)
            throw new AssertionError("Image is entirely colored");
    }

    static void checkDeterminism(int threads) throws InterruptedException {
        BufferedImage a = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage b = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        render(a, 1);
        render(b, threads);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    throw new AssertionError(
                            "1 thread and " + threads + " threads disagree at ("
                                    + x + "," + y + ")");
                }
            }
        }
    }


    static void checkParallelSpeedup(int threads) throws InterruptedException {
        if (threads <= 1) return;

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        long t1 = System.nanoTime();
        render(img, 1);
        long single = System.nanoTime() - t1;

        long tN = System.nanoTime();
        render(img, threads);
        long multi = System.nanoTime() - tN;

        double speedup = (double) single / multi;
        System.out.printf("Single thread: %.1f ms, %d threads: %.1f ms, speedup: %.2fx%n",
                single / 1e6, threads, multi / 1e6, speedup);

        if (speedup < 1.5) {
            throw new AssertionError(
                    "Expected speedup >= 1.5x with " + threads
                            + " threads, got " + String.format("%.2f", speedup));
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        long start = System.nanoTime();
        render(image, THREADS);
        long end = System.nanoTime();

        checkKnownPoints();
        checkImageNotEmpty(image);
        checkDeterminism(THREADS);
        checkParallelSpeedup(THREADS);

        ImageIO.write(image, "png", new File("mandelbrot.png"));
        System.out.println("Rendered in " + (end - start) / 1_000_000 + " ms");
        System.out.println("OK: mandelbrot saved to mandelbrot.png");
    }
}