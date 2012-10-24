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
            res.append("Project:" + entry.getKey().getName() + "\n");
            for(ChangeLogSet changeSet : entry.getValue()){
                for(Object entry1 : changeSet){
                    ChangeLogSet.Entry entry2 = (ChangeLogSet.Entry)entry1;
                    res.append("  changeset id: " + entry2.getCommitId() + "\n");
                    res.append("        author: " + entry2.getAuthor() + "\n");
                    res.append("       message: " + entry2.getMsg() + "\n");
                }
            }
        }
        return res.toString();
    }

    public HashMap<AbstractProject, HashSet<ChangeLogSet>> GetUpstreamScmChangesSinceLastSuccess(AbstractBuild<?, ?> build) {
        Fingerprint.RangeSet sinceLastSuccess = GetSinceLastSuccesBuildsCausedByUpstreamJobs(build);
        return GetTransitiveUpstreamProjectChanges(build, sinceLastSuccess);
    }

    public Fingerprint.RangeSet GetSinceLastSuccesBuildsCausedByUpstreamJobs(AbstractBuild<?, ?> build) {
        Fingerprint.RangeSet result = new Fingerprint.RangeSet();
        AutomaticFingerprintAction action = build.getAction(JobsDependencyFingerprinter.class);
        Fingerprint f = null;
        try {
            f = Jenkins.getInstance()._getFingerprint(action.GetHashString());

        } catch (IOException e) {
            e.printStackTrace();
        }

        if(f!=null){
            AbstractProject project = build.getProject();
            Fingerprint.RangeSet buildsRelatedToUpstream = f.getRangeSet(project);

            for(Integer buildId : buildsRelatedToUpstream.listNumbersReverse()){
                if(buildId > build.getNumber()){//for elder builds than requested
                    continue;
                }
                Run buildByNumber = project.getBuildByNumber(buildId);
                if(buildByNumber == null){//for deleted builds
                    continue;
                }
                Result buildResult = buildByNumber.getResult();
                if(buildResult == null){//for uncompleted builds
                    continue;
                }
                if(buildResult.isBetterOrEqualTo(Result.SUCCESS)){
                    if(build.getNumber() == buildId){
                        result.add(buildId);
                    }else{
                        break;
                    }
                } else{
                    result.add(buildId);
                }
            }
        }
        return result;
    }

    private HashMap<AbstractProject, HashSet<ChangeLogSet>> GetTransitiveUpstreamProjectChanges(AbstractBuild<?, ?> build, Fingerprint.RangeSet sinceLastSuccess) {

        List<AbstractBuild> buildsSinceLastSuccess = (List<AbstractBuild>)build.getProject().getBuilds(sinceLastSuccess);

        HashMap<AbstractProject, HashSet<ChangeLogSet>> result = new HashMap<AbstractProject, HashSet<ChangeLogSet>>();
        HashMap<String, Fingerprint.RangeSet> upstreamBuildsSinceLastSuccess = new HashMap<String, Fingerprint.RangeSet>();
        for(AbstractBuild previousBuild : buildsSinceLastSuccess){
            AutomaticFingerprintAction action = previousBuild.getAction(BuildsDependencyFingerprinter.class);
            Fingerprint f = null;
            try {
                f = Jenkins.getInstance()._getFingerprint(action.GetHashString());

            } catch (IOException e) {
                e.printStackTrace();
            }

            if(f != null){
                Hashtable<String,Fingerprint.RangeSet> usages = f.getUsages();

                for(Map.Entry<String,Fingerprint.RangeSet> project : usages.entrySet()){
                    if(!upstreamBuildsSinceLastSuccess.containsKey(project.getKey())){
                        upstreamBuildsSinceLastSuccess.put(project.getKey(), project.getValue());
                    } else {
                        upstreamBuildsSinceLastSuccess.get(project.getKey()).add(project.getValue());
                    }
                }
            }
        }
        for(Map.Entry<String, Fingerprint.RangeSet> entry : upstreamBuildsSinceLastSuccess.entrySet()){
            AbstractProject upProject = Hudson.getInstance().getItemByFullName(entry.getKey(), AbstractProject.class);
            if(IsScmConfiguredForProject(upProject)){
                List<AbstractBuild> builds = upProject.getBuilds(entry.getValue());
                for (AbstractBuild upBuild : builds){
                    ChangeLogSet changeLogSet = upBuild.getChangeSet();
                    if(!changeLogSet.isEmptySet()){
                        if(!result.containsKey(upProject)){
                            result.put(upProject, new HashSet<ChangeLogSet>());
                        }
                        result.get(upProject).add(changeLogSet);
                    }
                }
            }
        }
        return result;
    }

    private boolean IsScmConfiguredForProject(AbstractProject proj) {
        return !(proj.getScm() instanceof NullSCM);
    }
}

