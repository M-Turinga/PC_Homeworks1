package Homework_1;

import java.util.concurrent.atomic.DoubleAdder;

import static Homework_1.LaunchConfig.*;

public class MeasureAtomic implements Runnable{

    private final Integrator.Function f;

    public MeasureAtomic(Integrator.Function f) {
        this.f = f;
    }

    @Override
    public void run() {
        try {

            Thread[] threads = new Thread[THREADS];
            DoubleAdder results = new DoubleAdder();

            double width = (B - A)/THREADS;

            var pStart = System.nanoTime();

            for (int i = 0; i < THREADS; i++) {
                double a = A + i*width;
                double b  = a + width;
                threads[i] = taskAtomicThread(a, b, results);
            }

            for (int i = 0; i < THREADS; i++)
                threads[i].start();

            for (int i = 0; i < THREADS; i++)
                threads[i].join();

            var pResult = results.sum();
            var pFinish = System.nanoTime();

            System.out.println("Atomic result");
            System.out.println(pResult);
            System.out.println("Atomic time (ms)");
            System.out.println((double)(pFinish - pStart)/1000000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private Thread taskAtomicThread(double a, double b, DoubleAdder results){
        return new Thread(()-> {
            Integrator.integrate(f, a, b, N_PER_THREAD, results::add);
        });
    }
}
