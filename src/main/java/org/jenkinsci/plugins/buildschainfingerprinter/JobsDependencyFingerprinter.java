package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.model.AbstractBuild;

/**
 * Created with IntelliJ IDEA.
 * User: psamoshkin
 * Date: 10/23/12
 * Time: 4:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class JobsDependencyFingerprinter extends AutomaticFingerprintAction {
    public JobsDependencyFingerprinter() {
    }

    public JobsDependencyFingerprinter(AbstractBuild build) {
        super(build.getProject().getFullName(), build);
    }
}
