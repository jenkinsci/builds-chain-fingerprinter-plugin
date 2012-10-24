package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.model.AbstractBuild;

import java.util.UUID;

/**
 * Created with IntelliJ IDEA.
 * User: psamoshkin
 * Date: 10/23/12
 * Time: 4:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class BuildsDependencyFingerprinter extends AutomaticFingerprintAction {
    public BuildsDependencyFingerprinter(AbstractBuild build) {
        super(UUID.randomUUID().toString(), build);
    }

    public BuildsDependencyFingerprinter() {
    }
}
