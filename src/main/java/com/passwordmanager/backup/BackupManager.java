package com.passwordmanager.backup;

import com.passwordmanager.security.Encryptor;
import com.passwordmanager.security.SecureWiper;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.sql.Connection;
import java.sql.SQLException;

public class BackupManager {
    private static final int CURRENT_BACKUP_VERSION = 1;
    private static final String BACKUP_EXTENSION = ".pmbackup";
    private static final String METADATA_FILE = "backup_metadata.json";
    private static final int BUFFER_SIZE = 8192;

    private final String dbPath;
    private final SecretKey masterKey;

    public BackupManager(String dbPath, SecretKey masterKey) {
        this.dbPath = dbPath;
        this.masterKey = masterKey;
    }

    /**
     * Creates an encrypted backup of the database
     * @param outputPath The path where the backup should be saved
     * @throws Exception if backup creation fails
     */
    public void createBackup(String outputPath) throws Exception {
        // Create a temporary directory for backup preparation
        Path tempDir = Files.createTempDirectory("db_backup_");
        try {
            // Copy the database file to temp directory
            Path dbCopy = tempDir.resolve("database.db");
            Files.copy(Paths.get(dbPath), dbCopy);

            // Create metadata file
            String metadata = createBackupMetadata();
            Path metadataPath = tempDir.resolve(METADATA_FILE);
            Files.writeString(metadataPath, metadata);

            // Create ZIP file containing database and metadata
            ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
            zipDirectory(tempDir.toFile(), zipBuffer);
            byte[] zipData = zipBuffer.toByteArray();

            // Calculate checksum of the unencrypted ZIP data
            String checksum = calculateChecksum(zipData);

            // Encrypt the ZIP data
            Encryptor encryptor = new Encryptor(masterKey);
            byte[] encryptedData = encryptor.encryptBytes(zipData);

            // Create final backup file
            try (DataOutputStream out = new DataOutputStream(
                    new FileOutputStream(outputPath + BACKUP_EXTENSION))) {
                // Write backup version
                out.writeInt(CURRENT_BACKUP_VERSION);
                // Write checksum
                out.writeUTF(checksum);
                // Write encrypted data length
                out.writeInt(encryptedData.length);
                // Write encrypted data
                out.write(encryptedData);
            }

        } finally {
            // Secure cleanup
            SecureWiper.secureTempCleanup(tempDir.toFile());
        }
    }

    /**
     * Restores the database from an encrypted backup
     * @param backupPath The path to the backup file
     * @param targetPath Where to restore the database
     * @throws Exception if restoration fails
     */
    public void restoreBackup(String backupPath, String targetPath) throws Exception {
        // Create temporary directory for restoration
        Path tempDir = Files.createTempDirectory("db_restore_");
        try {
            // Read and decrypt backup file
            byte[] decryptedZipData;
            String storedChecksum;
            
            try (DataInputStream in = new DataInputStream(
                    new FileInputStream(backupPath))) {
                
                // Read and verify backup version
                int version = in.readInt();
                if (version > CURRENT_BACKUP_VERSION) {
                    throw new Exception("Backup version " + version + 
                        " is newer than supported version " + CURRENT_BACKUP_VERSION);
                }

                // Read checksum
                storedChecksum = in.readUTF();
                
                // Try to read in new format first
                try {
                    // Read encrypted data length
                    int encryptedLength = in.readInt();
                    byte[] encryptedData = new byte[encryptedLength];
                    
                    // Read encrypted data
                    in.readFully(encryptedData);

                    // Decrypt the backup
                    Encryptor encryptor = new Encryptor(masterKey);
                    decryptedZipData = encryptor.decryptBytes(encryptedData);
                } catch (EOFException e) {
                    // If EOF encountered, try old format
                    in.close();
                    try (DataInputStream oldIn = new DataInputStream(
                            new FileInputStream(backupPath))) {
                        // Skip version and checksum we already read
                        oldIn.readInt();
                        oldIn.readUTF();
                        
                        // Read encrypted data as string (old format)
                        String encryptedString = oldIn.readUTF();
                        
                        // Decrypt using old string-based method
                        Encryptor encryptor = new Encryptor(masterKey);
                        String decryptedString = encryptor.decrypt(encryptedString);
                        decryptedZipData = decryptedString.getBytes();
                    }
                }
            }

            // Verify checksum
            String calculatedChecksum = calculateChecksum(decryptedZipData);
            if (!storedChecksum.equals(calculatedChecksum)) {
                throw new Exception("Backup file is corrupted or has been tampered with");
            }

            // Write decrypted ZIP to temp file for extraction
            Path tempZip = tempDir.resolve("temp.zip");
            Files.write(tempZip, decryptedZipData);

            // Unzip the backup
            unzipFile(tempZip.toFile(), tempDir.toFile());

            // Verify metadata
            verifyBackupMetadata(tempDir.resolve(METADATA_FILE));

            // Copy restored database to target location
            Files.copy(tempDir.resolve("database.db"), 
                     Paths.get(targetPath), 
                     StandardCopyOption.REPLACE_EXISTING);

        } finally {
            // Secure cleanup
            SecureWiper.secureTempCleanup(tempDir.toFile());
        }
    }

    private String createBackupMetadata() {
        // Create JSON with backup metadata
        return String.format("""
            {
                "version": %d,
                "timestamp": "%s",
                "databaseVersion": 1,
                "description": "Password Manager Database Backup"
            }""",
            CURRENT_BACKUP_VERSION,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private void verifyBackupMetadata(Path metadataPath) throws Exception {
        String metadata = Files.readString(metadataPath);
        // Add validation logic here
        if (!metadata.contains("\"version\": " + CURRENT_BACKUP_VERSION)) {
            throw new Exception("Invalid backup metadata version");
        }
    }

    private void zipDirectory(File dir, OutputStream out) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (File file : dir.listFiles()) {
                if (!file.isDirectory()) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    Files.copy(file.toPath(), zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private void unzipFile(File zipFile, File targetDir) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(targetDir, entry.getName());
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private String calculateChecksum(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
} 