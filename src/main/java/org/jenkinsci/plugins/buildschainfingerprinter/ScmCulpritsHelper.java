package org.jenkinsci.plugins.buildschainfingerprinter;

import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;

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
        HashMap<AbstractProject, HashSet<ChangeLogSet>> upstreamChanges = GetUpstreamScmChangesSinceLastTransientSuccess(build);
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

    public HashMap<AbstractProject, HashSet<ChangeLogSet>> GetUpstreamScmChangesSinceLastTransientSuccess(AbstractBuild<?, ?> build) {
        Fingerprint.RangeSet buildsRelatedToUpstream = GetBuildsCausedByUpstreamJobs(build);
        Fingerprint.RangeSet sinceLastSuccess = GetSinceLastSuccessToExactBuildRange(build, buildsRelatedToUpstream);
        return GetTransitiveUpstreamProjectChanges(build.getProject(), sinceLastSuccess);
    }

    private HashMap<AbstractProject, HashSet<ChangeLogSet>> GetTransitiveUpstreamProjectChanges(AbstractProject<?, ?> project, Fingerprint.RangeSet sinceLastSuccess) {
        List<AbstractBuild> buildsSinceLastSuccess = (List<AbstractBuild>)project.getBuilds(sinceLastSuccess);
        HashMap<AbstractProject, HashSet<ChangeLogSet>> result = new HashMap<AbstractProject, HashSet<ChangeLogSet>>();
        for(AbstractBuild build : buildsSinceLastSuccess){
            Map<AbstractProject, Integer> transitiveUpstreamBuilds = build.getTransitiveUpstreamBuilds();
            for(Map.Entry<AbstractProject,Integer> projectBuild : transitiveUpstreamBuilds.entrySet()){
                if(IsScmConfiguredForProject(projectBuild.getKey())){
                    if(!result.containsKey(projectBuild.getKey())){
                        result.put(projectBuild.getKey(), new HashSet<ChangeLogSet>());
                    }
                    AbstractBuild upstreamBuild = (AbstractBuild)projectBuild.getKey().getBuildByNumber(projectBuild.getValue());
                    ChangeLogSet changeLogSet = upstreamBuild.getChangeSet();
                    if(!changeLogSet.isEmptySet()){
                        result.get(projectBuild.getKey()).add(changeLogSet);
                    }
                }
            }
        }
        return result;
    }

    private Fingerprint.RangeSet GetSinceLastSuccessToExactBuildRange(AbstractBuild<?, ?> build, Fingerprint.RangeSet buildsRelatedToUpstream) {
        Fingerprint.RangeSet result = new Fingerprint.RangeSet();
        for(Integer buildId : buildsRelatedToUpstream.listNumbersReverse()){
            if(buildId > build.getNumber()){
                continue;
            }
            Result buildResult = build.getParent().getBuildByNumber(buildId).getResult();
            if(buildResult == null || buildResult.isBetterOrEqualTo(Result.SUCCESS)){
                if(build.getNumber() == buildId){
                    result.add(buildId);
                }else{
                    break;
                }
            } else{
                result.add(buildId);
            }
        }
        return result;
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

    public Fingerprint.RangeSet GetBuildsCausedByUpstreamJobs(AbstractBuild<?, ?> build) {
        Fingerprint.RangeSet buildsRange = new Fingerprint.RangeSet();
        buildsRange.add(build.getNumber());
        AbstractProject project = build.getProject();
        Map<AbstractProject,Integer> transitiveUpstreamBuilds = build.getTransitiveUpstreamBuilds();
        for(Map.Entry<AbstractProject,Integer> upstreamBuild : transitiveUpstreamBuilds.entrySet()){
            if(IsScmConfiguredForProject(upstreamBuild.getKey()))
            {
                SortedMap<Integer, Fingerprint.RangeSet> buildsRelatedToUpstrteamProject = upstreamBuild.getKey().getRelationship(project);
                for (Map.Entry<Integer, Fingerprint.RangeSet> entry : buildsRelatedToUpstrteamProject.entrySet()){
                    buildsRange.add(entry.getValue());
                }
            }
        }
        return buildsRange;
    }

    private boolean IsScmConfiguredForProject(AbstractProject proj) {
        return !(proj.getScm() instanceof NullSCM);
    }
}

