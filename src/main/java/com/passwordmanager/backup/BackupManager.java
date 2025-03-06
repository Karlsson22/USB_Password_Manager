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
        Path tempDir = Files.createTempDirectory("db_backup_");
        try {
            Path dbCopy = tempDir.resolve("database.db");
            Files.copy(Paths.get(dbPath), dbCopy);

            String metadata = createBackupMetadata();
            Path metadataPath = tempDir.resolve(METADATA_FILE);
            Files.writeString(metadataPath, metadata);

            ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
            zipDirectory(tempDir.toFile(), zipBuffer);
            byte[] zipData = zipBuffer.toByteArray();

            String checksum = calculateChecksum(zipData);

            Encryptor encryptor = new Encryptor(masterKey);
            byte[] encryptedData = encryptor.encryptBytes(zipData);

            try (DataOutputStream out = new DataOutputStream(
                    new FileOutputStream(outputPath + BACKUP_EXTENSION))) {
                out.writeInt(CURRENT_BACKUP_VERSION);
                out.writeUTF(checksum);
                out.writeInt(encryptedData.length);
                out.write(encryptedData);
            }

        } finally {
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
        Path tempDir = Files.createTempDirectory("db_restore_");
        try {
            byte[] decryptedZipData;
            String storedChecksum;
            
            try (DataInputStream in = new DataInputStream(
                    new FileInputStream(backupPath))) {
                
                int version = in.readInt();
                if (version > CURRENT_BACKUP_VERSION) {
                    throw new Exception("Backup version " + version + 
                        " is newer than supported version " + CURRENT_BACKUP_VERSION);
                }

                storedChecksum = in.readUTF();
                
                try {
                    int encryptedLength = in.readInt();
                    byte[] encryptedData = new byte[encryptedLength];
                    
                    in.readFully(encryptedData);

                    Encryptor encryptor = new Encryptor(masterKey);
                    decryptedZipData = encryptor.decryptBytes(encryptedData);
                } catch (EOFException e) {
                    in.close();
                    try (DataInputStream oldIn = new DataInputStream(
                            new FileInputStream(backupPath))) {
                        oldIn.readInt();
                        oldIn.readUTF();
                        
                        String encryptedString = oldIn.readUTF();
                        
                        Encryptor encryptor = new Encryptor(masterKey);
                        String decryptedString = encryptor.decrypt(encryptedString);
                        decryptedZipData = decryptedString.getBytes();
                    }
                }
            }

            String calculatedChecksum = calculateChecksum(decryptedZipData);
            if (!storedChecksum.equals(calculatedChecksum)) {
                throw new Exception("Backup file is corrupted or has been tampered with");
            }

            Path tempZip = tempDir.resolve("temp.zip");
            Files.write(tempZip, decryptedZipData);

            unzipFile(tempZip.toFile(), tempDir.toFile());

            verifyBackupMetadata(tempDir.resolve(METADATA_FILE));

            Files.copy(tempDir.resolve("database.db"), 
                     Paths.get(targetPath), 
                     StandardCopyOption.REPLACE_EXISTING);

        } finally {
            SecureWiper.secureTempCleanup(tempDir.toFile());
        }
    }

    private String createBackupMetadata() {
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