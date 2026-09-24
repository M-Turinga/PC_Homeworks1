package Homework_1;

public class LaunchConfig {
    public static final Double A = 0.0;
    public static final Double B = 1.0;
    public static final int N = 1_000_000;
    public static final Integrator.Function F1 = x -> x * x;
    public static final Integrator.Function F2 = x -> Math.exp(-x * x);
    public static final int THREADS = 4;
    public static final int N_PER_THREAD = N/THREADS;
}
