package edplatform.edplat.utils;

import java.io.*;
import java.nio.file.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class FileUploadUtil {

    public static void saveFile(String uploadDir, String fileName,
                                MultipartFile multipartFile) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        try (InputStream inputStream = multipartFile.getInputStream()) {
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ioe) {
            throw new IOException("Could not save image file: " + fileName, ioe);
        }
    }

    public static void deleteFile(String uploadDir, String fileName) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            log.error("Trying to delete submission file from nonexistent directory");
            return;
        }

        Path filePath = uploadPath.resolve(fileName);
        try {
            Files.delete(filePath);
        } catch (IOException ioe) {
            throw new IOException("Could not delete image file: " + fileName, ioe);
        }
    }
}
