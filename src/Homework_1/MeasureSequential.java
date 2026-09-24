package Homework_1;

import static Homework_1.LaunchConfig.*;

public class MeasureSequential implements Runnable{

    private final Integrator.Function f;

    public MeasureSequential(Integrator.Function f) {
        this.f = f;
    }

    @Override
    public void run() {
        var start = System.nanoTime();

        double result = Integrator.integrate(f, A, B, N);

        var finish = System.nanoTime();
        System.out.println("Sequential result");
        System.out.println(result);
        System.out.println("Sequential time (ms)");
        System.out.println((double)(finish - start)/1_000_000);
    }
}
