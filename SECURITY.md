# Security Policy

## Supported Versions

We provide security updates for the following versions of Ollama Android Client:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of Ollama Android Client seriously. If you believe you have found a security vulnerability, please report it to us as described below.

### Please do NOT:

- Open a public GitHub issue
- Discuss the vulnerability publicly
- Share the vulnerability with others until it has been resolved

### Please DO:

1. **Email or create a private security advisory** with details of the vulnerability
2. **Include the following information**:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if you have one)
   - Your contact information

3. **Wait for our response**:
   - We will acknowledge receipt within 48 hours
   - We will provide an initial assessment within 7 days
   - We will keep you informed of our progress

### What to Expect

- **Acknowledgment**: We will acknowledge receipt of your report within 48 hours
- **Initial Assessment**: We will provide an initial assessment within 7 days
- **Updates**: We will keep you informed of our progress toward resolving the issue
- **Resolution**: We will work to resolve the issue as quickly as possible
- **Credit**: With your permission, we will credit you in our security advisories

### Security Best Practices

When using Ollama Android Client:

1. **Use HTTPS**: Always use HTTPS when connecting to your Ollama server in production
2. **Keep Updated**: Update to the latest version of the app regularly
3. **Secure Your Server**: Ensure your Ollama server is properly secured
4. **Local Data**: Be aware that chat messages are stored locally on your device
5. **Network Security**: Use secure networks when connecting to remote servers

### Known Security Considerations

- **Cleartext Traffic**: The app allows cleartext HTTP traffic for local development. For production, use HTTPS.
- **Local Storage**: Chat messages are stored in a local Room database. Uninstalling the app will delete this data.
- **Firebase**: If using Firebase services, ensure your `google-services.json` is properly configured and not committed to version control.
- **Network Security Config**: Review `network_security_config.xml` for your security requirements.

### Security Features

- **Encrypted Storage**: Local data uses Android's built-in encryption
- **HTTPS Support**: Full support for secure HTTPS connections
- **Network Security Config**: Configurable network security policies
- **No Cloud Storage**: Chat data is not stored on external servers (unless you configure a remote Ollama server)

### Disclosure Policy

- We will disclose vulnerabilities after they have been fixed and patches are available
- We will credit security researchers who responsibly disclose vulnerabilities
- We will maintain a security advisory list for known issues

## Contact

For security-related issues, please contact:

- **Repository**: [http://10.0.0.129:3000/charles/ollama-android-client](http://10.0.0.129:3000/charles/ollama-android-client)
- **Security Issues**: Create a private security advisory or contact the maintainers directly

Thank you for helping keep Ollama Android Client and its users safe!

