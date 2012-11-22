package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.Extension;
import hudson.Plugin;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import hudson.scm.SCMRevisionState;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: psamoshkin
 * Date: 10/22/12
 * Time: 4:18 PM
 *
 * This class allows to resolve SCM changes in projects of whole workflow
 */
public class ScmDependencyChangesResolver extends InvisibleAction {
    private final AbstractBuild build;
    private HashSet<Fingerprint> fingerprintsSinceLastSuccess;
    private Map<User, Set<AbstractBuild<?,?>>> culprits;
    private HashMap<AbstractProject, HashSet<ChangeLogSet>> changesSinceLastSuccess;
    private StringBuilder res;
    private boolean resolved;
    private Object locker = new Object();

    public ScmDependencyChangesResolver(AbstractBuild build) {
        this.build = build;
        Resolve();
    }

    public void Resolve(){
        synchronized (locker){
            try{
                changesSinceLastSuccess =  _getScmChangesSinceLastSuccess();
            }
            finally {
                resolved = true;
            }
        }
    }

    public static ResultTrend GetTrend(AbstractBuild build){
        Result prevBuildResult = GetPreviousCompletedBuildResult(build);
        return GetResultTrend(build, prevBuildResult);
    }

    public static Result GetPreviousCompletedBuildResult(AbstractBuild build) {
        AbstractBuild lastBuild = GetPreviousCompletedBuild(build, null);
        return lastBuild == null ? Result.NOT_BUILT : lastBuild.getResult();
    }

    private static ResultTrend GetResultTrend(AbstractBuild build, Result prevResult) {
        Result result = build.getResult();

        if (result == Result.ABORTED) {
            return ResultTrend.ABORTED;
        } else if (result == Result.NOT_BUILT) {
            return ResultTrend.NOT_BUILT;
        }

        if (result == Result.SUCCESS) {
            if (isFix(result, prevResult)) {
                return ResultTrend.FIXED;
            } else {
                return ResultTrend.SUCCESS;
            }
        }

        if (result == Result.UNSTABLE) {
            if (prevResult == null) {
                return ResultTrend.UNSTABLE;
            }
            if (prevResult == Result.UNSTABLE) {
                return ResultTrend.STILL_UNSTABLE;
            } else if (prevResult == Result.FAILURE) {
                return ResultTrend.NOW_UNSTABLE;
            } else {
                return ResultTrend.UNSTABLE;
            }
        } else if (result == Result.FAILURE) {
            if (prevResult != null && prevResult == Result.FAILURE) {
                return ResultTrend.STILL_FAILING;
            } else {
                return ResultTrend.FAILURE;
            }
        }

        throw new IllegalArgumentException("Unknown result: '" + result + "' for build: " + build);
    }

    private static boolean isFix(Result result, Result prevResult) {
        if (result != Result.SUCCESS) {
            return false;
        }

        if (prevResult != null) {
            return prevResult.isWorseThan(Result.SUCCESS);
        }
        return false;
    }

    public AbstractBuild GetPreviousSuccesfullBuild(AbstractBuild build){
        return GetPreviousCompletedBuild(build, Result.SUCCESS);
    }

    public Set<User> GetCulprits(){
        return _getUpstreamBuildCulprits().keySet();
    }


    public String GetLastSuccessRevision(){
        AbstractBuild previousSuccessBuild = GetPreviousSuccesfullBuild(build);
        if(previousSuccessBuild == null){
            return null;
        }
        Fingerprint lastSuccessFingerprint = GetBuidsDependencyFingerprint(GetPreviousSuccesfullBuild(build));
        if(lastSuccessFingerprint == null){
            return null;
        }
        Hashtable<String,Fingerprint.RangeSet> usages = lastSuccessFingerprint.getUsages();

        for (Map.Entry<String, Fingerprint.RangeSet> entry : usages.entrySet()){
            AbstractProject project = (AbstractProject)Jenkins.getInstance().getItemByFullName(entry.getKey());
            if(IsScmConfiguredForProject(project)){
                for (Integer buildNumber : entry.getValue().listNumbers()){
                    AbstractBuild build = (AbstractBuild) project.getBuildByNumber(buildNumber);
                    if(build != null){
                        SCMRevisionState state = build.getAction(SCMRevisionState.class);
                        if(state != null){
                            if(Hudson.getInstance().getPlugin("mercurial") != null
                                    && state instanceof hudson.plugins.mercurial.MercurialTagAction){
                                return ((hudson.plugins.mercurial.MercurialTagAction)state).getId();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public HashMap<AbstractProject,String> GetLastSuccessRevisions(){
        AbstractBuild previousSuccessBuild = GetPreviousSuccesfullBuild(build);
        if(previousSuccessBuild == null){
            return null;
        }
        Fingerprint lastSuccessFingerprint = GetBuidsDependencyFingerprint(previousSuccessBuild);
        if(lastSuccessFingerprint == null){
            return null;
        }
        HashMap<AbstractProject, String> result = new HashMap<AbstractProject, String>();
        Hashtable<String,Fingerprint.RangeSet> usages = lastSuccessFingerprint.getUsages();

        for (Map.Entry<String, Fingerprint.RangeSet> entry : usages.entrySet()){
            AbstractProject project = (AbstractProject)Jenkins.getInstance().getItemByFullName(entry.getKey());
            if(IsScmConfiguredForProject(project)){
                for (Integer buildNumber : entry.getValue().listNumbers()){
                    AbstractBuild build = (AbstractBuild) project.getBuildByNumber(buildNumber);
                    if(build != null){
                        SCMRevisionState state = build.getAction(SCMRevisionState.class);
                        if(state != null){
                            if(Hudson.getInstance().getPlugin("mercurial") != null
                                    && state instanceof hudson.plugins.mercurial.MercurialTagAction){
                                result.put(project,((hudson.plugins.mercurial.MercurialTagAction)state).getId());
                                break;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static AbstractBuild GetPreviousCompletedBuild(AbstractBuild<?, ?> build, Result treshold){
        Fingerprint buildsDependencyFingerprint = GetBuidsDependencyFingerprint(build);
        if (buildsDependencyFingerprint != null) {
            String rootProjectName = buildsDependencyFingerprint.getOriginal().getName();
            AbstractBuild b = (AbstractBuild) build.getPreviousCompletedBuild();
            while (b != null) {
                if (b.getResult() != null) {
                    if (treshold == null || b.getResult().isBetterOrEqualTo(treshold)) {
                        buildsDependencyFingerprint = GetBuidsDependencyFingerprint(b);
                        if (buildsDependencyFingerprint != null && buildsDependencyFingerprint.getOriginal().getName().equals(rootProjectName)) {
                            return b;
                        }
                    }
                }
                b = (AbstractBuild) b.getPreviousCompletedBuild();
            }
        }
        return null;
    }

    public String toString(){
        if(!resolved){
            Resolve();
        }
        if(res == null){
            res = new StringBuilder();
            for(Map.Entry<AbstractProject, HashSet<ChangeLogSet>> entry : changesSinceLastSuccess.entrySet()){
                res.append("Project:" + entry.getKey().getName() + "\n\n");
                for(ChangeLogSet changeLogSet : entry.getValue()){
                    for(Object entry2 : changeLogSet){
                        ChangeLogSet.Entry changeSet = (ChangeLogSet.Entry)entry2;
                        res.append("  changeset id: " + changeSet.getCommitId() + "\n");
                        res.append("        author: " + changeSet.getAuthor() + "\n");
                        res.append("       message: " + changeSet.getMsg() + "\n\n");
        //                    res.append("       affected files: \n");
        //
        //                    for(ChangeLogSet.AffectedFile af : changeSet.getAffectedFiles()){
        //                        res.append("                 " + af.getEditType().getName() + ": "+ af.getPath() + "\n");
        //                    }
                    }
                }
            }
        }
        return res.toString();
    }

    private Map<User, Set<AbstractBuild<?,?>>> _getUpstreamBuildCulprits() {
        HashMap<User, Set<AbstractBuild<?,?>>> result = new HashMap<User, Set<AbstractBuild<?,?>>>();

        for(Map.Entry<AbstractProject, HashSet<ChangeLogSet>> upstreamProjectChanges : changesSinceLastSuccess.entrySet()){
            for(ChangeLogSet changeLogSet : upstreamProjectChanges.getValue()){
                for(Object changeSet : changeLogSet.getItems()){
                    User author = ((ChangeLogSet.Entry) changeSet).getAuthor();
                    if(!result.containsKey(author)){
                        result.put(author, new HashSet<AbstractBuild<?, ?>>());
                    }
                    result.get(author).add(changeLogSet.build);
                }
            }
        }
        return result;
    }

    public HashMap<AbstractProject, HashSet<ChangeLogSet>> GetScmChangesSinceLastSuccess() {
        return changesSinceLastSuccess;
    }

    private HashMap<AbstractProject, HashSet<ChangeLogSet>> _getScmChangesSinceLastSuccess() {
        fingerprintsSinceLastSuccess = GetSinceLastSuccesBuildsCausedByUpstreamJobs();
        return GetWorkflowProjectsChanges(fingerprintsSinceLastSuccess);
    }

    private HashSet<Fingerprint> GetSinceLastSuccesBuildsCausedByUpstreamJobs() {
        HashSet<Fingerprint> result = new HashSet<Fingerprint>();
        Fingerprint f = GetBuidsDependencyFingerprint(build);
        if(f!=null){
            AbstractBuild previousSuccessBuild = GetPreviousSuccesfullBuild(build);
            if(previousSuccessBuild == null){
                return result;
            }
            Fingerprint.Range sinceLastSuccesBuildsRange = new Fingerprint.Range(previousSuccessBuild.getNumber()+1, build.getNumber()+1);
            Fingerprint.BuildPtr rootBuild = f.getOriginal();
            AbstractProject rootJob = (AbstractProject)Jenkins.getInstance().getItemByFullName(rootBuild.getName());
            if(rootJob != null){
                for(Object b : rootJob.getBuilds()){
                    if(((AbstractBuild) b).getNumber() > rootBuild.getNumber()){
                        continue;
                    }
                    Fingerprint e = GetBuidsDependencyFingerprint((AbstractBuild) b);
                    if(e != null) {
                        Fingerprint.RangeSet buildsRange = e.getRangeSet(build.getProject());
                        if(!buildsRange.isEmpty()) {
                            if(sinceLastSuccesBuildsRange.includes(buildsRange.min())){
                                result.add(e);
                            } else if(buildsRange.isSmallerThan(previousSuccessBuild.getNumber())){
                                break;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

//    private int IsFingerprintInRangeSinceLastSuccess(Fingerprint fingerprint){
//        Fingerprint.RangeSet buildsRelatedToUpstream = fingerprint.getRangeSet(build.getProject());
//        int result = 0;
//        for(Integer buildId : buildsRelatedToUpstream.listNumbersReverse()){
//            if(buildId > build.getNumber()){//for elder builds than requested
//                result = 1;
//                continue;
//            }
//            Run buildByNumber = build.getProject().getBuildByNumber(buildId);
//            if(buildByNumber == null){//for deleted builds
//                result = 0;
//                continue;
//            }
//            Result buildResult = buildByNumber.getResult();
//            if(buildResult == null){//for uncompleted builds
//                result = 0;
//                continue;
//            } else if (buildResult.isBetterOrEqualTo(Result.SUCCESS)){
//                if(build.getNumber() == buildId){
//                    result = 0;
//                    break;
//                }else if (buildId < build.getNumber()){
//                    lastSuccessFingerprint = fingerprint;
//                    result = -1;
//                    break;
//                }
//            } else{
//                result = 0;
//                break;
//            }
//        }
//        return result;
//    }

    public static AbstractProject GetWorkflowRootProject(AbstractBuild build){
        Fingerprint buildsDependencyFingerprint = GetBuidsDependencyFingerprint(build);
        if(buildsDependencyFingerprint != null){
            Fingerprint.BuildPtr buildPtr = buildsDependencyFingerprint.getOriginal();
            return (AbstractProject)Jenkins.getInstance().getItemByFullName(buildPtr.getName());
        }
        return null;
    }

    private static Fingerprint GetBuidsDependencyFingerprint(AbstractBuild<?, ?> b) {
        AutomaticFingerprintAction action = b.getAction(BuildsDependencyFingerprinter.class);
        Fingerprint f = null;
        try {
            if(action != null){
                f = Jenkins.getInstance()._getFingerprint(action.GetHashString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    private HashMap<AbstractProject, HashSet<ChangeLogSet>> GetWorkflowProjectsChanges(HashSet<Fingerprint> sinceLastSuccess) {
        HashMap<AbstractProject, HashSet<ChangeLogSet>> result = new HashMap<AbstractProject, HashSet<ChangeLogSet>>();
        HashMap<AbstractProject, Fingerprint.RangeSet> upstreamBuildsSinceLastSuccess = new HashMap<AbstractProject, Fingerprint.RangeSet>();
        for(Fingerprint f : sinceLastSuccess){
            Hashtable<String,Fingerprint.RangeSet> usages = f.getUsages();

            for(Map.Entry<String,Fingerprint.RangeSet> project : usages.entrySet()){
                AbstractProject p = Hudson.getInstance().getItemByFullName(project.getKey(), AbstractProject.class);
                if(p != null && !upstreamBuildsSinceLastSuccess.containsKey(p)){
                    upstreamBuildsSinceLastSuccess.put(p, project.getValue());
                } else {
                    upstreamBuildsSinceLastSuccess.get(p).add(project.getValue());
                }
            }
        }

        for(Map.Entry<AbstractProject, Fingerprint.RangeSet> entry : upstreamBuildsSinceLastSuccess.entrySet()){
            AbstractProject proj = entry.getKey();
            if(IsScmConfiguredForProject(proj)){
                List<AbstractBuild> builds = proj.getBuilds(entry.getValue());
                for (AbstractBuild upBuild : builds){
                    if(upBuild == null){
                        continue;
                    }
                    ChangeLogSet changeLogSet = upBuild.getChangeSet();
                    if(!changeLogSet.isEmptySet()){
                        if(!result.containsKey(proj)){
                            result.put(proj, new HashSet<ChangeLogSet>());
                        }
                        result.get(proj).add(changeLogSet);
                    }
                }
            }
        }
        return result;
    }

    private boolean IsScmConfiguredForProject(AbstractProject proj) {
        return !(proj.getScm() instanceof NullSCM);
    }

    public boolean isMercurialPluginAvailable() {
        boolean result = false;
        Plugin mercurial = Jenkins.getInstance().getPlugin("mercurial");
        if(mercurial != null) {
            return true;
        }
        return result;
    }
}

