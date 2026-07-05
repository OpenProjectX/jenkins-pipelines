package org.pipeline.deploy

interface Deployer {
    void deploy(Map environment, Map config)
    void rollback(Map environment, Map config)
}
