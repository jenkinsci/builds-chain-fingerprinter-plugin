package org.jenkinsci.plugins.buildschainfingerprinter;

/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio, Tom Huybrechts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link hudson.model.ParameterValue} created from {@link org.jenkinsci.plugins.buildschainfingerprinter.UpstreamDependencyParameterDefinition}.
 */
public class UpstreamDependencyParameterValue extends ParameterValue {
    @Exported(visibility=4)
    public final String value;

    @DataBoundConstructor
    public UpstreamDependencyParameterValue(String name, String value) {
        this(name, value, null);
    }

    public UpstreamDependencyParameterValue(String name, String value, String description) {
        super(name, description);
        this.value = value;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
        env.put(name,value);
        env.put(name.toUpperCase(Locale.ENGLISH),value); // backward compatibility pre 1.345
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return UpstreamDependencyParameterValue.this.name.equals(name) ? value : null;
            }
        };
    }

    @Override
    public BuildWrapper createBuildWrapper(AbstractBuild<?, ?> build) {
        CauseAction causes = build.getAction(CauseAction.class);
        if(causes != null && value != null){
            for (Cause c : causes.getCauses()){
                if( c instanceof Cause.UserCause || c instanceof Cause.UserIdCause){
                    return new BuildWrapper() {
                        @Override
                        public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                            Object dependencyFingerprint = Jenkins.getInstance().getFingerprint(Util.getDigestOf(value));
                            if(dependencyFingerprint instanceof Fingerprint){
                                for(Map.Entry<String,Fingerprint.RangeSet> entry : ((Fingerprint) dependencyFingerprint).getUsages().entrySet()){
                                    AbstractProject proj = (AbstractProject)Jenkins.getInstance().getItemByFullName(entry.getKey());
                                    if(proj != null){
                                        List<AbstractBuild> builds = proj.getBuilds(entry.getValue());
                                        for (AbstractBuild b : builds){
                                            BuildsDependencyFingerprinter bdf = b.getAction(BuildsDependencyFingerprinter.class);
                                            if(bdf != null){
                                                build.addAction(bdf);
                                                return new Environment() {};
                                            }
                                        }
                                    }
                                }
                            }
                            return new Environment() {};
                        }
                    };
                }
            }
        }
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        UpstreamDependencyParameterValue other = (UpstreamDependencyParameterValue) obj;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "(UpstreamDependencyParameterValue) " + getName() + "='" + value + "'";
    }
}
