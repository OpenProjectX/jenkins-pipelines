package org.pipeline.prgate

interface PrGate {
    void check(Map config)
    void notify(String result, Map config)
}
