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
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private final static String REMOTE_ORG_NAME = "xxxxxxxxxxxxx";

    // Ton token GitHub perso (voir README.MD comment faire pour en obtenir un)
    private final static String GITHUB_TOKEN = "yyyyyyyyyyyyyyyyyyyyy";

    // Dossier où seront déposés les repos zippés (attention à conserver le / final
    // !)
    private final static String LOCAL_DESTINATION_FOLDER = "./downloaded_repos/";

    public static void main(String[] args) throws IOException, GitAPIException {

        System.out.println();
        System.out.println("=====================================================================================");
        System.out.println("GitHubOrganizationReposExporter v1.0 ");
        System.out.println("-------------------------------------------------------------------------------------");

        System.out.println("Établissement de la liste des repos de [" + REMOTE_ORG_NAME + "]...");

        TreeSet<RepoToExportAndZip> repositoriesToExportAndZip = new TreeSet<>();

        try {

            int currentPageWithinReposList = 0;
            int amountReposFoundInThatPage;
            do {
                // For the moment we failed to read them
                amountReposFoundInThatPage = 0;

                // Go to next page of repos
                currentPageWithinReposList++;

                // Request to get the list of the next 1..100 repos in that organization
                // (see https://docs.github.com/fr/rest/repos/repos?apiVersion=2022-11-28)
                String url = GITHUB_API_BASE_URL + "/orgs/" + REMOTE_ORG_NAME + "/repos?per_page="
                        + MAX_RETURNED_REPOS_PER_GITHUB_API_REQUEST + "&page=" + currentPageWithinReposList
                        + "&type=all";
                JSONArray repos = new JSONArray(doGetRequest(url, GITHUB_TOKEN));
                amountReposFoundInThatPage = repos.length();

                // Traitement de la collection reçue
                for (int i = 0; i < amountReposFoundInThatPage; i++) {
                    JSONObject repo = repos.getJSONObject(i);
                    String repoName = repo.getString("name");
                    String repoUrl = repo.getString("clone_url");

                    // Ajouter à la liste
                    repositoriesToExportAndZip.add(new RepoToExportAndZip(repoName, repoUrl));
                }
            } while (amountReposFoundInThatPage > 0);

            System.out.println("Un total de " + repositoriesToExportAndZip.size()
                    + " repos ont été trouvés pour l'organisation [" + REMOTE_ORG_NAME + "] !");
        } catch (Exception e) {
            System.out.println(
                    "Erreur lors de la récupération des dépôts de l'organisation " + REMOTE_ORG_NAME + " : " + e);
            e.printStackTrace();
        }

        System.out.println(
                "Clonage et ZIP des " + repositoriesToExportAndZip.size() + " repos de [" + REMOTE_ORG_NAME + "]...");

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_THREADS_EXPORTING_REPOS);
        for (RepoToExportAndZip repo : repositoriesToExportAndZip) {
            executor.submit(() -> {
                threadedRepositoryExportationAndCompression(repo);
            });
        }
        // Arrêter l'executor après la soumission de toutes les tâches
        executor.shutdown();
        try {
            // Attendre que toutes les tâches soient terminées avant de continuer
            if (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                System.out.println("Temps d'attente dépassé !");
            }
        } catch (InterruptedException e) {
            System.out.println("Erreur avec les tâches réalisées en parallèle avec le thread pool : " + e);
            e.printStackTrace();
        }

        System.out.println("Clonage et ZIP des repos de [" + REMOTE_ORG_NAME + "] terminé avec succès !");
        System.out.println("=====================================================================================");
        System.out.println();
    }

    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private final static int MAX_RETURNED_REPOS_PER_GITHUB_API_REQUEST = 64 + 32; // From 1 to 100
    private final static int MAX_CONCURRENT_THREADS_EXPORTING_REPOS = 8;

    private static void threadedRepositoryExportationAndCompression(RepoToExportAndZip repo) {

        // Clonage du repository via JGit
        try {
            Git.cloneRepository()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GITHUB_TOKEN, ""))
                    .setURI(repo.getRepoUrl())
                    // .setURI(repoUrlWithToken)
                    .setDirectory(new File(LOCAL_DESTINATION_FOLDER + repo.getRepoName())) // Répertoire local du repo
                                                                                           // cloné
                    .call()
                    .close();

            // Zippage du repository cloné local
            ZipFolder(LOCAL_DESTINATION_FOLDER + repo.getRepoName());

        } catch (GitAPIException e) {
            System.out.println("Erreur lors du clonage du repo [" + repo.getRepoName() + "] !");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Erreur lors de la compression repo [" + repo.getRepoName() + "] !");
            e.printStackTrace();
        }

        System.out.println("Exportation et compression du repo [" + repo.getRepoName() + "] terminée !");
    }

    private static String doGetRequest(String url, String GITHUB_TOKEN) throws IOException {

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }

    @SuppressWarnings("resource")
    private static void ZipFolder(String pathToRepoFolder) throws IOException {
        new ZipFile(pathToRepoFolder + ".zip").addFolder(new File(pathToRepoFolder));
        FileUtils.delete(new File(pathToRepoFolder), FileUtils.RECURSIVE + FileUtils.RETRY);
    }

}
