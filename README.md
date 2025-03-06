# The Password Vault

A secure password manager with encryption and key file authentication. Built with Java and JavaFX.

## Features

- Secure password storage with encryption
- Two-factor authentication using key files
- Category organization for passwords
- Backup and restore functionality
- Modern, user-friendly interface
- Secure password deletion
- Account lockout protection

## Download and Installation

### Prerequisites

- Java 17 or higher installed on your system
- Download the latest release from the [Releases](../../releases) page

### Installation Steps

1. Download the latest release ZIP file
2. Extract the ZIP file to your desired location
3. Run the application:
   - **Windows**: Double-click `run.bat` or run `java -jar password-vault-1.0.0.jar`
   - **Linux/Mac**: Open terminal, navigate to the folder and run:
     ```bash
     chmod +x run.sh
     ./run.sh
     ```

## First Time Setup

1. Launch the application
2. Click "Sign Up" to create a new account
3. Choose a strong master password
4. Select a location to save your key file
5. Keep your key file safe - you'll need it to log in!

## Security Features

- Master password hashing with salt rotation
- Two-factor authentication with key files
- Envelope encryption for stored passwords
- Secure memory wiping
- Account lockout after failed attempts
- Encrypted backup files

## Usage Tips

1. **Master Password**: Choose a strong master password that you haven't used elsewhere
2. **Key File**: Store your key file on a separate device (like a USB drive)
3. **Backups**: Regularly create encrypted backups of your password database
4. **Categories**: Use categories to organize your passwords effectively

## Building from Source

If you want to build the application from source:

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/password-vault.git
   cd password-vault
   ```

2. Build with Maven:
   ```bash
   mvn clean package
   ```

3. Find the executable JAR in `target/password-vault-1.0.0.jar`

## Troubleshooting

1. **Can't Log In**: 
   - Verify your master password
   - Ensure you're using the correct key file
   - Wait if your account is temporarily locked

2. **Database Reset**:
   - Close the application
   - Delete the `passwords.db` file
   - Restart the application

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Security Notice

While this password manager implements strong security measures, no system is completely immune to all threats. Always:
- Keep your master password secure
- Store your key file separately from your password database
- Create regular backups
- Update the application when new versions are released