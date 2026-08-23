package com.filecabinet.document.service;

import com.filecabinet.shared.exception.ServiceExceptions.InvalidStateException;
import com.filecabinet.shared.exception.ServiceExceptions.NotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${filecabinet.upload-dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + uploadRoot, e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidStateException("Uploaded file is empty.");
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "_" + original;
        Path target = uploadRoot.resolve(storedName).normalize();
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store file " + original, e);
        }
        log.info("Stored uploaded file {}", storedName);
        return target.toString();
    }

    public Resource loadAsResource(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NotFoundException("File not found: " + filePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new NotFoundException("File not found: " + filePath);
        }
    }
}
