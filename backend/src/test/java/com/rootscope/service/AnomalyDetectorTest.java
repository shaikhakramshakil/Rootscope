package com.rootscope.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

  @Test
  void flatErrorRateIsNotAnomalous() {
    for (int i = 0; i < 20; i++) {
      var r = AnomalyDetector.checkValue("error_rate", 0.01, 0.01, 0.002, 20);
      assertFalse(r.anomalous());
    }
  }

  @Test
  void errorSpikeIsAnomalousWithHighSeverity() {
    var r = AnomalyDetector.checkValue("error_rate", 0.08, 0.01, 0.002, 20);
    assertTrue(r.anomalous());
    assertTrue(r.severity() > 0.5);
  }

  @Test
  void trafficDropIsAnomalous() {
    var r = AnomalyDetector.checkValue("request_rate", 40.0, 100.0, 5.0, 20);
    assertTrue(r.anomalous());
  }

  @Test
  void warmingUpNeverFires() {
    var r = AnomalyDetector.checkValue("error_rate", 0.9, 0.0, 0.0, 3);
    assertFalse(r.anomalous());
  }
}
