package Homework_1;

public class Integrator {

    @FunctionalInterface
    public interface Function {
        double apply(double x);
    }

    @FunctionalInterface
    public interface Sink {
        void accept(double value);
    }

    public static double integrate(Function f, double a, double b, int n) {
        if (n <= 0) throw new IllegalArgumentException("n должно быть > 0");

        double h = (b - a) / n;
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            double x = a + i * h;
            sum += f.apply(x);
        }

        return sum * h;
    }

    public static void integrate(Function f, double a, double b, int n, Sink sink) {
        if (n <= 0) throw new IllegalArgumentException("n должно быть > 0");
        double h = (b - a) / n;
        for (int i = 0; i < n; i++) {
            sink.accept(f.apply(a + i * h) * h);
        }
    }
}