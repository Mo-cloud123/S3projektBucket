package se.systementor.s3demoigen;

import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.stream.Collectors;

public class S3Service {
    public List<String> ListAll() {
        ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);
        List<String> filNamnen = listRes.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());

        return  filNamnen;
    }

}
