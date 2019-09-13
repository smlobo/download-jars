package oracle;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class DownloadJars {
    private static long bytesNeeded;
    private static long bytesDownloaded;
    private static int jarCount;
    private static LinkedList<String> suggestions = new LinkedList<>();

    public static void main(String[] args) {

        // Number of MB to download
        if (args.length < 1) {
            System.out.println("Usage: java -jar download-jars-1.0.jar " +
                    "<MB-to-download> [suggestion1 suggestion2 ...]");
            return;
        }
        bytesNeeded = 1024 * 1024 * Integer.parseInt(args[0]);
        System.out.println("Downloading " + bytesNeeded);

        // Read user search suggestions
        for (int i = 1; i < args.length; i++) {
            suggestions.add(args[i]);
        }

        while (bytesNeeded > bytesDownloaded) {

            // If there are suggestions - use those
            if (!suggestions.isEmpty()) {
                searchFor(suggestions.remove());
            }
            else {
                searchFor(randomString(3));
            }
        }
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        final String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < length; i++) {
            int randomIndex = (int) (Math.random() * lowerCase.length());
            sb.append(lowerCase.charAt(randomIndex));
        }
        return sb.toString();
    }

    private static void searchFor(String searchTerm) {

        CloseableHttpClient httpclient = HttpClients.createDefault();

        String url = "https://search.maven.org/solrsearch/select?q=" +
                searchTerm + "&rows=20&wt=json";
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpGet);
            //System.out.println(response.getStatusLine());
            HttpEntity entity = response.getEntity();

            // do something useful with the response body
            // and ensure it is fully consumed
            InputStream inputStream = entity.getContent();
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse
                    (new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            //System.out.println(jsonObject);

            // If found something
            JSONObject responseObject = (JSONObject) jsonObject.get("response");
            System.out.println("Search '" + searchTerm + "', found: " +
                    responseObject.get("numFound"));
            long count = (long) responseObject.get("numFound");
            if (count > 0) {
                parseResponseObject(responseObject);
            }

            // Check for suggestions
            JSONObject scObject = (JSONObject) jsonObject.get("spellcheck");
            //System.out.println(scObject);
            JSONArray suggArray = (JSONArray) scObject.get("suggestions");
            if (!suggArray.isEmpty()) {
                JSONObject suggObject = (JSONObject) suggArray.get(1);
                //System.out.println(suggObject);
                JSONArray sTermArray = (JSONArray) suggObject.get("suggestion");
                @SuppressWarnings("unchecked")
                Iterator<String> sTermIter = sTermArray.iterator();
                while (sTermIter.hasNext()) {
                    suggestions.add(sTermIter.next());
                }
            }

            EntityUtils.consume(entity);
        } catch (IOException e) {
            System.out.println("IOException for: " + url + " {" + e + "}");
        } catch (ParseException e) {
            System.out.println("ParseException for: " + url + " {" + e + "}");
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                System.out.println("Could not close HTTP response: " + e);
            }
        }
    }

    private static void parseResponseObject(JSONObject jO) {
        JSONArray responseArray = (JSONArray) jO.get("docs");

        @SuppressWarnings("unchecked")
        Iterator<JSONObject> responseIter = responseArray.iterator();
        while (responseIter.hasNext()) {
            JSONObject responseObj = responseIter.next();

            //System.out.println(responseObj);
            JSONArray textArray = (JSONArray) responseObj.get("text");
            boolean foundJar = false;
            @SuppressWarnings("unchecked")
            Iterator<String> textIter = textArray.iterator();
            while (textIter.hasNext()) {
                String text = textIter.next();
                if (text.equals(".jar")) {
                    foundJar = true;
                    break;
                }
            }

            if (!foundJar)
                continue;

            String filePath = ((String) responseObj.get("g")).replace('.', '/')
                    + "/" + responseObj.get("a") + "/" +
                    responseObj.get("latestVersion") + "/" +
                    responseObj.get("a") + "-" +
                    responseObj.get("latestVersion") + ".jar";
            //System.out.println(filePath);

            downloadFromMaven(filePath);

            // Download the first jar found
            break;
        }
    }

    private static void downloadFromMaven(String fileName) {
        // Create the local file
        File file = new File(fileName);

        String urlString = "https://search.maven.org/classic/remotecontent?filepath=" +
                fileName;
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            System.out.println("Bad URL: " + urlString);
            return;
        }
        try {
            FileUtils.copyURLToFile(url, file, 5*1000, 5*1000);
            bytesDownloaded += file.length();
            jarCount++;
            System.out.println("Downloaded: [" + jarCount + "] " + fileName +
                    "; " + file.length() + " {" + bytesDownloaded + "}");
        } catch (IOException e) {
            System.out.println("Failed to download: " + fileName);
        }
    }
}
