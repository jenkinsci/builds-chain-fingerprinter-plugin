package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.Util;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import hudson.tasks.Fingerprinter;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

@ExportedBean
public class AutomaticFingerprintAction implements RunAction {
    private static final Logger LOG = Logger.getLogger(AutomaticFingerprintAction.class.getName());

    private String token;
    private String fileName;
    private String md5sum;

    private transient Fingerprint fingerprint;

    private HashMap<String, String> fingerprintActionRecords;

    public AutomaticFingerprintAction() {
    }

    public AutomaticFingerprintAction(String token, AbstractBuild build) {
        this.token = token;
        this.fileName = "Auto fingerprinting token (" + this.token + ")";
        CreateFingerprints(build);
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
    }

    public void onBuildComplete() {

    }

    public void PerformFingerprinting(AbstractBuild build) {
        try {
            if(!isAlreadyFingerprinted(build, getFingerprint())){
                getFingerprint().add(build);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        AddFingerprintAction(build);
    }

    private void AddFingerprintAction(AbstractBuild build) {
        Fingerprinter.FingerprintAction fingerprintAction = build.getAction(Fingerprinter.FingerprintAction.class);
        if(fingerprintAction == null){
            build.addAction(new Fingerprinter.FingerprintAction(build, fingerprintActionRecords));
        } else {
            fingerprintAction.add(fingerprintActionRecords);
        }
    }

    private boolean isAlreadyFingerprinted(AbstractBuild build, Fingerprint f) {
        return f.getRangeSet(build.getProject()).includes(build.getNumber());
    }

    public void CreateFingerprints(AbstractBuild build) {
        FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
        fingerprintActionRecords = new HashMap<String, String>();
        try {
            md5sum = Util.getDigestOf(token);
            fingerprint = map.getOrCreate(build , fileName, md5sum);
            getFingerprint().add(build);
            fingerprintActionRecords.put(fileName, md5sum);

            AddFingerprintAction(build);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Fingerprint getFingerprint() {
        if(fingerprint == null){
            try {
                fingerprint = Jenkins.getInstance()._getFingerprint(md5sum);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fingerprint;
    }
}
