package Homework_1;

import static Homework_1.LaunchConfig.*;

public class MeasureParallel implements Runnable{

    private final Integrator.Function f;

    public MeasureParallel(Integrator.Function f) {
        this.f = f;
    }

    @Override
    public void run() {
        try {

            Thread[] threads = new Thread[THREADS];
            double[] results = new double[THREADS];

            double width = (B - A)/THREADS;

            var pStart = System.nanoTime();

            for (int i = 0; i < THREADS; i++) {
                double a = A + i*width;
                double b  = a + width;
                threads[i] = taskThread(i, a, b, results);
            }

            for (int i = 0; i < THREADS; i++)
                threads[i].start();

            for (int i = 0; i < THREADS; i++)
                threads[i].join();

            double pResult = 0.0;
            for (int i = 0; i < THREADS; i++) {
                pResult += results[i];
            }
            var pFinish = System.nanoTime();

            System.out.println("Parallel result");
            System.out.println(pResult);
            System.out.println("Parallel time (ms)");
            System.out.println((double)(pFinish - pStart)/1000000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Thread taskThread(int idx, double a, double b,  double[] results){
        return new Thread(()-> {
            results[idx] = Integrator.integrate(f, a, b, N_PER_THREAD);
        });
    }
}
