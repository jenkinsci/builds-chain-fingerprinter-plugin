package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.Extension;
import hudson.model.*;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Created with IntelliJ IDEA.
 * User: psamoshkin
 * Date: 10/19/12
 * Time: 12:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class AutomaticFingerprintJobProperty extends JobProperty<AbstractProject<?, ?>> {

    private Boolean isAutomaticFingerprintingEnabled;

    @DataBoundConstructor
    public AutomaticFingerprintJobProperty(Boolean isAutomaticFingerprintingEnabled) {
        this.isAutomaticFingerprintingEnabled = isAutomaticFingerprintingEnabled;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if(getIsAutomaticFingerprintingEnabled()){
            AutomaticFingerprintAction automaticFingerprintAction = new AutomaticFingerprintAction();
            build.addAction(automaticFingerprintAction);
            automaticFingerprintAction.AddNewFingerprintAction(build);
        }
        return true;
    }

    public Boolean getIsAutomaticFingerprintingEnabled() {
        return isAutomaticFingerprintingEnabled;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {

        public DescriptorImpl() {
            super(AutomaticFingerprintJobProperty.class);
            load();
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String param = req.getParameter("isAutomaticFingerprintingEnabled");
            return param == null ? null : new AutomaticFingerprintJobProperty(param.equals("on"));
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
