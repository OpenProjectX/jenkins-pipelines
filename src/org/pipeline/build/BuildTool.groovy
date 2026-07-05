package org.pipeline.build

interface BuildTool {
    void build(Map config)
    void test(Map config)
}
