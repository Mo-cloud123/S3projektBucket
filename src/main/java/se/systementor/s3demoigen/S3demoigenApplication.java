package se.systementor.s3demoigen;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@SpringBootApplication
public class S3demoigenApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(S3demoigenApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Dotenv dotenv = Dotenv.load();

        String bucketName = dotenv.get("BUCKET_NAME");
        String accessKey = dotenv.get("ACCESS_KEY");
        String secretKey = dotenv.get("SECRET_KEY");

        System.out.println("Bucket Name: " + bucketName);
        System.out.println("Access Key: " + accessKey);
        System.out.println("Secret Key: " + secretKey);


        Scanner scanner = new Scanner(System.in);
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
//        S3Client s3Client = new S3Client();
//        s3Client.Connect(accessKey, secretKey);
//        List<String> filnamnen =  s3Client.ListFiles(bucketName);


        while(true){
            System.out.println("1. Lista alla filer");
            System.out.println("2. Ladda upp fil");
            System.out.println("3. Ladda ner fil");
            System.out.println("4. Avsluta");
            System.out.print("Choose an option: ");
            int choice = Integer.parseInt(scanner.nextLine());

            switch(choice){
                case 1:
                    System.out.println("Nu listas alla filer");
                    ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .build();

     //               List<String> filer = new S3Service().ListAll();


                    ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);
                    List<String> filNamnen = listRes.contents().stream()
                            .map(S3Object::key)
                            .collect(Collectors.toList());

                    filNamnen.forEach(System.out::println);

                    // lista alla filer i bucketen som heter "teststefan0813"
                    break;
                case 2:
                    System.out.println("Vilken fil vill du ladda upp");
                    break;
                case 3:
                    System.out.println("Vilken fil vill du ladda ner fil");
                    break;
                case 4:
                    return;
            }

        }
    }
}
