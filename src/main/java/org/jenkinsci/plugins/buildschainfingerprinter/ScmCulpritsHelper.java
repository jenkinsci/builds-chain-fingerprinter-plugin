package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: psamoshkin
 * Date: 10/22/12
 * Time: 4:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class ScmCulpritsHelper {
    public Map<User, Set<AbstractBuild<?,?>>> GetUpstreamBuildCulprits(AbstractBuild<?, ?> build) {
        HashMap<User, Set<AbstractBuild<?,?>>> result = new HashMap<User, Set<AbstractBuild<?,?>>>();
        HashMap<AbstractProject, HashSet<ChangeLogSet>> upstreamChanges = GetUpstreamScmChangesSinceLastSuccess(build);
        for(Map.Entry<AbstractProject, HashSet<ChangeLogSet>> upstreamProjectChanges : upstreamChanges.entrySet()){
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

    public Set<User> GetUpstreamCulprits(AbstractBuild<?, ?> build){
        Set<User> users = GetUpstreamBuildCulprits(build).keySet();
        return users;
    }

    public String UpstreamChangesToString(HashMap<AbstractProject, HashSet<ChangeLogSet>> changes){
        StringBuilder res = new StringBuilder();
        for(Map.Entry<AbstractProject, HashSet<ChangeLogSet>> entry : changes.entrySet()){
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
        return res.toString();
    }

    public HashMap<AbstractProject, HashSet<ChangeLogSet>> GetUpstreamScmChangesSinceLastSuccess(AbstractBuild<?, ?> build) {
        HashSet<Fingerprint> sinceLastSuccess = GetSinceLastSuccesBuildsCausedByUpstreamJobs(build);
        return GetWorkflowProjectsChanges(sinceLastSuccess, build.getProject());
    }

    public HashSet<Fingerprint> GetSinceLastSuccesBuildsCausedByUpstreamJobs(AbstractBuild<?, ?> build) {
        HashSet<Fingerprint> result = new HashSet<Fingerprint>();
        Fingerprint f = GetBuidsDependencyFingerprint(build);

        if(f!=null){
            Fingerprint.BuildPtr rootBuild = f.getOriginal();
            AbstractProject rootJob = (AbstractProject)Jenkins.getInstance().getItemByFullName(rootBuild.getName());
            if(rootJob != null){
                for(Object b : rootJob.getBuilds()){
                    if(((AbstractBuild) b).getNumber() > rootBuild.getNumber()){
                        continue;
                    }
                    Fingerprint e = GetBuidsDependencyFingerprint((AbstractBuild) b);
                    int i = IsFingerprintInRangeSinceLastSuccess(e, build);
                    if(i == 0){
                        result.add(e);
                    } else if (i < 0){
                        break;
                    }
                }
            }
        }
        return result;
    }

    private int IsFingerprintInRangeSinceLastSuccess(Fingerprint fingerprint, AbstractBuild build){
        Fingerprint.RangeSet buildsRelatedToUpstream = fingerprint.getRangeSet(build.getProject());
        int result = 0;
        for(Integer buildId : buildsRelatedToUpstream.listNumbersReverse()){
            if(buildId > build.getNumber()){//for elder builds than requested
                result = 1;
            }
            Run buildByNumber = build.getProject().getBuildByNumber(buildId);
            if(buildByNumber == null){//for deleted builds
                result = 0;
            }
            Result buildResult = buildByNumber.getResult();
            if(buildResult == null){//for uncompleted builds
                result = 0;
            } else if (buildResult.isBetterOrEqualTo(Result.SUCCESS)){
                if(build.getNumber() == buildId){
                    result = 0;
                    break;
                }else if (buildId < build.getNumber()){
                    result = -1;
                }
            } else{
                result = 0;
                break;
            }
        }
        return result;
    }

    private Fingerprint GetBuidsDependencyFingerprint(AbstractBuild<?, ?> build) {
        AutomaticFingerprintAction action = build.getAction(BuildsDependencyFingerprinter.class);
        Fingerprint f = null;
        try {
            f = Jenkins.getInstance()._getFingerprint(action.GetHashString());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    private HashMap<AbstractProject, HashSet<ChangeLogSet>> GetWorkflowProjectsChanges(HashSet<Fingerprint> sinceLastSuccess, AbstractProject that) {
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

        HashSet<AbstractProject> scmConfiguredProjects = getUpstreamProjectsWithScmConfigured(that);
        if(IsScmConfiguredForProject(that)){
            scmConfiguredProjects.add(that);
        }


        for(Map.Entry<AbstractProject, Fingerprint.RangeSet> entry : upstreamBuildsSinceLastSuccess.entrySet()){
            AbstractProject proj = entry.getKey();
            if(scmConfiguredProjects.contains(proj)){
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

    private HashSet<AbstractProject> getUpstreamProjectsWithScmConfigured(AbstractProject that) {
        HashSet<AbstractProject> scmConfiguredProjects = new HashSet<AbstractProject>();
        Set<AbstractProject> projects = Jenkins.getInstance().getDependencyGraph().getTransitiveUpstream(that);
        for(AbstractProject p : projects){
            if(IsScmConfiguredForProject(p)){
                scmConfiguredProjects.add(p);
            }
        }
        return scmConfiguredProjects;
    }

    private boolean isUpstreamOrRequested(AbstractProject proj, AbstractProject dest) {
        DependencyGraph graph = Jenkins.getInstance().getDependencyGraph();
        return graph.hasIndirectDependencies(proj, dest);
    }

    private boolean IsScmConfiguredForProject(AbstractProject proj) {
        return !(proj.getScm() instanceof NullSCM);
    }
}

