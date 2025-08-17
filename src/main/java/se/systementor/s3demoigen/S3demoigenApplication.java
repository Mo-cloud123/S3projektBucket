package se.systementor.s3demoigen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import java.util.UUID;

@SpringBootApplication
public class S3demoigenApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(S3demoigenApplication.class, args);
    }


    private void runPolly() {

        System.out.println("Running Polly...");
        Dotenv dotenv = Dotenv.load();

        String accessKey = dotenv.get("ACCESS_KEY");
        String secretKey = dotenv.get("SECRET_KEY");
        String bucketName = dotenv.get("BUCKET_NAME");

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(new AwsCredentialsProvider() {
                    @Override
                    public AwsCredentials resolveCredentials() {
                        return AwsBasicCredentials.builder()
                                .accessKeyId(accessKey)
                                .secretAccessKey(secretKey).build();
                    }
                })
                .region(Region.EU_NORTH_1)
                .build();


        TranscribeClient transcribeClient = TranscribeClient.builder()
                .credentialsProvider(new AwsCredentialsProvider() {
                    @Override
                    public AwsCredentials resolveCredentials() {
                        return AwsBasicCredentials.builder()
                                .accessKeyId(accessKey)
                                .secretAccessKey(secretKey).build();
                    }
                })
                .region(Region.EU_NORTH_1)
                .build();


        Scanner scanner = new Scanner(System.in);

        // Implement Polly functionality here
        while(true){
            System.out.println("1. Start transcription");
            System.out.println("4. Exit");
            System.out.print("Choose an option: ");
            String localFilePath = "src/main/resources/RecordingInEnglish.m4a";
            String keyName = "RecordingInEnglish.m4a";
            int choice = Integer.parseInt(scanner.nextLine());
            switch(choice){
                case 1:
                    System.out.println("Ange filnamn för ljudfilen som ska transkriberas:");
                    // Skapa en unik jobbnamn för transkribering
                    String transcriptionJobName = "job-" + UUID.randomUUID().toString();
                    // 1. Ladda upp ljudfilen till S3
                    uploadFileToS3(s3Client, bucketName, keyName, localFilePath);
                    startTranscriptionJob(transcribeClient, transcriptionJobName, bucketName, keyName);
                    waitForTranscriptionToComplete(transcribeClient, transcriptionJobName);
                    String urlForResult = getTranscriptResultUri(transcribeClient, transcriptionJobName);
                    String transcriptText = getTranscriptTextFromUrl(urlForResult);
                    System.out.println("Transkriberad text: " + transcriptText);
                    break;
                case 4:
                    return;
            }
        }
    }

    private static String getTranscriptTextFromUrl(String transcriptUri) {
        String transcriptText = null;
        try {
            URL url = new URL(transcriptUri);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP-felkod: " + responseCode);
            }

            try (InputStream inputStream = conn.getInputStream()) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(inputStream);

                // Navigera i JSON-strukturen för att hitta transkriptionstexten
                 transcriptText = rootNode.path("results").path("transcripts").get(0).path("transcript").asText();

                System.out.println("\n--- Transkriberad text ---");
                System.out.println(transcriptText);
                System.out.println("--------------------------");
            }

        } catch (IOException e) {
            System.err.println("Fel vid nedladdning eller parsning av transkriptionsfil: " + e.getMessage());
        }
        return transcriptText;
    }
    private String getTranscriptResultUri(TranscribeClient transcribeClient, String transcriptionJobName) {

        try {
            GetTranscriptionJobRequest getJobRequest = GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(transcriptionJobName)
                    .build();

            GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(getJobRequest);
            String transcriptUri = response.transcriptionJob().transcript().transcriptFileUri();
            System.out.println("\nTranskriptionen finns på: " + transcriptUri);

            return transcriptUri;

            // Härifrån kan du ladda ner JSON-filen och bearbeta den för att få ut den rena texten
            // (kod för nedladdning och parsing av JSON är utelämnad för att hålla exemplet kort)

        } catch (TranscribeException e) {
            System.err.println("Fel vid hämtning av transkriptionens URI: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }


    private static void waitForTranscriptionToComplete(TranscribeClient transcribeClient, String transcriptionJobName) {
        GetTranscriptionJobRequest getJobRequest = GetTranscriptionJobRequest.builder()
                .transcriptionJobName(transcriptionJobName)
                .build();

        try {
            TranscriptionJobStatus jobStatus;
            do {
                GetTranscriptionJobResponse response = transcribeClient.getTranscriptionJob(getJobRequest);
                jobStatus = response.transcriptionJob().transcriptionJobStatus();
                System.out.print(".");
                Thread.sleep(5000); // Vänta 5 sekunder
            } while (jobStatus == TranscriptionJobStatus.IN_PROGRESS);

            if (jobStatus == TranscriptionJobStatus.FAILED) {
                System.err.println("\nTranskriberingsjobbet misslyckades.");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("\nFel under väntan på transkriberingsjobb: " + e.getMessage());
            System.exit(1);
        }


    }

    private static void startTranscriptionJob(TranscribeClient transcribeClient, String transcriptionJobName, String bucketName, String keyName) {

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(transcriptionJobName)
                .languageCode(LanguageCode.EN_US) // Ange språk
                .media(Media.builder()
                        .mediaFileUri("s3://" + bucketName + "/" + keyName)
                        .build())
                .build();

        try {
            transcribeClient.startTranscriptionJob(request);
        } catch (TranscribeException e) {
            System.err.println("Fel vid start av transkriberingsjobb: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Transkriberingsjobb startat: " + transcriptionJobName);

    }



    private static void uploadFileToS3(S3Client s3Client, String bucketName, String keyName, String localFilePath) {

        try {
            File audioFile = new File(localFilePath);
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(audioFile));
        } catch (Exception e) {
            System.err.println("Fel vid uppladdning till S3: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) throws Exception {

        runPolly();
        return;



//        Scanner scanner = new Scanner(System.in);
//        S3Client s3Client = S3Client.builder()
//                .credentialsProvider(new AwsCredentialsProvider() {
//                    @Override
//                    public AwsCredentials resolveCredentials() {
//                        return AwsBasicCredentials.builder()
//                                .accessKeyId(accessKey)
//                                .secretAccessKey(secretKey).build();
//                    }
//                })
//                .region(Region.EU_NORTH_1)
//                .build();
////        S3Client s3Client = new S3Client();
////        s3Client.Connect(accessKey, secretKey);
////        List<String> filnamnen =  s3Client.ListFiles(bucketName);
//
//
//        while(true){
//            System.out.println("1. Lista alla filer");
//            System.out.println("2. Ladda upp fil");
//            System.out.println("3. Ladda ner fil");
//            System.out.println("4. Avsluta");
//            System.out.print("Choose an option: ");
//            int choice = Integer.parseInt(scanner.nextLine());
//
//            switch(choice){
//                case 1:
//                    System.out.println("Nu listas alla filer");
//                    ListObjectsV2Request listReq = ListObjectsV2Request.builder()
//                            .bucket(bucketName)
//                            .build();
//
//     //               List<String> filer = new S3Service().ListAll();
//
//
//                    ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);
//                    List<String> filNamnen = listRes.contents().stream()
//                            .map(S3Object::key)
//                            .collect(Collectors.toList());
//
//                    filNamnen.forEach(System.out::println);
//
//                    // lista alla filer i bucketen som heter "teststefan0813"
//                    break;
//                case 2:
//                    System.out.println("Vilken fil vill du ladda upp");
//                    break;
//                case 3:
//                    System.out.println("Vilken fil vill du ladda ner fil");
//                    break;
//                case 4:
//                    return;
//            }

//        }
    }
}
