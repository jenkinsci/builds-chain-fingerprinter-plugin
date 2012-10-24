package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.Extension;
import hudson.model.*;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.UUID;

/**
 * Created with IntelliJ IDEA.
 * User: psamoshkin
 * Date: 10/19/12
 * Time: 12:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class AutomaticFingerprintJobProperty extends JobProperty<AbstractProject<?, ?>> {

    private Boolean isPerBuildsChainEnabled;
    private Boolean isPerJobsChainEnabled;

    @DataBoundConstructor
    public AutomaticFingerprintJobProperty(Boolean isPerJobsChainEnabled, Boolean isPerBuildsChainEnabled) {
        this.isPerJobsChainEnabled = isPerJobsChainEnabled;
        this.isPerBuildsChainEnabled = isPerBuildsChainEnabled;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if(isPerJobsChainEnabled) {
            build.addAction(new JobsDependencyFingerprinter(build));
        }
        if(isPerBuildsChainEnabled){
            build.addAction(new BuildsDependencyFingerprinter(build));
        }
        return true;
    }

    public Boolean getIsPerJobsChainEnabled(){
        return isPerJobsChainEnabled;
    }

    public Boolean getIsPerBuildsChainEnabled(){
        return isPerBuildsChainEnabled;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {

        public DescriptorImpl() {
            super(AutomaticFingerprintJobProperty.class);
            load();
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String value = req.getParameter("chainfingerprinting");

            if (value== null || !value.equals("on")) {
                return null;
            }

            String isPerBuildsChainEnabled = req.getParameter("isPerBuildsChainEnabled");
            String isPerJobsChainEnabled = req.getParameter("isPerJobsChainEnabled");
            boolean isPerJobsEnabled = isPerJobsChainEnabled != null && isPerJobsChainEnabled.equals("on");
            boolean isPerBuildsEnabled = isPerBuildsChainEnabled != null && isPerBuildsChainEnabled.equals("on");
            if(!isPerJobsEnabled && !isPerBuildsEnabled){
                return null;
            }
            return new AutomaticFingerprintJobProperty(isPerJobsEnabled, isPerBuildsEnabled);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return "[Downstream build view] - Automatic fingerprinting";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }
    }

}
