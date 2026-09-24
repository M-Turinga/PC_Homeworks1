package Homework_1;

import static Homework_1.LaunchConfig.*;

public class MeasureMon implements Runnable {

    private final Integrator.Function f;

    public MeasureMon(Integrator.Function f) {
        this.f = f;
    }

    private static class Acc {
        private double acc = 0.0;

        synchronized void add(double x){
            acc += x;
        }

        synchronized double get(){
            return acc;
        }

    }
    @Override
    public void run() {
        try {

            Thread[] threads = new Thread[THREADS];
            Acc acc = new Acc();

            double width = (B - A)/THREADS;

            var pStart = System.nanoTime();

            for (int i = 0; i < THREADS; i++) {
                double a = A + i*width;
                double b  = a + width;
                threads[i] = taskMonitorThread(a, b, acc);
            }

            for (int i = 0; i < THREADS; i++)
                threads[i].start();

            for (int i = 0; i < THREADS; i++)
                threads[i].join();

            double pResult = acc.get();

            var pFinish = System.nanoTime();

            System.out.println("Monitor result");
            System.out.println(pResult);
            System.out.println("Monitor time (ms)");
            System.out.println((double) (pFinish - pStart) / 1000000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public Thread taskMonitorThread(double a, double b,  Acc acc){
        return new Thread(()-> {
            Integrator.integrate(f, a, b, N_PER_THREAD, acc::add);
        });
    }
}
