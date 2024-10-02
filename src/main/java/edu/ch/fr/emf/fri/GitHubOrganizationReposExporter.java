package edu.ch.fr.emf.fri;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import net.lingala.zip4j.ZipFile;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import org.eclipse.jgit.util.FileUtils;

/**
 * Clone tous les repos d'une organisation GitHub au format ZIP.<br>
 * <br>
 * Ce qui revient au même que, manuellement et pour chaque repo, le cloner
 * à l'aide de par exemple VSC, puis de zipper le dossier cloné.
 * 
 * @author Paul Friedli <paul.friedli@edufr.ch>
 * @since 26.09.2024
 * @version 0.1
 */
public class GitHubOrganizationReposExporter {

    // Organisation GitHub dont on veut pomper les repos localement
    private final static String REMOTE_ORG_NAME = "emf-info-d400";

    // Ton token GitHub perso (voir README.MD comment faire pour en obtenir un)
    private final static String GITHUB_TOKEN = "ghp_xyzxyzxyzxyzxyzxyzxzxz";

    // Dossier où seront déposés les repos zippés (attention à conserver le / final
    // !)
    private final static String LOCAL_DESTINATION_FOLDER = "./downloaded_repos/";

    public static void main(String[] args) throws IOException, GitAPIException {
        DecimalFormat df3 = new DecimalFormat("000");
        OkHttpClient client = new OkHttpClient();

        System.out.println();
        System.out.println("=====================================================================================");
        System.out.println("GitHubOrganizationReposExporter v1.0 ");
        System.out.println("-------------------------------------------------------------------------------------");

        System.out.println("Établissement de la liste des repos de [" + REMOTE_ORG_NAME + "]...");

        Request request = new Request.Builder()
                .url("https://api.github.com" + "/orgs/" + REMOTE_ORG_NAME
                        + "/repos?per_page=1024&page=1&type=all")
                .header("Authorization", "token " + GITHUB_TOKEN)
                .build();

        // Exécution de la requête
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erreur dans la requête : " + response);
            }

            // Récupération des données JSON
            String jsonData = response.body().string();
            JSONArray repos = new JSONArray(jsonData);
            int nbreTotalRepos = repos.length();

            System.out.println("Clonage et ZIP des " + nbreTotalRepos + " repos de [" + REMOTE_ORG_NAME + "]...");

            // Parcours des repositories et clonage
            for (int i = 0; i < nbreTotalRepos; i++) {
                JSONObject repo = repos.getJSONObject(i);
                String repoName = repo.getString("name");
                String repoUrl = repo.getString("clone_url");
                String strAvance = df3.format(i + 1) + "/" + df3.format(nbreTotalRepos);

                System.out.print("  - " + strAvance + " [" + repoName + "] : ");

                // Clonage du repository via JGit
                System.out.print("clonage...");
                Git.cloneRepository()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GITHUB_TOKEN, ""))
                        .setURI(repoUrl)
                        // .setURI(repoUrlWithToken)
                        .setDirectory(new File(LOCAL_DESTINATION_FOLDER + repoName)) // Répertoire local du repo cloné
                        .call()
                        .close();

                // Zippage du repository cloné local
                ZipFolder(LOCAL_DESTINATION_FOLDER + repoName);

            }

            System.out.println("Clonage et ZIP des repos de [" + REMOTE_ORG_NAME + "] terminé avec succès !");
            System.out.println("=====================================================================================");
            System.out.println();
        }
    }

    @SuppressWarnings("resource")
    private static void ZipFolder(String pathToRepoFolder) throws IOException {
        System.out.print(" zippage...");
        new ZipFile(pathToRepoFolder + ".zip").addFolder(new File(pathToRepoFolder));
        System.out.println(" suppression du clone...");
        FileUtils.delete(new File(pathToRepoFolder), FileUtils.RECURSIVE + FileUtils.RETRY);
    }

}
