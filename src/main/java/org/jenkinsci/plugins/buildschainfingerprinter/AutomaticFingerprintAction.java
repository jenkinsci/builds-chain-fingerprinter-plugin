package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.Util;
import hudson.model.*;
import hudson.tasks.Fingerprinter;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class AutomaticFingerprintAction implements RunAction {
    private static final Logger LOG = Logger.getLogger(AutomaticFingerprintAction.class.getName());

    private String fingerprintToken = UUID.randomUUID().toString();
    private String dummyFilename = "Builds chain token (" + fingerprintToken + ")";

    public AutomaticFingerprintAction() {
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public String getIconFileName() {
        return null;
    }

    public void onLoad() {

    }

    public void onAttached(Run r) {
        CopyAutomaticFingerprintsFromUpstreamBuilds(r);
    }

    public void onBuildComplete() {

    }

    public void CopyAutomaticFingerprintsFromUpstreamBuilds(Run r) {
        CauseAction ca = r.getAction(CauseAction.class);
        if (ca == null || ca.getCauses() ==null) {
            return;
        }
        for (Cause c : ca.getCauses()){
            if( c instanceof Cause.UpstreamCause){
                Cause.UpstreamCause upcause = (Cause.UpstreamCause)c;
                AbstractProject project = Hudson.getInstance().getItemByFullName(upcause.getUpstreamProject(), AbstractProject.class);
                AbstractBuild upBuild = (AbstractBuild)project.getBuildByNumber(upcause.getUpstreamBuild());
                CopyFingerprintActionFromUpstreamBuild((AbstractBuild)r, upBuild);
            }
        }
    }

    public void CopyFingerprintActionFromUpstreamBuild(AbstractBuild build, AbstractBuild upstreamBuild){
        for (Fingerprinter.FingerprintAction action : upstreamBuild.getActions(Fingerprinter.FingerprintAction.class)){
            Map<String, Fingerprint> fingerprints = action.getFingerprints();
            if(fingerprints.containsKey(dummyFilename)){
                Fingerprint f = fingerprints.get(dummyFilename);
                if(!isAlreadyFingerprinted(build, f)){
                    String md5sum = f.getHashString();
                    doFingerprint(build, f, md5sum);
                }
            }
        }
    }

    private boolean isAlreadyFingerprinted(AbstractBuild build, Fingerprint f) {
        return f.getRangeSet(build.getProject().getFullName()).includes(build.getNumber());
    }

    public void AddNewFingerprintAction(AbstractBuild build) {
        FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
        String md5sum = Util.getDigestOf(UUID.randomUUID().toString());
        try {
            map.getOrCreate(build , dummyFilename, md5sum);
            HashMap<String, String> record = new HashMap<String, String>();
            record.put(dummyFilename, md5sum);
            build.addAction(new Fingerprinter.FingerprintAction(build, record));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doFingerprint(AbstractBuild build, Fingerprint f, String md5sum) {
        HashMap<String, String> record = new HashMap<String, String>();
        record.put(dummyFilename, md5sum);
        Fingerprinter.FingerprintAction act = build.getAction(Fingerprinter.FingerprintAction.class);
        if(act == null){
            act = new Fingerprinter.FingerprintAction(build, record);
            build.addAction(act);
        }

        try {
            f.add(build);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
