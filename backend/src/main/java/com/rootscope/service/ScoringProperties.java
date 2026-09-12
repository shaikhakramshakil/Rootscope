package com.rootscope.service;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rootscope.scoring")
public class ScoringProperties {
  /** Weight vector; must sum to ~1 (validated at startup). */
  @PositiveOrZero private double wTemporal = 0.25;
  @PositiveOrZero private double wDependency = 0.20;
  @PositiveOrZero private double wAnomaly = 0.20;
  @PositiveOrZero private double wError = 0.20;
  @PositiveOrZero private double wDeployment = 0.15;
  @Positive private double temporalTauMinutes = 15.0;
  @Positive private long lookbackMinutes = 60;

  public boolean isWeightsValid() {
    double sum = wTemporal + wDependency + wAnomaly + wError + wDeployment;
    return Math.abs(sum - 1.0) < 1e-6;
  }

  public double getWTemporal() { return wTemporal; }
  public void setWTemporal(double v) { this.wTemporal = v; }
  public double getWDependency() { return wDependency; }
  public void setWDependency(double v) { this.wDependency = v; }
  public double getWAnomaly() { return wAnomaly; }
  public void setWAnomaly(double v) { this.wAnomaly = v; }
  public double getWError() { return wError; }
  public void setWError(double v) { this.wError = v; }
  public double getWDeployment() { return wDeployment; }
  public void setWDeployment(double v) { this.wDeployment = v; }
  public double getTemporalTauMinutes() { return temporalTauMinutes; }
  public void setTemporalTauMinutes(double v) { this.temporalTauMinutes = v; }
  public long getLookbackMinutes() { return lookbackMinutes; }
  public void setLookbackMinutes(long v) { this.lookbackMinutes = v; }
}
