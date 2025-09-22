package se.systementor.s3demoigen;

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
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

@SpringBootApplication
public class S3demoigenApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(S3demoigenApplication.class, args);
    }

    @Override
    public void run(String... args) {
        Dotenv dotenv = Dotenv.load();

        String accessKey = dotenv.get("ACCESS_KEY");
        String secretKey = dotenv.get("SECRET_KEY");
        String bucketName = dotenv.get("BUCKET_NAME");
        String bucketName2 = dotenv.get("BUCKET_NAME");

        if (bucketName == null || bucketName.isEmpty()) {
            System.err.println("BUCKET_NAME saknas i .env");
            return;
        }

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(new AwsCredentialsProvider() {
                    @Override
                    public AwsCredentials resolveCredentials() {
                        return AwsBasicCredentials.create(accessKey, secretKey);
                    }
                })
                .region(Region.EU_NORTH_1) // Stockholm
                .build();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- Meny ---");
            System.out.println("1. Lista alla filer i bucket");
            System.out.println("2. Ladda upp fil");
            System.out.println("3. Ladda ner fil");
            System.out.println("4. Avsluta");
            System.out.print("Välj alternativ: ");

            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1":
                        listFiles(s3Client, bucketName);
                        break;
                    case "2":
                        System.out.print("Ange lokal filväg: ");
                        String localPath = scanner.nextLine();
                        System.out.print("Ange filnamn i bucket: ");
                        String uploadKey = scanner.nextLine();
                        uploadFile(s3Client, bucketName, uploadKey, localPath);
                        break;
                    case "3":
                        System.out.print("Ange filnamn i bucket: ");
                        String downloadKey = scanner.nextLine();
                        System.out.print("Ange lokal filväg att spara till: ");
                        String savePath = scanner.nextLine();
                        downloadFile(s3Client, bucketName, downloadKey, savePath);
                        break;
                    case "4":
                        System.out.println("Avslutar...");
                        return;
                    default:
                        System.out.println("Ogiltigt val, försök igen.");
                }
            } catch (Exception e) {
                System.err.println("Fel: " + e.getMessage());
            }
        }
    }

    private void listFiles(S3Client s3Client, String bucketName) {
        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);

        if (listRes.contents().isEmpty()) {
            System.out.println("Inga filer i bucket.");
        } else {
            System.out.println("Filer i bucket:");
            listRes.contents().forEach(obj -> System.out.println(" - " + obj.key()));
        }
    }

    private void uploadFile(S3Client s3Client, String bucketName, String keyName, String localFilePath) {
        File file = new File(localFilePath);
        if (!file.exists()) {
            System.err.println("Filen finns inte lokalt.");
            return;
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromFile(file));
        System.out.println("Uppladdning klar: " + keyName);
    }

    private void downloadFile(S3Client s3Client, String bucketName, String keyName, String savePath) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
             InputStream in = s3Object) {
            java.nio.file.Files.copy(in, new File(savePath).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Nedladdning klar: " + savePath);
        } catch (IOException e) {
            System.err.println("Fel vid nedladdning: " + e.getMessage());
        }
    }
}
