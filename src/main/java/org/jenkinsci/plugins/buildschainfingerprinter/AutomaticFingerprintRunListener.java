package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;

/**
 * This listener adds {@link AutomaticFingerprintAction} to every new build.
 *
 * @author advantiss@gmail.com
 */
@SuppressWarnings("unchecked")
@Extension
public class AutomaticFingerprintRunListener extends RunListener<AbstractBuild> {

    /**
     * {@link hudson.Extension} needs parameterless constructor.
     */
    public AutomaticFingerprintRunListener() {
        super(AbstractBuild.class);
    }

    @Override
    public void onStarted(AbstractBuild r, TaskListener listener) {
        CauseAction ca = r.getAction(CauseAction.class);
        if (ca == null || ca.getCauses() ==null) {
            return;
        }
        for (Cause c : ca.getCauses()){
            if( c instanceof Cause.UpstreamCause){
                Cause.UpstreamCause upcause = (Cause.UpstreamCause)c;
                String upProjectName = upcause.getUpstreamProject();
                int buildNumber = upcause.getUpstreamBuild();
                AbstractProject project = Hudson.getInstance().getItemByFullName(upProjectName, AbstractProject.class);
                AbstractBuild upBuild = (AbstractBuild)project.getBuildByNumber(buildNumber);
                for(AutomaticFingerprintAction action : upBuild.getActions(AutomaticFingerprintAction.class)){
                    r.addAction(action);
                    action.PerformFingerprinting(r);
                }
            }
        }
    }
}
