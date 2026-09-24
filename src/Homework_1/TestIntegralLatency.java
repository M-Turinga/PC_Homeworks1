package Homework_1;

import static Homework_1.LaunchConfig.*;

public class TestIntegralLatency {
    public static void main(String[] args) {

        System.out.println("x^2 на отрезке [0,1] (простая функция)");

        MeasureSequential sequentialEasy = new MeasureSequential(F1);
        MeasureParallel parallelEasy = new MeasureParallel(F1);
        MeasureAtomic atomicEasy = new MeasureAtomic(F1);
        MeasureMon monitorEasy = new MeasureMon(F1);

        sequentialEasy.run();
        parallelEasy.run();
        atomicEasy.run();
        monitorEasy.run();

        System.out.println("e^(-x^2) на отрезке [0,1] (сложная функция)");

        MeasureSequential sequentialDifficult = new MeasureSequential(F2);
        MeasureParallel parallelDifficult = new MeasureParallel(F2);
        MeasureAtomic atomicDifficult = new MeasureAtomic(F2);
        MeasureMon monitorDifficult = new MeasureMon(F2);

        sequentialDifficult.run();
        parallelDifficult.run();
        atomicDifficult.run();
        monitorDifficult.run();
    }
}
