package edu.ch.fr.emf.fri;

public class RepoToExportAndZip implements Comparable<RepoToExportAndZip> {

    private final String repoName;
    private final String repoUrl;

    public RepoToExportAndZip(String repoName, String repoUrl) {
        this.repoName = repoName;
        this.repoUrl = repoUrl;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    @Override
    public int compareTo(RepoToExportAndZip o) {
        return repoName.compareTo(o.repoName);
    }

}
