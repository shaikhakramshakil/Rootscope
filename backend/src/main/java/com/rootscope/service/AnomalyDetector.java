package com.rootscope.service;

import org.springframework.stereotype.Service;

/**
 * Threshold + z-score anomaly detector for the PRD metric list.
 *
 * <p>Keeps a bounded rolling window per (service, metric) in memory; the
 * production profile persists these to the metrics DB (Prometheus) — the
 * math is identical. Severity is always 0..1: {@code min(1, max(z/6, jumpRatio))}.
 */
@Service
public class AnomalyDetector {

  private static final int WINDOW = 50;
  private final java.util.concurrent.ConcurrentHashMap<String, Window> windows =
      new java.util.concurrent.ConcurrentHashMap<>();

  public record Reading(boolean anomalous, double severity, String reason) {}

  public Reading observe(String service, String metric, double value) {
    Window w = windows.computeIfAbsent(service + "|" + metric, k -> new Window());
    Reading r = w.check(metric, value);
    w.add(value);
    return r;
  }

  /** Pure function used by ingestion and unit tests. */
  public static Reading checkValue(String metric, double value, double mean, double stddev, int n) {
    if (n < 5) return new Reading(false, 0.0, "warming up");
    double z = stddev < 1e-9 ? 0.0 : (value - mean) / stddev;
    return switch (metric) {
      case "error_rate" -> {
        double jump = mean < 1e-9 ? (value > 0.01 ? 1.0 : 0.0) : (value - mean) / mean;
        boolean flag = (z > 3.0 && value - mean > 0.005) || (jump > 0.5 && value - mean > 0.01);
        yield new Reading(flag, flag ? clamp(Math.max(z / 6.0, jump / 3.0)) : 0.0,
            "error_rate mean=" + mean + " value=" + value);
      }
      case "latency_ms", "db_latency_ms" -> {
        double jump = mean < 1e-9 ? 0.0 : (value - mean) / mean;
        boolean flag = z > 3.0 || (jump > 0.5 && value - mean > 50.0);
        yield new Reading(flag, flag ? clamp(Math.max(z / 6.0, jump / 3.0)) : 0.0,
            metric + " mean=" + mean + " value=" + value);
      }
      case "request_rate" -> {
        double drop = mean < 1e-9 ? 0.0 : (mean - value) / mean;
        boolean flag = drop > 0.3 && z < -2.0;
        yield new Reading(flag, flag ? clamp(Math.max(-z / 6.0, drop)) : 0.0, "traffic drop");
      }
      case "cpu", "memory" -> {
        boolean flag = value > 85.0 || (z > 3.0 && value - mean > 10.0);
        yield new Reading(flag, flag ? clamp(Math.max(z / 6.0, (value - 70.0) / 30.0)) : 0.0, metric + " high");
      }
      default -> {
        boolean flag = Math.abs(z) > 3.0;
        yield new Reading(flag, flag ? clamp(Math.abs(z) / 6.0) : 0.0, "generic z=" + z);
      }
    };
  }

  private static double clamp(double v) {
    return Math.min(1.0, Math.max(0.0, v));
  }

  private static class Window {
    private final double[] buf = new double[WINDOW];
    private int count;
    private int pos;

    synchronized Reading check(String metric, double value) {
      if (count < 5) return new Reading(false, 0.0, "warming up");
      double mean = mean();
      double std = stddev(mean);
      return checkValue(metric, value, mean, std, count);
    }

    synchronized void add(double v) {
      buf[pos] = v;
      pos = (pos + 1) % WINDOW;
      if (count < WINDOW) count++;
    }

    private double mean() {
      double s = 0;
      for (int i = 0; i < count; i++) s += buf[i];
      return s / count;
    }

    private double stddev(double mean) {
      double s = 0;
      for (int i = 0; i < count; i++) {
        double d = buf[i] - mean;
        s += d * d;
      }
      return Math.sqrt(s / count);
    }
  }
}
